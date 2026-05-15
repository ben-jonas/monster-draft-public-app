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
import org.monstercubedraft.crac.DynamoDbClientResource;
import org.monstercubedraft.crac.IdGeneratorResource;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
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

    @BeforeEach
    void setUp() {
        dynamoDbCommandService = new DynamoDbCommandService(
            mockDynamoResource, mockIdGeneratorResource, WSCONNECTIONS_TABLE_NAME, GAME_TABLE_NAME);
        when(mockDynamoResource.getClient()).thenReturn(mockDynamoDbClient);
    }

    @Test 
    void connectToExistingSession_updateFailsConditionCheck() {
        when(mockDynamoDbClient.updateItem(any(UpdateItemRequest.class)))
        .thenThrow(ConditionalCheckFailedException.class);
        String result = dynamoDbCommandService.connectToExistingSession(
            TEST_WSCONNECTION_ID, TEST_GAME_ID, TEST_SESSION_ID);
        assertThat(result).isNotEqualTo("success");
    }

    @Test
    void connectToExistingSession_validConditionCheck() {
        when(mockDynamoDbClient.updateItem(any(UpdateItemRequest.class)))
        .thenReturn(
            UpdateItemResponse.builder()
                .attributes(Map.of(
                    "wsConnectionId", AttributeValue.fromS(TEST_WSCONNECTION_ID)))
                .build());
        String result = dynamoDbCommandService.connectToExistingSession(
            TEST_WSCONNECTION_ID, TEST_GAME_ID, TEST_SESSION_ID);
        assertThat(result).isEqualTo("success");
        verify(mockDynamoDbClient).updateItem(updateItemRequestCaptor.capture());
        UpdateItemRequest capturedUpdateRequest = updateItemRequestCaptor.getValue();
        assertThat(capturedUpdateRequest.tableName()).isEqualTo(WSCONNECTIONS_TABLE_NAME);
        // need more assertions; interrogate the update request more
    }

    @Test
    void connectToExistingSession_enforceAllArgsMustBeNonNull() {
        assertThatNullPointerException().isThrownBy(() -> 
            dynamoDbCommandService.connectToExistingSession(
                null, TEST_GAME_ID, TEST_SESSION_ID
        ));
        assertThatNullPointerException().isThrownBy(() -> 
            dynamoDbCommandService.connectToExistingSession(
                TEST_SESSION_ID, null, TEST_SESSION_ID
        ));
        assertThatNullPointerException().isThrownBy(() -> 
            dynamoDbCommandService.connectToExistingSession(
                TEST_SESSION_ID, TEST_GAME_ID, null
        ));
        verify(mockDynamoDbClient, never()).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void connectToNewSession_gameNotFound() {

    }

    @Test
    void connectToNewSession_validGameId() {

    }

    @Test
    void connectToNewSession_gameIsFull() {

    }

    @Test
    void disconnectUser_testCantFail() {
        assumeTrue(false, "Method incomplete");
    }
}
