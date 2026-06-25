package org.monstercubedraft;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;

import org.monstercubedraft.crac.DynamoDbClientResource;
import org.monstercubedraft.crac.IdGeneratorResource;
import org.monstercubedraft.model.access.WriteItemPattern;
import org.monstercubedraft.model.access.draft.DraftTableAccess;
import org.monstercubedraft.model.access.session.SessionTableAccess;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.SessionAlias;
import org.monstercubedraft.model.types.SessionId;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;

import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

public class OpenWebsocketHandler
    implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

  static final String ENVKEY__GAME_TABLE_NAME = "GAME_TABLE_NAME";
  static final String ENVKEY__WSCONNECTIONS_TABLE_NAME = "WSCONNECTIONS_TABLE_NAME";

  private final DynamoDbClientResource dynamoResource;
  private final IdGeneratorResource idGeneratorResource;
  private final DraftTableAccess draftTableAccess;
  private final SessionTableAccess sessionTableAccess;

  public OpenWebsocketHandler() {
    this(
        new DynamoDbClientResource(),
        new IdGeneratorResource(),
        new DraftTableAccess(System.getenv(ENVKEY__GAME_TABLE_NAME)),
        new SessionTableAccess(System.getenv(ENVKEY__WSCONNECTIONS_TABLE_NAME)));
  }

  public OpenWebsocketHandler(
      DynamoDbClientResource dynamoResource,
      IdGeneratorResource idGeneratorResource,
      DraftTableAccess draftTableAccess,
      SessionTableAccess sessionTableAccess) {
    this.dynamoResource = Objects.requireNonNull(dynamoResource);
    this.idGeneratorResource = Objects.requireNonNull(idGeneratorResource);
    this.draftTableAccess = Objects.requireNonNull(draftTableAccess);
    this.sessionTableAccess = Objects.requireNonNull(sessionTableAccess);
  }

  @Override
  public APIGatewayV2WebSocketResponse handleRequest(
      APIGatewayV2WebSocketEvent input, Context context) {
    // try/catch everything so that the lambda context isn't killed in the event of an unhandled
    // exception
    try {
      return handleRequestInternal(input, context);
    } catch (Exception e) {
      System.out.print("Unhandled exception thrown: " + e.getMessage());
      return generateResponse(500);
    }
  }

  public APIGatewayV2WebSocketResponse handleRequestInternal(
      APIGatewayV2WebSocketEvent input, Context context) {
    System.out.println(input.toString());

    String wsConnectionId = input.getRequestContext().getConnectionId();
    Map<String, String> queryStringParams = input.getQueryStringParameters();
    if (queryStringParams == null) queryStringParams = Map.of();

    final DraftId draftId;
    try {
      draftId = new DraftId(queryStringParams.get("draft_id"));
    } catch (NullPointerException e) {
      System.out.println("'draft_id' query param was not specified");
      return generateResponse(400);
    } catch (IllegalArgumentException e) {
      System.out.println("'draft_id' query param was malformed");
      return generateResponse(400);
    }

    final SessionId sessionId;
    try {
      String sessionIdStr = queryStringParams.get("session_id");
      sessionId = sessionIdStr == null ? null : new SessionId(sessionIdStr);
    } catch (IllegalArgumentException e) {
      System.out.println("'session_id' query param was malformed");
      return generateResponse(400);
    }

    if (sessionId == null) {
      System.out.println("Attempting to connect to draft as new session");
      final SessionId generatedSessionId = idGeneratorResource.generateSessionId();
      final SessionAlias generatedSessionAlias = idGeneratorResource.generateSessionAlias();
      WriteItemPattern<UpdateItemRequest, UpdateItemResponse> addSessionPattern =
          draftTableAccess
              .onPartition(draftId)
              .addSession(generatedSessionId, generatedSessionAlias);
      try {
        addSessionPattern.writeTo(dynamoResource.getClient());
        try {
          sessionTableAccess
              .onPartition(draftId)
              .putSession(generatedSessionId, wsConnectionId, ZonedDateTime.now().plusHours(1))
              .writeTo(dynamoResource.getClient());
        } catch (Exception e) {
          // TODO Use SQS to queue up a job to remove the session we just built from the Draft
          // table. (This should only happen in the rare case of a transient dynamodb failure that
          // is somehow not handled by SDK's retries.)
          return generateResponse(500);
        }
        return generateResponse(200);
      } catch (ConditionalCheckFailedException e) {
        // TODO Write & use "UpdateDraftAddSession.interpretConditionFailures()", then log result
        return generateResponse(400);
      }
    } else {
      System.out.println("Attempting to connect to draft as existing session");
      try {
        sessionTableAccess
            .onPartition(draftId)
            .putSession(sessionId, wsConnectionId, ZonedDateTime.now().plusHours(1))
            .writeTo(dynamoResource.getClient());
        return generateResponse(200);
      } catch (Exception e) {
        return generateResponse(500);
      }
    }
  }

  private APIGatewayV2WebSocketResponse generateResponse(int statusCode) {
    var response = new APIGatewayV2WebSocketResponse();
    response.setStatusCode(statusCode);
    return response;
  }
}
