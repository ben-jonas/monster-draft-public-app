package org.monstercubedraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.monstercubedraft.crac.DynamoDbClientResource;
import org.monstercubedraft.crac.IdGeneratorResource;
import org.monstercubedraft.model.access.WriteItemPattern;
import org.monstercubedraft.model.access.draft.DraftTableAccess;
import org.monstercubedraft.model.access.session.SessionTableAccess;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.SessionAlias;
import org.monstercubedraft.model.types.SessionId;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

@ExtendWith(MockitoExtension.class)
public class OpenWebsocketHandlerTest {

  static final String GAME_TABLE_NAME = "test-draft-table";
  static final String DRAFT_ID_FROM_API = "LrXQHFyi_LvzohBcE_IPQBjQFy";
  static final String WSCONNECTION_ID = "someWsConnId=";
  static final SessionId PREGEN_SESSION_ID = new SessionId("zezzim7890");
  static final SessionAlias PREGEN_SESSION_ALIAS = new SessionAlias("z00");

  @Mock Context context;
  @Mock APIGatewayV2WebSocketEvent event;
  @Mock APIGatewayV2WebSocketEvent.RequestContext eventRequestContext;

  // Any time we use the CRAC resource it's only to get the client
  @Mock(strictness = Mock.Strictness.LENIENT)
  DynamoDbClientResource dynamoResource;

  @Mock DynamoDbClient dynamoClient;
  @Mock IdGeneratorResource idGenResource;

  @Mock DraftTableAccess draftTableAccess;
  @Mock DraftTableAccess.AccessOnPartition draftAccessOnPartition;
  @Mock WriteItemPattern<UpdateItemRequest, UpdateItemResponse> addSessionPattern;

  @Mock SessionTableAccess sessionTableAccess;
  @Mock SessionTableAccess.AccessOnPartition sessionAccessOnPartition;
  @Mock WriteItemPattern<PutItemRequest, PutItemResponse> putSessionPattern;

  OpenWebsocketHandler wsHandler;

  @BeforeEach
  void setUp() {
    wsHandler =
        new OpenWebsocketHandler(
            dynamoResource, idGenResource, draftTableAccess, sessionTableAccess);
    when(event.getRequestContext()).thenReturn(eventRequestContext);
    when(eventRequestContext.getConnectionId()).thenReturn(WSCONNECTION_ID);
    when(dynamoResource.getClient()).thenReturn(dynamoClient);
  }

  @Test
  void handleRequest_missingDraftIdParam_returns400() {
    Map<String, String> qsParams = Map.of("session_id", PREGEN_SESSION_ID.toString());
    when(event.getQueryStringParameters()).thenReturn(qsParams);
    assertThat(wsHandler.handleRequest(event, context).getStatusCode()).isEqualTo(400);
  }

  @Test
  void handleRequest_misformattedDraftId_returns400() {
    Map<String, String> qsParams = Map.of("draft_id", "IllegalDraftId");
    when(event.getQueryStringParameters()).thenReturn(qsParams);
    assertThat(wsHandler.handleRequest(event, context).getStatusCode()).isEqualTo(400);
  }

  @Test
  void handleRequest_successfulNewSession_returns200() {
    Map<String, String> qsParams = Map.of("draft_id", DRAFT_ID_FROM_API);
    when(event.getQueryStringParameters()).thenReturn(qsParams);

    when(idGenResource.generateSessionId()).thenReturn(PREGEN_SESSION_ID);
    when(idGenResource.generateSessionAlias()).thenReturn(PREGEN_SESSION_ALIAS);

    when(draftTableAccess.onPartition(any(DraftId.class))).thenReturn(draftAccessOnPartition);
    when(draftAccessOnPartition.addSession(PREGEN_SESSION_ID, PREGEN_SESSION_ALIAS))
        .thenReturn(addSessionPattern);

    when(sessionTableAccess.onPartition(any(DraftId.class))).thenReturn(sessionAccessOnPartition);
    when(sessionAccessOnPartition.putSession(
            eq(PREGEN_SESSION_ID), eq(WSCONNECTION_ID), any(ZonedDateTime.class)))
        .thenReturn(putSessionPattern);
    APIGatewayV2WebSocketResponse response = wsHandler.handleRequest(event, context);
    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.getBody()).isEqualTo(PREGEN_SESSION_ID.toString());
  }

  @Test
  void handleRequest_misformattedSessionId_returns400() {
    String misformedSessionString = "misfrmd";
    Map<String, String> qsParams =
        Map.of("draft_id", DRAFT_ID_FROM_API, "session_id", misformedSessionString);
    when(event.getQueryStringParameters()).thenReturn(qsParams);
    assertThat(wsHandler.handleRequest(event, context).getStatusCode()).isEqualTo(400);
  }

  @Test
  void handleRequest_conditionalFailure_returns400() {
    Map<String, String> qsParams =
        Map.of("draft_id", DRAFT_ID_FROM_API, "session_id", PREGEN_SESSION_ID.toString());
    when(event.getQueryStringParameters()).thenReturn(qsParams);

    when(sessionTableAccess.onPartition(any(DraftId.class))).thenReturn(sessionAccessOnPartition);
    when(sessionAccessOnPartition.putSession(
            eq(PREGEN_SESSION_ID), eq(WSCONNECTION_ID), any(ZonedDateTime.class)))
        .thenReturn(putSessionPattern);
    when(putSessionPattern.writeTo(dynamoClient)).thenThrow(ConditionalCheckFailedException.class);

    assertThat(wsHandler.handleRequest(event, context).getStatusCode()).isEqualTo(400);
  }
}
