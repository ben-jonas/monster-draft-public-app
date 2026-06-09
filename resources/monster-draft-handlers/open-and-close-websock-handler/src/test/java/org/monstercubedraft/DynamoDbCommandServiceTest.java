package org.monstercubedraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.monstercubedraft.DynamoDbCommandService.CommandResult;
import org.monstercubedraft.crac.DynamoDbClientResource;
import org.monstercubedraft.crac.IdGeneratorResource;
import org.monstercubedraft.model.types.SessionId;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

@ExtendWith(MockitoExtension.class)
public class DynamoDbCommandServiceTest {

  static final String GAME_TABLE_NAME = "GameTableName";
  static final String WSCONNECTIONS_TABLE_NAME = "WsConnectionsTableName";
  static final String TEST_WSCONNECTION_ID = "abc123";
  static final String TEST_GAME_ID = "def456";
  static final String TEST_SESSION_ID = "ghi789";

  private DynamoDbCommandService dynamoDbCommandService;

  // Lenient strictness because we access the DynamoClient via the Dynamo CRaC Resource for every
  // test except our no-null-arg enforcement tests.
  @Mock(strictness = Mock.Strictness.LENIENT)
  private DynamoDbClientResource mockDynamoResource;

  @Mock private DynamoDbClient mockDynamoDbClient;

  @Mock private IdGeneratorResource mockIdGeneratorResource;

  @Captor private ArgumentCaptor<UpdateItemRequest> updateItemRequestCaptor;

  @Captor private ArgumentCaptor<PutItemRequest> putItemRequestCaptor;

  @BeforeEach
  void setUp() {
    dynamoDbCommandService =
        new DynamoDbCommandService(
            mockDynamoResource, mockIdGeneratorResource, WSCONNECTIONS_TABLE_NAME, GAME_TABLE_NAME);
    when(mockDynamoResource.getClient()).thenReturn(mockDynamoDbClient);
  }

  @Test
  void constructor_requireAllArgsNonNull() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new DynamoDbCommandService(
                    null, mockIdGeneratorResource, WSCONNECTIONS_TABLE_NAME, GAME_TABLE_NAME));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new DynamoDbCommandService(
                    mockDynamoResource, null, WSCONNECTIONS_TABLE_NAME, GAME_TABLE_NAME));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new DynamoDbCommandService(
                    mockDynamoResource, mockIdGeneratorResource, null, GAME_TABLE_NAME));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new DynamoDbCommandService(
                    mockDynamoResource, mockIdGeneratorResource, WSCONNECTIONS_TABLE_NAME, null));
  }

  @Test
  void connectToExistingSession_requireAllArgsNonNull() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                dynamoDbCommandService.connectToExistingSession(
                    null, TEST_GAME_ID, TEST_SESSION_ID));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                dynamoDbCommandService.connectToExistingSession(
                    TEST_SESSION_ID, null, TEST_SESSION_ID));
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                dynamoDbCommandService.connectToExistingSession(
                    TEST_SESSION_ID, TEST_GAME_ID, null));
    verify(mockDynamoDbClient, never()).updateItem(any(UpdateItemRequest.class));
  }

  @Test
  void connectToExistingSession_updateFailsConditionCheck() {
    when(mockDynamoDbClient.updateItem(any(UpdateItemRequest.class)))
        .thenThrow(ConditionalCheckFailedException.class);
    CommandResult result =
        dynamoDbCommandService.connectToExistingSession(
            TEST_WSCONNECTION_ID, TEST_GAME_ID, TEST_SESSION_ID);
    assertThat(result).isExactlyInstanceOf(CommandResult.FailedCondition.class);
  }

  @Test
  void connectToExistingSession_validConditionCheck() {

    Map<String, AttributeValue> oldWsConnectionAttrMap =
        Map.of("wsConnectionId", AttributeValue.fromS(TEST_WSCONNECTION_ID));
    when(mockDynamoDbClient.updateItem(any(UpdateItemRequest.class)))
        .thenReturn(UpdateItemResponse.builder().attributes(oldWsConnectionAttrMap).build());
    CommandResult result =
        dynamoDbCommandService.connectToExistingSession(
            TEST_WSCONNECTION_ID, TEST_GAME_ID, TEST_SESSION_ID);
    assertThat(result).isExactlyInstanceOf(CommandResult.Succeeded.class);
    verify(mockDynamoDbClient).updateItem(updateItemRequestCaptor.capture());
    UpdateItemRequest capturedUpdateRequest = updateItemRequestCaptor.getValue();
    assertThat(capturedUpdateRequest.tableName()).isEqualTo(WSCONNECTIONS_TABLE_NAME);
    assertThat(capturedUpdateRequest.conditionExpression().contains("attribuge_exists(sessionId)"));
  }

  @Test
  void connectToNewSession_requireAllArgsNonNull() {
    assertThatNullPointerException()
        .isThrownBy(() -> dynamoDbCommandService.connectToNewSession(TEST_WSCONNECTION_ID, null));
    assertThatNullPointerException()
        .isThrownBy(() -> dynamoDbCommandService.connectToNewSession(null, TEST_GAME_ID));
    verify(mockDynamoDbClient, never()).updateItem(any(UpdateItemRequest.class));
    verify(mockDynamoDbClient, never()).putItem(any(PutItemRequest.class));
  }

  @Test
  void connectToNewSession_attemptIncrementPlayerCountFailsConditionCheck() {
    // This represents the first of two sequential calls to Dynamo failing.
    when(mockIdGeneratorResource.generateSessionId()).thenReturn(new SessionId("aaaaa11111"));
    when(mockDynamoDbClient.updateItem(any(UpdateItemRequest.class)))
        .thenThrow(ConditionalCheckFailedException.class);
    CommandResult result =
        dynamoDbCommandService.connectToNewSession(TEST_WSCONNECTION_ID, TEST_GAME_ID);
    assertThat(result).isExactlyInstanceOf(CommandResult.FailedCondition.class);
    verify(mockDynamoDbClient, never()).putItem(any(PutItemRequest.class));
  }

  @Test
  void connectToNewSession_attemptPutSessionFailsConditionCheck() {
    // This represents the second of two sequential calls to Dynamo failing.
    when(mockIdGeneratorResource.generateSessionId()).thenReturn(new SessionId("aaaaa11111"));
    when(mockDynamoDbClient.updateItem(any(UpdateItemRequest.class)))
        .thenReturn(
            UpdateItemResponse.builder()
                .attributes(Map.of("wsConnectionId", AttributeValue.fromS(TEST_WSCONNECTION_ID)))
                .build());
    when(mockDynamoDbClient.putItem(any(PutItemRequest.class)))
        .thenThrow(ConditionalCheckFailedException.class);
    CommandResult result =
        dynamoDbCommandService.connectToNewSession(TEST_WSCONNECTION_ID, TEST_GAME_ID);
    assertThat(result).isExactlyInstanceOf(CommandResult.FailedCondition.class);
  }

  @Test
  void connectToNewSession_validConditionChecks() {
    when(mockIdGeneratorResource.generateSessionId()).thenReturn(new SessionId("aaaaa11111"));
    when(mockDynamoDbClient.updateItem(any(UpdateItemRequest.class)))
        .thenReturn(
            UpdateItemResponse.builder()
                .attributes(Map.of("wsConnectionId", AttributeValue.fromS(TEST_WSCONNECTION_ID)))
                .build());
    when(mockDynamoDbClient.putItem(any(PutItemRequest.class)))
        .thenReturn(PutItemResponse.builder().build());
    CommandResult result =
        dynamoDbCommandService.connectToNewSession(TEST_WSCONNECTION_ID, TEST_GAME_ID);

    assertThat(result).isExactlyInstanceOf(CommandResult.Succeeded.class);
    verify(mockDynamoDbClient).updateItem(updateItemRequestCaptor.capture());
    verify(mockDynamoDbClient).putItem(putItemRequestCaptor.capture());
    UpdateItemRequest capturedUpdateRequest = updateItemRequestCaptor.getValue();
    PutItemRequest capturedPutRequest = putItemRequestCaptor.getValue();

    assertThat(capturedUpdateRequest.tableName()).isEqualTo(GAME_TABLE_NAME);
    assertThat(capturedUpdateRequest.conditionExpression().contains("attribuge_exists(gameId)"));
    assertThat(capturedUpdateRequest.conditionExpression().contains("occupants < maxOccupants"));

    assertThat(capturedPutRequest.tableName()).isEqualTo(WSCONNECTIONS_TABLE_NAME);
    assertThat(capturedPutRequest.conditionExpression().contains("attribute_not_exists(gameRef"));
    // TODO need more assertions; interrogate the update request more to ensure structure.

  }

  @Test
  void disconnectUser_testCantFail() {
    assumeTrue(false, "Method incomplete");
  }
}
