package org.monstercubedraft.crac;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

/**
 * Owns MainDraftHandler's SsmClient and the ApiGatewayManagementApiClient used to push messages
 * to WebSocket clients, resolving the latter's endpoint from SSM Parameter Store (published by
 * MonsterDraftPublicAppApiStack).
 *
 * <p>{@link #beforeCheckpoint} only makes throwaway calls whose results are discarded, to warm up
 * request marshalling / credential resolution / JIT &amp; classloading for both SDK code paths
 * before the snapshot is taken — exactly like {@link DynamoDbClientResource}'s throwaway {@code
 * describeTable} call. It deliberately does not cache anything into {@link #managementClient}:
 * checkpointing happens once, at version-publish time, and its result is frozen into a snapshot
 * that many future execution environments restore from unchanged — including ones spun up long
 * after a later redeploy of the API stack changes the real endpoint (a new snapshot is only taken
 * when this function is itself republished, not when the API stack changes).
 *
 * <p>{@link #afterRestore} does the real work — fetch the callback URL from SSM and build the real
 * client from it — and is safe to do eagerly rather than lazily: it runs independently once per
 * restored execution environment (never shared across environments the way a snapshot is), and by
 * the time any real invocation can reach this function, the API stack — which owns both the SQS
 * integration this function is triggered by and the SSM parameter this class reads — must already
 * be deployed. Its fetch is still best-effort (failures are logged, not thrown): {@link
 * #managementClient()} is the load-bearing fallback, lazily resolving and caching on first real
 * use, and throws on failure so a caller processing an SQS message can let it fail and retry
 * rather than silently proceeding without a client.
 */
public class ApiClientResource implements Resource {

  private static final URI WARMUP_ENDPOINT =
      URI.create("https://apigatewaymanagementapi-warmup.invalid");
  private static final Duration WARMUP_TIMEOUT = Duration.ofSeconds(2);

  private final SsmClient ssmClient;
  private final ApiGatewayManagementApiClient warmupManagementClient;
  private final String parameterName;
  private volatile ApiGatewayManagementApiClient managementClient;

  public ApiClientResource(String parameterName) {
    this(
        SsmClient.builder().httpClient(UrlConnectionHttpClient.create()).build(),
        buildWarmupManagementClient(),
        parameterName);
  }

  public ApiClientResource(
      SsmClient ssmClient,
      ApiGatewayManagementApiClient warmupManagementClient,
      String parameterName) {
    this.ssmClient = Objects.requireNonNull(ssmClient);
    this.warmupManagementClient = Objects.requireNonNull(warmupManagementClient);
    this.parameterName = Objects.requireNonNull(parameterName);
    Core.getGlobalContext().register(this);
  }

  // Points at a hostname reserved by RFC 2606 to never resolve, so building/using this client
  // can't accidentally reach a real service. Short timeouts are checkpoint-latency insurance on
  // top of that, not a substitute for it (DNS resolution isn't reliably bounded by these
  // timeouts on every JVM/OS combination) — the class it's used from only calls it inside a
  // try/catch, and it's never used for anything but this warm-up.
  private static ApiGatewayManagementApiClient buildWarmupManagementClient() {
    return ApiGatewayManagementApiClient.builder()
        .endpointOverride(WARMUP_ENDPOINT)
        .httpClient(
            UrlConnectionHttpClient.builder()
                .connectionTimeout(WARMUP_TIMEOUT)
                .socketTimeout(WARMUP_TIMEOUT)
                .build())
        .overrideConfiguration(
            ClientOverrideConfiguration.builder()
                .apiCallTimeout(WARMUP_TIMEOUT)
                .apiCallAttemptTimeout(WARMUP_TIMEOUT)
                .build())
        .build();
  }

  @Override
  public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
    warmUpSsmClient();
    warmUpManagementClient();
  }

  @Override
  public void afterRestore(Context<? extends Resource> context) throws Exception {
    try {
      managementClient = buildManagementClient();
    } catch (Exception e) {
      // Best-effort; see class javadoc. managementClient() retries on first real use if this
      // didn't land.
      System.out.println(
          "Could not eagerly resolve WebSocket callback endpoint during restore: " + e);
    }
  }

  /**
   * Returns the cached ApiGatewayManagementApiClient, blocking to resolve and build it on first
   * call if {@link #afterRestore} didn't already populate it. Throws if SSM is unreachable or the
   * parameter is missing.
   */
  public ApiGatewayManagementApiClient managementClient() {
    ApiGatewayManagementApiClient client = managementClient;
    if (client != null) {
      return client;
    }
    synchronized (this) {
      if (managementClient == null) {
        managementClient = buildManagementClient();
      }
      return managementClient;
    }
  }

  private ApiGatewayManagementApiClient buildManagementClient() {
    String endpoint =
        ssmClient
            .getParameter(GetParameterRequest.builder().name(parameterName).build())
            .parameter()
            .value();
    return ApiGatewayManagementApiClient.builder()
        .endpointOverride(URI.create(endpoint))
        .httpClient(UrlConnectionHttpClient.create())
        .build();
  }

  private void warmUpSsmClient() {
    try {
      // Result discarded — see class javadoc.
      ssmClient.getParameter(GetParameterRequest.builder().name(parameterName).build());
    } catch (Exception e) {
      System.out.println("Could not warm up SSM client during checkpoint: " + e);
    }
  }

  private void warmUpManagementClient() {
    try {
      // Result discarded — see class javadoc. Building the client/request and attempting the call
      // is what loads the apigatewaymanagementapi classes and JITs the marshalling/signing path;
      // reaching a real endpoint isn't the point, so there's no reason to make this cleverer.
      warmupManagementClient.postToConnection(
          PostToConnectionRequest.builder()
              .connectionId("warmup")
              .data(SdkBytes.fromString("", StandardCharsets.UTF_8))
              .build());
    } catch (Exception e) {
      System.out.println("Could not warm up ApiGatewayManagementApiClient during checkpoint: " + e);
    }
  }
}
