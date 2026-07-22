package org.monstercubedraft.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import javax.naming.LimitExceededException;

import org.monstercubedraft.controller.DraftCommandParser.ParseCommandException;
import org.monstercubedraft.controller.types.DraftCommand;
import org.monstercubedraft.controller.types.DraftRequestSource;
import org.monstercubedraft.controller.types.RawInputRecords.RawServerSentMessage;
import org.monstercubedraft.controller.types.RawInputRecords.RawWebsocketClientMessage;
import org.monstercubedraft.crac.AwsAsyncClientsResource;
import org.monstercubedraft.model.access.draft.DraftTableAccess;
import org.monstercubedraft.model.access.session.SessionTableAccess;
import org.monstercubedraft.model.constants.SessionTableConstants;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.DraftSession;
import org.monstercubedraft.model.types.SessionId;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.ApiGatewayManagementApiException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

public class DraftAsyncController {

  private final AwsAsyncClientsResource awsSdkClients;
  private final ObjectMapper objectMapper;
  private final DraftCommandParser commandParser;
  private final DraftTableAccess draftTableAccess;
  private final SessionTableAccess sessionTableAccess;

  public DraftAsyncController(
      AwsAsyncClientsResource clients,
      ObjectMapper mapper,
      DraftCommandParser parser,
      DraftTableAccess draftTableAccess,
      SessionTableAccess sessionTableAccess) {
    this.awsSdkClients = clients;
    this.objectMapper = mapper;
    this.commandParser = parser;
    this.draftTableAccess = draftTableAccess;
    this.sessionTableAccess = sessionTableAccess;
  }

  public CompletableFuture<Void> handleSQSMessage(SQSMessage message)
      throws JsonMappingException, JsonProcessingException {
    JsonNode jsonBase = objectMapper.readTree(message.getBody());
    DraftRequestSource source =
        DraftRequestSource.fromSourceString(jsonBase.required("source").asText());
    JsonNode jsonItem = jsonBase.required("item");
    String requestBody = jsonItem.required("body").asText();
    switch (source) {
      case DraftRequestSource.APIGW_CLIENT:
        var clientMessage =
            new RawWebsocketClientMessage(
                jsonItem.required("wsConnectionId").asText(), requestBody);
        return new ClientWorkflow(clientMessage).startWorkflowAsync();
      default:
        DraftId draftId = new DraftId(jsonItem.required("draftId").asText());
        return doServerSentWorkflow(new RawServerSentMessage(draftId, requestBody));
    }
  }

  static Throwable unwrapAsyncExceptionTypes(Throwable ex) {
    while (ex != null
        && (ex instanceof CompletionException || ex instanceof ExecutionException)
        && ex.getCause() != null) {
      ex = ex.getCause();
    }
    return ex;
  }

  class ClientWorkflow {
    // we maintain these in scope as they get populated by asynchronous calls. They could instead be
    // part of some WorkflowAccumulator object, with all the Futures being genericized as
    // "CompletableFuture<WorkflowAccumulator>".
    private final RawWebsocketClientMessage clientMessage;
    private DraftCommand command;
    private DraftSession session;
    private GetItemResponse getDraftResponse;

    ClientWorkflow(RawWebsocketClientMessage clientMessage) {
      this.clientMessage = clientMessage;
    }

    CompletableFuture<Void> startWorkflowAsync() {
      return CompletableFuture.supplyAsync(
              () -> {
                return commandParser.parse(clientMessage);
              })
          .handle(
              (command, ex) -> {
                final CompletableFuture<Void> future;
                if (ex == null) {
                  future =
                      switch (command.verb()) {
                        case ACKNOWLEDGE -> notifyWsClient(command.id() + " ack");
                        case WHO_AM_I -> whoAmI(command);
                        case SET_SELF_AS_LEADER -> setSelfAsLeader(command);
                        case GET_VISIBLE_STATE -> getVisibleState(command);
                        case TAKE_SEAT -> takeSeat(command);
                        case STAND_UP -> standUp(command);
                        case READY -> ready(command);
                        default -> notifyWsClient(command.id() + " not ack");
                      };
                } else {
                  Throwable cause = unwrapAsyncExceptionTypes(ex);
                  String errNotificationOut;
                  if (cause instanceof ParseCommandException) {
                    errNotificationOut = "##(parsing error): " + ex.getMessage();
                  } else {
                    errNotificationOut = "##(unknown error during parsing)";
                  }
                  future = notifyWsClient(errNotificationOut);
                }
                return future;
              })
          .thenCompose(Function.identity())
          .handle(
              (_, ex) -> {
                final CompletableFuture<Void> future;
                if (ex == null) {
                  future = CompletableFuture.completedFuture(null);
                } else {
                  System.out.println(
                      "Error during command handling: "
                          + unwrapAsyncExceptionTypes(ex).getMessage());
                  future = notifyWsClient(command.id() + " failed");
                }
                return future;
              })
          .thenCompose(Function.identity());
    }

    private CompletableFuture<Void> notifyWsClient(String message) {
      return awsSdkClients
          .supplyApiGwMgmtApi()
          .get()
          .postToConnection(
              PostToConnectionRequest.builder()
                  .connectionId(clientMessage.wsConnectionId())
                  .data(SdkBytes.fromString(message, StandardCharsets.UTF_8))
                  .build())
          .handleAsync(
              (_, ex) -> {
                if (ex != null) {
                  Throwable cause = unwrapAsyncExceptionTypes(ex);
                  System.out.println(
                      "Exception occurred when sending message to WebSocket client: "
                          + cause.getMessage());
                  if (cause instanceof ApiGatewayManagementApiException
                      && !(cause instanceof GoneException)
                      && !(cause instanceof LimitExceededException)) {
                    awsSdkClients.refreshApiGwMgmtApiAsyncClient();
                  }
                }
                return null;
              });
    }

    private CompletableFuture<Void> populateSessionData() {
      QueryRequest queryForSessionsMatchingWsConnectionId =
          sessionTableAccess
              .onGsi_WsConnectionId(clientMessage.wsConnectionId())
              .queryAll()
              .request();
      return awsSdkClients
          .getDynamo()
          .query(queryForSessionsMatchingWsConnectionId)
          .thenAccept(
              queryResponse -> {
                if (!queryResponse.hasItems())
                  throw new RuntimeException(
                      "Table or index setup is wrong; "
                          + "query returned null collection (empty collection would be fine).");
                if (queryResponse.items().size() < 1)
                  throw new RuntimeException(
                      "Sessions table doesn't yet have an entry matching this connection; "
                          + "client should wait a short while and retry.");
                if (queryResponse.items().size() > 1)
                  System.out.println(
                      "Ws connection ID was not unique. "
                          + "This should be impossible from the APIGW perspective; "
                          + "the app must have double-counted at some point.");
                Map<String, AttributeValue> sessionEntry = queryResponse.items().getFirst();
                this.session =
                    new DraftSession(
                        new DraftId(sessionEntry.get(SessionTableConstants.PK_DRAFT_ID).s()),
                        new SessionId(sessionEntry.get(SessionTableConstants.SK_SESSION_ID).s()),
                        sessionEntry.get(SessionTableConstants.K_WS_CONNECTION_ID).s());
              });
    }

    private CompletableFuture<Void> whoAmI(DraftCommand command) {
      return populateSessionData()
          .thenCompose(
              _ -> {
                return notifyWsClient(command.id() + " " + session.sessionId().toString());
              });
    }

    private CompletableFuture<Void> setSelfAsLeader(DraftCommand command) {
      // TODO implement command
      throw new UnsupportedOperationException("unimplemented");
    }

    private CompletableFuture<Void> getVisibleState(DraftCommand command) {
      // TODO implement command
      throw new UnsupportedOperationException("unimplemented");
    }

    private CompletableFuture<Void> takeSeat(DraftCommand command) {
      // TODO implement command
      throw new UnsupportedOperationException("unimplemented");
    }

    private CompletableFuture<Void> standUp(DraftCommand command) {
      // TODO implement command
      throw new UnsupportedOperationException("unimplemented");
    }

    private CompletableFuture<Void> ready(DraftCommand command) {
      // TODO implement command
      throw new UnsupportedOperationException("unimplemented");
    }
  }

  private CompletableFuture<Void> doServerSentWorkflow(RawServerSentMessage serverMessage) {
    return CompletableFuture.runAsync(() -> {});
  }
}
