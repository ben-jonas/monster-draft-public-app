package org.monstercubedraft;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

  private static final String ACK_MESSAGE = "Received";

  private final ObjectMapper jsonMapper;

  // One ApiGatewayManagementApiClient per WebSocket API endpoint (domainName + stage). The
  // endpoint isn't known until a message arrives (see api-stack.ts for why it can't be an env
  // var), so clients are built lazily and cached across warm invocations rather than per message.
  private final Map<String, ApiGatewayManagementApiClient> managementClientsByEndpoint;

  public MainDraftHandler() {
    this(new ObjectMapper(), new ConcurrentHashMap<>());
  }

  public MainDraftHandler(
      ObjectMapper jsonMapper,
      Map<String, ApiGatewayManagementApiClient> managementClientsByEndpoint) {
    this.jsonMapper = jsonMapper;
    this.managementClientsByEndpoint = managementClientsByEndpoint;
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
    String domainName = item.path("domainName").asText(null);
    String stage = item.path("stage").asText(null);

    if (connectionId == null || domainName == null || stage == null) {
      System.out.println(
          String.format(
              "messageId=%s is missing connectionId/domainName/stage; skipping ack.",
              message.getMessageId()));
      return;
    }

    managementClientFor(domainName, stage)
        .postToConnection(
            PostToConnectionRequest.builder()
                .connectionId(connectionId)
                .data(SdkBytes.fromString(ACK_MESSAGE, StandardCharsets.UTF_8))
                .build());
  }

  private ApiGatewayManagementApiClient managementClientFor(String domainName, String stage) {
    String endpoint = "https://" + domainName + "/" + stage;
    return managementClientsByEndpoint.computeIfAbsent(
        endpoint,
        e ->
            ApiGatewayManagementApiClient.builder()
                .endpointOverride(URI.create(e))
                .httpClient(UrlConnectionHttpClient.create())
                .build());
  }
}
