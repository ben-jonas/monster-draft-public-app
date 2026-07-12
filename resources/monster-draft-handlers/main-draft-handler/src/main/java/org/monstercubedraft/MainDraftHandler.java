package org.monstercubedraft;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.monstercubedraft.crac.ApiClientResource;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse.BatchItemFailure;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;

public class MainDraftHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

  static final String ENVKEY__WEBSOCKET_CALLBACK_URL_PARAM_NAME =
      "WEBSOCKET_CALLBACK_URL_PARAM_NAME";
  private static final String ACK_MESSAGE = "Received";

  private final ObjectMapper jsonMapper;
  private final ApiClientResource apiClientResource;

  public MainDraftHandler() {
    this(
        new ObjectMapper(),
        new ApiClientResource(System.getenv(ENVKEY__WEBSOCKET_CALLBACK_URL_PARAM_NAME)));
  }

  public MainDraftHandler(ObjectMapper jsonMapper, ApiClientResource apiClientResource) {
    this.jsonMapper = jsonMapper;
    this.apiClientResource = apiClientResource;
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

    apiClientResource
        .managementClient()
        .postToConnection(
            PostToConnectionRequest.builder()
                .connectionId(connectionId)
                .data(SdkBytes.fromString(ACK_MESSAGE, StandardCharsets.UTF_8))
                .build());
  }
}
