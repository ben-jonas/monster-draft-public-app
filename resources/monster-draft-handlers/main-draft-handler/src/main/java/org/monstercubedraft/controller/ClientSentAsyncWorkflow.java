package org.monstercubedraft.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.naming.LimitExceededException;

import org.monstercubedraft.controller.DraftAsyncController.Services;
import org.monstercubedraft.controller.DraftCommandParser.ParseCommandException;
import org.monstercubedraft.controller.types.records.RawInputRecords.RawWebsocketClientMessage;
import org.monstercubedraft.model.constants.SessionTableConstants;
import org.monstercubedraft.model.types.DraftCore;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.SessionId;
import org.monstercubedraft.model.types.records.DraftSession;
import org.monstercubedraft.view.types.ClientViewableDraft;
import org.monstercubedraft.view.types.records.CommandSuccessResponse;

import com.fasterxml.jackson.core.JacksonException;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.ApiGatewayManagementApiException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

class ClientSentAsyncWorkflow {

  private final Services controllerServices;
  private final RawWebsocketClientMessage clientMessage;

  ClientSentAsyncWorkflow(Services controllerServices, RawWebsocketClientMessage clientMessage) {
    this.controllerServices = controllerServices;
    this.clientMessage = clientMessage;
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
                      case ACKNOWLEDGE ->
                          notifyWsClientSuccess(new CommandSuccessResponse<>(command.id(), "ack"));
                      case WHO_AM_I -> whoAmI(accumulator);
                      case SET_SELF_AS_LEADER -> CompletableFuture.completedFuture(null);
                      case GET_VISIBLE_STATE -> getVisibleState(accumulator);
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

  /*command: who_am_i*/
  private CompletableFuture<Void> whoAmI(Accumulator accumulator) {
    return populateSessionData(accumulator)
        .thenCompose(
            acc -> {
              CommandSuccessResponse<SessionId> response =
                  new CommandSuccessResponse<>(acc.getCommand().id(), acc.getSession().sessionId());
              return notifyWsClientSuccess(response);
            });
  }

  /*command: get_visible_state*/
  private CompletableFuture<Void> getVisibleState(Accumulator accumulator) {
    return populateSessionData(accumulator)
        .thenCompose(this::populateDraftData)
        .thenCompose(
            acc -> {
              ClientViewableDraft draftView =
                  controllerServices
                      .draftConverter()
                      .viewForClientSession(acc.getDraft(), acc.getSession().sessionId());
              CommandSuccessResponse<ClientViewableDraft> response =
                  new CommandSuccessResponse<>(acc.getCommand().id(), draftView);
              return notifyWsClientSuccess(response);
            });
  }

  /*utils*/
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

  private CompletableFuture<Void> notifyWsClientSuccess(CommandSuccessResponse<?> response) {
    try {
      String responseStr = controllerServices.objectMapper().writeValueAsString(response);
      return notifyWsClient(responseStr);
    } catch (JacksonException ex) {
      throw new WorkflowException(ex, "Serialization failed");
    }
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
                    String.format("#%s: error fetching session", accumulator.getCommand().id()));
              if (queryResponse.items().size() < 1)
                throw new WorkflowException(
                    "Sessions table doesn't yet have an entry matching this connection; "
                        + "client should wait a short while and retry.",
                    String.format(
                        "#%s: Session not yet populated; wait and retry.",
                        accumulator.getCommand().id()));
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
    DraftId draftId = accumulator.getSession().draftId();
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
                    String.format("#%s: error fetching session", accumulator.getCommand().id()));
              return accumulator.withDraft(new DraftCore(queryResponse));
            });
  }
}
