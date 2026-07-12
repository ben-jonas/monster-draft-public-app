package org.monstercubedraft;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.monstercubedraft.crac.WebSocketEndpointResource;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse.BatchItemFailure;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;

public class MainDraftHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

  static final String ENVKEY__WEBSOCKET_CALLBACK_URL_PARAM_NAME =
      "WEBSOCKET_CALLBACK_URL_PARAM_NAME";
  private static final String ACK_MESSAGE = "Received";

  private final ObjectMapper jsonMapper;
  private final WebSocketEndpointResource webSocketEndpointResource;

  // Built lazily from webSocketEndpointResource.endpoint() on first use, then reused across warm
  // invocations. There's only ever one WebSocket API/stage for this deployment, so one client
  // suffices — unlike the endpoint lookup itself, this doesn't need to vary per message.
  private volatile ApiGatewayManagementApiClient managementClient;

  public MainDraftHandler() {
    this(
        new ObjectMapper(),
        new WebSocketEndpointResource(System.getenv(ENVKEY__WEBSOCKET_CALLBACK_URL_PARAM_NAME)));
  }

  public MainDraftHandler(
      ObjectMapper jsonMapper, WebSocketEndpointResource webSocketEndpointResource) {
    this.jsonMapper = jsonMapper;
    this.webSocketEndpointResource = webSocketEndpointResource;
  }

  @Override
  public SQSBatchResponse handleRequest(SQSEvent input, Context context) {
    System.out.println(
        String.format("Received SQS batch of %d message(s).", input.getRecords().size()));

    List<BatchItemFailure> batchItemFailures = new ArrayList<>();

    for (SQSMessage message : input.getRecords()) {
      System.out.println(
          String.format(
              "[SQS message] messageId=%s | body=%s", message.getMessageId(), message.getBody()));
      try {
        acknowledgeReceipt(message);
      } catch (GoneException e) {
        // The client disconnected before we could ack. Not a failure to retry: stale connections
        // are cleaned up lazily via TTL, per the $disconnect no-op in api-stack.ts.
        System.out.println(
            String.format(
                "Connection gone for messageId=%s; treating receipt as acknowledged.",
                message.getMessageId()));
      } catch (Exception e) {
        System.out.println(
            String.format(
                "Failed to acknowledge messageId=%s: %s", message.getMessageId(), e));
        batchItemFailures.add(
            BatchItemFailure.builder().withItemIdentifier(message.getMessageId()).build());
      }
    }

    return SQSBatchResponse.builder().withBatchItemFailures(batchItemFailures).build();
  }

  private void acknowledgeReceipt(SQSMessage message) throws Exception {
    JsonNode item = jsonMapper.readTree(message.getBody()).path("item");
    String connectionId = item.path("connectionId").asText(null);

    if (connectionId == null) {
      System.out.println(
          String.format(
              "messageId=%s is missing connectionId; skipping ack.", message.getMessageId()));
      return;
    }

    managementClient()
        .postToConnection(
            PostToConnectionRequest.builder()
                .connectionId(connectionId)
                .data(SdkBytes.fromString(ACK_MESSAGE, StandardCharsets.UTF_8))
                .build());
  }

  private ApiGatewayManagementApiClient managementClient() {
    ApiGatewayManagementApiClient client = managementClient;
    if (client != null) {
      return client;
    }
    synchronized (this) {
      if (managementClient == null) {
        // webSocketEndpointResource.endpoint() blocks/throws on SSM failure by design: if it
        // throws here, this message is left unacknowledged and reported as a batch item failure
        // for SQS to retry, rather than this handler silently proceeding without an endpoint.
        managementClient =
            ApiGatewayManagementApiClient.builder()
                .endpointOverride(URI.create(webSocketEndpointResource.endpoint()))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
      }
      return managementClient;
    }
  }
}
