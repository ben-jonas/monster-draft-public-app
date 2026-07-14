package org.monstercubedraft.crac;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.LimitExceededException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

/**
 * Encapsulates an SsmClient and associated ApiGatewayManagementApiClient, resolving the latter's
 * endpoint from SSM Parameter Store (published by MonsterDraftPublicAppApiStack) and exposing
 * WebSocket pushes through {@link #message(String, String)} rather than handing the client itself
 * to callers — this lets the resource self-heal a stale client without callers needing to know a
 * rebuild happened.
 *
 * <p>{@link #beforeCheckpoint} only makes throwaway calls whose results are discarded, to warm up
 * request marshalling / credential resolution / JIT &amp; classloading for both SDK code paths
 * before the snapshot is taken.
 *
 * <p>{@link #afterRestore} fetches the callback URL from SSM and builds the real client from it.
 * Its fetch is still best-effort; {@link #message(String, String)} is the load-bearing fallback,
 * building on first real use if {@link #afterRestore} didn't land, and self-healing on later use by
 * rebuilding from a fresh SSM lookup whenever the current client fails for a reason a rebuild could
 * plausibly fix.
 *
 * <p>Not thread-safe by design; we expect to replace the {@link ApiGatewayManagementApiClient} with
 * the async client, using a single thread w/ callbacks.
 */
public class ApiClientResource implements Resource {

  static final String ENVKEY__WEBSOCKET_CALLBACK_URL_PARAM_NAME =
      "WEBSOCKET_CALLBACK_URL_PARAM_NAME";

  private static final String WARMUP_ENDPOINT = "https://apigatewaymanagementapi-warmup.invalid";

  private final Function<String, ApiGatewayManagementApiClient> managementClientFactory;
  private final SsmClient ssmClient;
  private final String parameterName;
  private ApiGatewayManagementApiClient managementClient;

  public ApiClientResource() {
    this(
        endpoint ->
            ApiGatewayManagementApiClient.builder()
                .endpointOverride(URI.create(endpoint))
                .httpClient(UrlConnectionHttpClient.create())
                .build(),
        SsmClient.builder().httpClient(UrlConnectionHttpClient.create()).build(),
        System.getenv(ENVKEY__WEBSOCKET_CALLBACK_URL_PARAM_NAME));
  }

  public ApiClientResource(
      Function<String, ApiGatewayManagementApiClient> managementClientFactory,
      SsmClient ssmClient,
      String parameterName) {
    this.managementClientFactory = Objects.requireNonNull(managementClientFactory);
    this.ssmClient = Objects.requireNonNull(ssmClient);
    this.parameterName = Objects.requireNonNull(parameterName);
    Core.getGlobalContext().register(this);
  }

  @Override
  public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
    warmUpSsmClient();
    warmUpManagementClient();
  }

  @Override
  public void afterRestore(Context<? extends Resource> context) throws Exception {
    try {
      managementClient = fetchAndBuildClient();
    } catch (Exception e) {
      // Best-effort; see class javadoc. message() retries on first real use if this didn't land.
      System.out.println(
          "Could not eagerly resolve WebSocket callback endpoint during restore: " + e);
    }
  }

  /**
   * Pushes {@code message} to the WebSocket connection identified by {@code wsConnectionId}.
   *
   * <p>If the current client throws something other than {@link GoneException} or {@link
   * LimitExceededException}, this fetches a fresh endpoint from SSM, builds a new client, and
   * retries once. A successful retry replaces the cached client so subsequent calls skip straight
   * to it. A failed retry re-throws the original exception from the cached client, not whatever the
   * retry itself failed with.
   */
  public void message(String wsConnectionId, String message) {
    ApiGatewayManagementApiClient client = managementClient;
    if (client == null) {
      // Not primed by afterRestore. No existing client to fall back to, so this throws directly
      // on failure rather than into the rethrow-original-exception path below.
      client = fetchAndBuildClient();
      managementClient = client;
    }

    try {
      postToConnection(client, wsConnectionId, message);
    } catch (GoneException | LimitExceededException e) {
      throw e;
    } catch (Exception original) {
      ApiGatewayManagementApiClient rebuilt;
      try {
        rebuilt = fetchAndBuildClient();
        postToConnection(rebuilt, wsConnectionId, message);
      } catch (Exception retryFailure) {
        System.out.println(
            "Failed to post to APIGW with existing client; "
                + "failed to self-heal with new APIGW mgmt client.");
        throw original;
      }
      managementClient = rebuilt;
      System.out.println("Replaced current APIGW mgmt client w/ new client.");
    }
  }

  private void postToConnection(
      ApiGatewayManagementApiClient client, String wsConnectionId, String message) {
    client.postToConnection(
        PostToConnectionRequest.builder()
            .connectionId(wsConnectionId)
            .data(SdkBytes.fromString(message, StandardCharsets.UTF_8))
            .build());
  }

  private ApiGatewayManagementApiClient fetchAndBuildClient() {
    String endpoint =
        ssmClient
            .getParameter(GetParameterRequest.builder().name(parameterName).build())
            .parameter()
            .value();
    return managementClientFactory.apply(endpoint);
  }

  private void warmUpSsmClient() {
    try {
      // Result discarded — see class javadoc.
      ssmClient.getParameter(GetParameterRequest.builder().name(parameterName).build());
    } catch (Exception e) {
      System.out.println("Task failed successfully; loaded SsmClient class for beforeCheckpoint()");
    }
  }

  private void warmUpManagementClient() {
    try {
      // Result discarded — see class javadoc.
      managementClientFactory
          .apply(WARMUP_ENDPOINT)
          .postToConnection(
              PostToConnectionRequest.builder()
                  .connectionId("warmup")
                  .data(SdkBytes.fromString("", StandardCharsets.UTF_8))
                  .build());
    } catch (Exception e) {
      System.out.println(
          "Task failed successfully; loaded ApiGatewayManagementApiClient for beforeCheckpoint()");
    }
  }
}
