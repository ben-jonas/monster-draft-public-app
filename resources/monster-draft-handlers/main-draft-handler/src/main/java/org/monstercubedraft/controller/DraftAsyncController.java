package org.monstercubedraft.controller;

import java.util.concurrent.CompletableFuture;

import org.monstercubedraft.controller.types.enums.DraftRequestSource;
import org.monstercubedraft.controller.types.records.RawInputRecords.RawServerSentMessage;
import org.monstercubedraft.controller.types.records.RawInputRecords.RawWebsocketClientMessage;
import org.monstercubedraft.crac.AwsAsyncClientsResource;
import org.monstercubedraft.model.access.draft.DraftTableAccess;
import org.monstercubedraft.model.access.session.SessionTableAccess;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.view.DraftConverter;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DraftAsyncController {

  /* Package-scoped because this is tightly coupled to the ephemeral Workflow classes, which are
   * instantiated per-request, and take the entire Services record as a constructor arg*/
  static record Services(
      AwsAsyncClientsResource awsSdkAsyncClients,
      ObjectMapper objectMapper,
      DraftCommandParser draftCommandParser,
      DraftTableAccess draftTableAccess,
      SessionTableAccess sessionTableAccess,
      DraftConverter draftConverter) {}

  private final Services services;

  public DraftAsyncController(
      AwsAsyncClientsResource awsSdkAsyncClients,
      ObjectMapper mapper,
      DraftCommandParser parser,
      DraftTableAccess draftTableAccess,
      SessionTableAccess sessionTableAccess,
      DraftConverter draftConverter) {
    this.services =
        new Services(
            awsSdkAsyncClients,
            mapper,
            parser,
            draftTableAccess,
            sessionTableAccess,
            draftConverter);
  }

  public CompletableFuture<Void> handleSQSMessage(SQSMessage message)
      throws JsonMappingException, JsonProcessingException {
    JsonNode jsonBase = services.objectMapper.readTree(message.getBody());
    DraftRequestSource source =
        DraftRequestSource.fromSourceString(jsonBase.required("source").asText());
    JsonNode jsonItem = jsonBase.required("item");
    String requestBody = jsonItem.required("body").asText();
    switch (source) {
      case DraftRequestSource.APIGW_CLIENT:
        var clientMessage =
            new RawWebsocketClientMessage(
                jsonItem.required("wsConnectionId").asText(), requestBody);
        return new ClientSentAsyncWorkflow(services, clientMessage).start();
      default:
        DraftId draftId = new DraftId(jsonItem.required("draftId").asText());
        return doServerSentWorkflow(new RawServerSentMessage(draftId, requestBody));
    }
  }

  private CompletableFuture<Void> doServerSentWorkflow(RawServerSentMessage serverMessage) {
    return CompletableFuture.runAsync(() -> {});
  }
}
