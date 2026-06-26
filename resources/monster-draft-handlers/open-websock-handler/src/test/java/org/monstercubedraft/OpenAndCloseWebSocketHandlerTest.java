package org.monstercubedraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.monstercubedraft.DynamoDbCommandService.CommandResult;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;

import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@ExtendWith(MockitoExtension.class)
class OpenAndCloseWebSocketHandlerTest {

  static final String WSCONNECTIONS_TABLE_NAME = "test-connections-table";
  static final String GAME_TABLE_NAME = "test-game-table";
  static final String GAME_ID = "SOME_GAME_ID";
  static final String SESSION_ID = "SOME_SESSION_ID";
  static final String WSCONNECTION_ID = "SOME_WSCONNECTION_ID";

  @Mock DynamoDbCommandService dynamoDbCommandService;
  @Mock Context context;
  @Mock APIGatewayV2WebSocketEvent event;
  @Mock APIGatewayV2WebSocketEvent.RequestContext eventRequestContext;

  OpenAndCloseWebSocketHandler wsHandler;

  @BeforeEach
  void setUp() {
    wsHandler = new OpenAndCloseWebSocketHandler(dynamoDbCommandService);
    when(event.getRequestContext()).thenReturn(eventRequestContext);
  }

  @Test
  void connectRoute_successfulNewSession_returns200() {
    Map<String, String> qsParams = Map.of("game_id", GAME_ID);
    when(event.getQueryStringParameters()).thenReturn(qsParams);
    when(eventRequestContext.getRouteKey()).thenReturn("$connect");
    when(eventRequestContext.getConnectionId()).thenReturn(WSCONNECTION_ID);

    when(dynamoDbCommandService.connectToNewSession(WSCONNECTION_ID, GAME_ID))
        .thenReturn(new CommandResult.Succeeded());
    int statusCode = wsHandler.handleRequest(event, context).getStatusCode();
    assertEquals(200, statusCode);
  }

  @Test
  void connectRoute_failedlNewSession_returns400() {
    Map<String, String> qsParams = Map.of("game_id", GAME_ID);
    when(event.getQueryStringParameters()).thenReturn(qsParams);
    when(eventRequestContext.getRouteKey()).thenReturn("$connect");
    when(eventRequestContext.getConnectionId()).thenReturn(WSCONNECTION_ID);

    when(dynamoDbCommandService.connectToNewSession(WSCONNECTION_ID, GAME_ID))
        .thenReturn(
            new CommandResult.FailedCondition(ConditionalCheckFailedException.builder().build()));
    int statusCode = wsHandler.handleRequest(event, context).getStatusCode();
    assertEquals(400, statusCode);
  }

  @Test
  void connectRoute_successfulExistingSession_returns200() {
    Map<String, String> qsParams = Map.of("game_id", GAME_ID, "session_id", SESSION_ID);
    when(event.getQueryStringParameters()).thenReturn(qsParams);
    when(eventRequestContext.getRouteKey()).thenReturn("$connect");
    when(eventRequestContext.getConnectionId()).thenReturn(WSCONNECTION_ID);

    when(dynamoDbCommandService.connectToExistingSession(WSCONNECTION_ID, GAME_ID, SESSION_ID))
        .thenReturn(new CommandResult.Succeeded());
    int statusCode = wsHandler.handleRequest(event, context).getStatusCode();
    assertEquals(200, statusCode);
  }

  @Test
  void connectRoute_failedExistingSession_returns400() {
    Map<String, String> qsParams = Map.of("game_id", GAME_ID, "session_id", SESSION_ID);
    when(event.getQueryStringParameters()).thenReturn(qsParams);
    when(eventRequestContext.getRouteKey()).thenReturn("$connect");
    when(eventRequestContext.getConnectionId()).thenReturn(WSCONNECTION_ID);

    when(dynamoDbCommandService.connectToExistingSession(WSCONNECTION_ID, GAME_ID, SESSION_ID))
        .thenReturn(
            new CommandResult.FailedCondition(ConditionalCheckFailedException.builder().build()));
    int statusCode = wsHandler.handleRequest(event, context).getStatusCode();
    assertEquals(400, statusCode);
  }

  @Test
  void disconnectRoute_returns200() {
    when(eventRequestContext.getRouteKey()).thenReturn("$disconnect");
    when(eventRequestContext.getConnectionId()).thenReturn(WSCONNECTION_ID);

    when(dynamoDbCommandService.disconnectUser(WSCONNECTION_ID, Map.of())).thenReturn("success");
    int statusCode = wsHandler.handleRequest(event, context).getStatusCode();
    assertEquals(200, statusCode);
  }

  @Test
  void defaultRoute_returns400() {
    when(eventRequestContext.getRouteKey()).thenReturn("$default");
    int statusCode = wsHandler.handleRequest(event, context).getStatusCode();
    assertEquals(400, statusCode);
  }
}
