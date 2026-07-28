package org.monstercubedraft.crac;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiAsyncClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiAsyncClientBuilder;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder;
import software.amazon.awssdk.services.ssm.SsmAsyncClient;
import software.amazon.awssdk.services.ssm.SsmAsyncClientBuilder;

public class AwsAsyncClientsResource implements Resource {

  static final String ENVKEY__WEBSOCKET_CALLBACK_URL_PARAM_NAME =
      "WEBSOCKET_CALLBACK_URL_PARAM_NAME";

  static final String FAKE_APIGW_MGMT_ENDPOINT = "https://fakeUri.invalid";

  static final Duration MINIMUM_TIME_BETWEEN_SSM_CALLS_TO_GET_APIGW_MGMT_ENDPOINT =
      Duration.ofSeconds(2);

  private final String websocketCallbackUriParamName;
  private final Supplier<SdkAsyncHttpClient> httpClientSupplier;
  private final Supplier<SsmAsyncClientBuilder> ssmClientBuilderSupplier;
  private final Supplier<DynamoDbAsyncClientBuilder> dynamoClientBuilderSupplier;
  private final Supplier<ApiGatewayManagementApiAsyncClientBuilder> apiGwClientBuilderSupplier;

  private SdkAsyncHttpClient asyncHttpClient;
  private SsmAsyncClient ssmAsyncClient;
  private DynamoDbAsyncClient dynamoDbAsyncClient;
  private volatile ApiGatewayManagementApiAsyncClient apiGwMgmtAsyncClient;

  // these should be evaluated together in a synchronized method or block
  private String lastKnownApiGwManagementEndpoint = FAKE_APIGW_MGMT_ENDPOINT;
  private Instant mostRecentApiGwManagementEndpointRetrieval = Instant.EPOCH;

  public AwsAsyncClientsResource(
      String websocketCallbackUriParamName,
      Supplier<SdkAsyncHttpClient> httpClientSupplier,
      Supplier<SsmAsyncClientBuilder> ssmClientBuilderSupplier,
      Supplier<DynamoDbAsyncClientBuilder> dynamoClientBuilderSupplier,
      Supplier<ApiGatewayManagementApiAsyncClientBuilder> apiGwClientBuilderSupplier) {
    this.websocketCallbackUriParamName = Objects.requireNonNull(websocketCallbackUriParamName);
    this.httpClientSupplier = Objects.requireNonNull(httpClientSupplier);
    this.ssmClientBuilderSupplier = Objects.requireNonNull(ssmClientBuilderSupplier);
    this.dynamoClientBuilderSupplier = Objects.requireNonNull(dynamoClientBuilderSupplier);
    this.apiGwClientBuilderSupplier = Objects.requireNonNull(apiGwClientBuilderSupplier);
    initStableClients();
    initApiGwManagementApiAsyncClient();
    Core.getGlobalContext().register(this);
  }

  public AwsAsyncClientsResource() {
    this(
        System.getenv(ENVKEY__WEBSOCKET_CALLBACK_URL_PARAM_NAME),
        NettyNioAsyncHttpClient::create,
        SsmAsyncClient::builder,
        DynamoDbAsyncClient::builder,
        ApiGatewayManagementApiAsyncClient::builder);
  }

  private void initStableClients() {
    // By a "stable client", we mean one which will not need replacement / reconfiguration as long
    // as this code is deployed and operating.
    asyncHttpClient = httpClientSupplier.get();
    dynamoDbAsyncClient = dynamoClientBuilderSupplier.get().httpClient(asyncHttpClient).build();
    ssmAsyncClient = ssmClientBuilderSupplier.get().httpClient(asyncHttpClient).build();
  }

  private void initApiGwManagementApiAsyncClient() {
    apiGwMgmtAsyncClient =
        apiGwClientBuilderSupplier
            .get()
            .endpointOverride(URI.create(lastKnownApiGwManagementEndpoint))
            .httpClient(asyncHttpClient)
            .build();
  }

  /**
   * Confers w/ SSM for the latest APIGW management endpoint. If our local APIGW client is using an
   * outdated endpoint (that is to say, different from the SSM-provided endpoint), close the current
   * APIGW client and swap it out for the new one.
   *
   * @return true if the client was replaced; false otherwise. Any replacement that took place will
   *     be observed by future calls to supplyApiGwMgmtApi().
   */
  public synchronized boolean refreshApiGwMgmtApiAsyncClient() {
    if (mostRecentApiGwManagementEndpointRetrieval.isAfter(
        Instant.now().minus(MINIMUM_TIME_BETWEEN_SSM_CALLS_TO_GET_APIGW_MGMT_ENDPOINT))) {
      return false;
    }
    mostRecentApiGwManagementEndpointRetrieval = Instant.now();
    String retrievedEndpoint =
        ssmAsyncClient
            .getParameter(gp -> gp.name(websocketCallbackUriParamName))
            .handle(
                (getParamResponse, throwable) -> {
                  if (getParamResponse != null) {
                    return getParamResponse.parameter().value();
                  } else {
                    var cause = (RuntimeException) throwable.getCause();
                    System.out.println(cause.getMessage());
                    throw cause; // TODO is rethrow actually appropriate? And should I prefer to end
                                 // the request or the entire Lambda execution?
                  }
                })
            .join();
    if (!(retrievedEndpoint.equals(lastKnownApiGwManagementEndpoint))) {
      lastKnownApiGwManagementEndpoint = retrievedEndpoint;
      initApiGwManagementApiAsyncClient();
      return true;
    }
    return false;
  }

  @Override
  public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
    CompletableFuture<?> warmDynamo =
        dynamoDbAsyncClient
            .describeTable(dt -> dt.tableName("fakeTableName"))
            .handle((_, _) -> null);

    CompletableFuture<?> warmSsm =
        ssmAsyncClient.getParameter(gp -> gp.name("fakeParameterName")).handle((_, _) -> null);

    CompletableFuture<?> warmApiGw =
        apiGwMgmtAsyncClient
            .postToConnection(
                ptc ->
                    ptc.connectionId("warmup")
                        .data(SdkBytes.fromString("", StandardCharsets.UTF_8)))
            .handle((_, _) -> null);

    CompletableFuture.allOf(warmDynamo, warmSsm, warmApiGw).join();
    asyncHttpClient.close();
  }

  @Override
  public void afterRestore(Context<? extends Resource> context) throws Exception {
    initStableClients();
    refreshApiGwMgmtApiAsyncClient();
  }

  /**
   * Returns the DynamoDbAsyncClient that is managed by this Resource.
   *
   * @return the DynamoDb async client.
   */
  public DynamoDbAsyncClient getDynamo() {
    return dynamoDbAsyncClient;
  }

  /**
   * Returns the SsmAsyncClient that is managed by this Resource.
   *
   * @return the SSM async client.
   */
  public SsmAsyncClient getSsm() {
    return ssmAsyncClient;
  }

  /**
   * Returns a supplier for the most current ApiGatewayManagementApiAsyncClient that is managed by
   * this Resource. Internally, the ApiGW client may change if an external change forces the
   * endpoint to need to be retrieved from SSM, so this Supplier will always point to the current
   * APIGW mgmt async client.
   *
   * @return a supplier for the APIGW management async client. Callers should get a new Client from
   *     the Supplier every time they need the Client's functionality; otherwise they risk using a
   *     closed Client.
   */
  public Supplier<ApiGatewayManagementApiAsyncClient> supplyApiGwMgmtApi() {
    return () -> apiGwMgmtAsyncClient;
  }
}
