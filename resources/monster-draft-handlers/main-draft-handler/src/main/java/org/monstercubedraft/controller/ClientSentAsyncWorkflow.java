package org.monstercubedraft.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.naming.LimitExceededException;

import org.monstercubedraft.DraftCommandParser.ParseCommandException;
import org.monstercubedraft.controller.DraftAsyncController.Services;
import org.monstercubedraft.controller.types.records.DraftCommand;
import org.monstercubedraft.controller.types.records.RawInputRecords.RawWebsocketClientMessage;
import org.monstercubedraft.model.constants.SessionTableConstants;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.SessionId;
import org.monstercubedraft.model.types.records.DraftSession;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.ApiGatewayManagementApiException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

class ClientSentAsyncWorkflow {

  private final Services controllerServices;
  private final RawWebsocketClientMessage clientMessage;

  ClientSentAsyncWorkflow(Services controllerServices, RawWebsocketClientMessage clientMessage) {
    this.controllerServices = controllerServices;
    this.clientMessage = clientMessage;
  }

  private static class Accumulator {
    private DraftCommand parsedCommand;
    private DraftSession session;
    private QueryResponse queryResponse;

    private Accumulator() {}

    Accumulator(DraftCommand parsedCommand) {
      this.parsedCommand = Objects.requireNonNull(parsedCommand);
    }

    public static Accumulator EMPTY = new Accumulator();

    Accumulator withSession(DraftSession session) {
      Accumulator accumulatorOut = EMPTY.ingest(this);
      accumulatorOut.session = Objects.requireNonNull(session);
      return accumulatorOut;
    }

    Accumulator withDraft(QueryResponse queryDraftResponse) {
      Accumulator accumulatorOut = EMPTY.ingest(this);
      accumulatorOut.queryResponse = Objects.requireNonNull(queryDraftResponse);
      return accumulatorOut;
    }

    Accumulator ingest(Accumulator other) {
      DraftCommand commandOut = parsedCommand == null ? other.parsedCommand : parsedCommand;
      DraftSession sessionOut = session == null ? other.session : session;
      QueryResponse queryDraftResponseOut =
          queryResponse == null ? other.queryResponse : queryResponse;
      var accumulatorOut = new Accumulator(commandOut);
      accumulatorOut.session = sessionOut;
      accumulatorOut.queryResponse = queryDraftResponseOut;
      return accumulatorOut;
    }
  }

  CompletableFuture<Void> start() {
    return CompletableFuture.supplyAsync(
            () -> {
              return controllerServices.draftCommandParser().parse(clientMessage);
            })
        .handle(
            (command, ex) -> {
              final CompletableFuture<?> future;
              if (command != null) {
                final var accumulator = new Accumulator(command);
                future =
                    switch (command.verb()) {
                      case ACKNOWLEDGE -> notifyWsClient(command.id() + " ack");
                      case WHO_AM_I -> whoAmI(accumulator);
                      case SET_SELF_AS_LEADER -> CompletableFuture.completedFuture(null);
                      case GET_VISIBLE_STATE -> CompletableFuture.completedFuture(null);
                      case TAKE_SEAT -> CompletableFuture.completedFuture(null);
                      case STAND_UP -> CompletableFuture.completedFuture(null);
                      case READY -> CompletableFuture.completedFuture(null);
                      default -> notifyWsClient(command.id() + " not ack");
                    };
              } else {
                Throwable cause = WorkflowUtils.unwrapAsyncExceptionTypes(ex);
                String errNotificationOut;
                if (cause instanceof ParseCommandException) {
                  errNotificationOut = "##(parsing error): " + ex.getMessage();
                } else {
                  errNotificationOut = "##(unknown error during parsing)";
                }
                throw new WorkflowException(cause, errNotificationOut);
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
                Throwable cause = WorkflowUtils.unwrapAsyncExceptionTypes(ex);
                cause.printStackTrace();
                String errMsgOut;
                if (cause instanceof WorkflowException) {
                  errMsgOut = ((WorkflowException) cause).getClientResponse();
                } else {
                  errMsgOut = "##(unknown error during command resolution)";
                }
                future = notifyWsClient(errMsgOut);
              }
              return future;
            })
        .thenCompose(Function.identity());
  }

  private CompletableFuture<Void> whoAmI(Accumulator accumulator) {
    return populateSessionData(accumulator)
        .thenCompose(
            acc ->
                notifyWsClient(
                    String.format(
                        "%s %s", acc.parsedCommand.id(), acc.session.sessionId().toString())));
  }

  private CompletableFuture<Void> notifyWsClient(String message) {
    return controllerServices
        .awsSdkAsyncClients()
        .supplyApiGwMgmtApi()
        .get()
        .postToConnection(
            PostToConnectionRequest.builder()
                .connectionId(this.clientMessage.wsConnectionId())
                .data(SdkBytes.fromString(message, StandardCharsets.UTF_8))
                .build())
        .handleAsync(
            (_, ex) -> {
              if (ex != null) {
                Throwable cause = WorkflowUtils.unwrapAsyncExceptionTypes(ex);
                System.out.println(
                    "Exception occurred when sending message to WebSocket client: "
                        + cause.getMessage());
                if (cause instanceof ApiGatewayManagementApiException
                    && !(cause instanceof GoneException)
                    && !(cause instanceof LimitExceededException)) {
                  controllerServices.awsSdkAsyncClients().refreshApiGwMgmtApiAsyncClient();
                }
              }
              return null;
            });
  }

  private CompletableFuture<Accumulator> populateSessionData(Accumulator accumulator) {
    QueryRequest queryForSessionsMatchingWsConnectionId =
        controllerServices
            .sessionTableAccess()
            .onGsi_WsConnectionId(this.clientMessage.wsConnectionId())
            .queryAll()
            .request();
    return controllerServices
        .awsSdkAsyncClients()
        .getDynamo()
        .query(queryForSessionsMatchingWsConnectionId)
        .thenApply(
            queryResponse -> {
              if (!queryResponse.hasItems())
                throw new WorkflowException(
                    "Table or index setup is wrong; "
                        + "query returned null collection (empty collection would be fine).",
                    String.format("#%s: error fetching session", accumulator.parsedCommand.id()));
              if (queryResponse.items().size() < 1)
                throw new WorkflowException(
                    "Sessions table doesn't yet have an entry matching this connection; "
                        + "client should wait a short while and retry.",
                    String.format(
                        "#%s: Session not yet populated; wait and retry.",
                        accumulator.parsedCommand.id()));
              if (queryResponse.items().size() > 1)
                System.out.println(
                    "Ws connection ID was not unique. "
                        + "This should be impossible from the APIGW perspective; "
                        + "the app must have double-counted at some point.");
              Map<String, AttributeValue> sessionEntry = queryResponse.items().getFirst();
              var session =
                  new DraftSession(
                      new DraftId(sessionEntry.get(SessionTableConstants.PK_DRAFT_ID).s()),
                      new SessionId(sessionEntry.get(SessionTableConstants.SK_SESSION_ID).s()),
                      sessionEntry.get(SessionTableConstants.K_WS_CONNECTION_ID).s());
              return accumulator.withSession(session);
            });
  }

  private CompletableFuture<Accumulator> populateDraftData(Accumulator accumulator) {
    // Note that the session must have been acquired from DynamoDB first, otherwise NPE will be
    // thrown here
    DraftId draftId = accumulator.session.draftId();
    QueryRequest queryForDraftPages =
        controllerServices.draftTableAccess().onPartition(draftId).queryCorePages().request();
    return controllerServices
        .awsSdkAsyncClients()
        .getDynamo()
        .query(queryForDraftPages)
        .thenApply(
            queryResponse -> {
              if (!queryResponse.hasItems())
                throw new WorkflowException(
                    "Table or index setup is wrong; "
                        + "query returned null collection (empty collection would be fine).",
                    String.format("#%s: error fetching session", accumulator.parsedCommand.id()));
              return accumulator.withDraft(queryResponse);
            });
  }
}
