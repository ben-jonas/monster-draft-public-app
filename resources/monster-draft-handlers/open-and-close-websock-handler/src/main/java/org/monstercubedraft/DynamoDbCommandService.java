package org.monstercubedraft;

import java.util.Map;
import java.util.Objects;

import org.monstercubedraft.crac.DynamoDbClientResource;
import org.monstercubedraft.crac.IdGeneratorResource;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

/**
 * 
 */
public class DynamoDbCommandService {
    static final String ENVKEY__WSCONNECTIONS_TABLE_NAME = "WSCONNECTIONS_TABLE_NAME";
    static final String ENVKEY__GAME_TABLE_NAME = "GAME_TABLE_NAME";

    private final DynamoDbClientResource dynamoResource;
    private final IdGeneratorResource idGeneratorResource;
    private final String connectionsTableName;
    private final String gameTableName;

    public DynamoDbCommandService() {
        this(
            new DynamoDbClientResource(),
            new IdGeneratorResource(),
            System.getenv(ENVKEY__WSCONNECTIONS_TABLE_NAME),
            System.getenv(ENVKEY__GAME_TABLE_NAME));
    }

    public DynamoDbCommandService(DynamoDbClientResource dynamoResource,
                                  IdGeneratorResource idGeneratorResource,
                                  String connectionsTableName,
                                  String gameTableName) {
        this.dynamoResource = dynamoResource;
        this.idGeneratorResource = idGeneratorResource;
        this.connectionsTableName = connectionsTableName;
        this.gameTableName = gameTableName;
    }

    public String connectToExistingSession(String wsConnectionId,
                                           String gameId,
                                           String sessionId) {
        Objects.requireNonNull(wsConnectionId);
        Objects.requireNonNull(gameId);
        Objects.requireNonNull(sessionId);
        UpdateItemRequest updateWsConnectionRequest = UpdateItemRequest.builder()
            .tableName(this.connectionsTableName)
            .key(Map.of(
                "sessionId", AttributeValue.fromS(sessionId),
                "gameRef", AttributeValue.fromS(gameId)
            ))
            .updateExpression("SET wsConnectionId :newWsConnectionId")
            .expressionAttributeValues(Map.of(
                ":newWsConnectionId", AttributeValue.fromS(wsConnectionId)
            ))
            .conditionExpression("attribute_exists(sessionId)")
            .returnValues(ReturnValue.UPDATED_OLD)
            .build();
        try {
            UpdateItemResponse updateWsConnectionResponse = 
            dynamoResource.getClient().updateItem(updateWsConnectionRequest);
            String oldWsConnectionId = 
                updateWsConnectionResponse.attributes().get("wsConnectionId").s();
            // TODO Attempt to disconnect the old connection in APIGW (best effort)
            return "success";
        } catch (ConditionalCheckFailedException conditionEx) {
            // Currently our code doesn't care why the condition check fails. If we want to write
            // and test different subcondition failures, we need to add
            // ".returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)"
            // to the UpdateItemRequest, then interrogate the thrown exception (e) for e.item().
            return "failure";
        }
    }

    public String connectToNewSession(String wsConnectionId, String gameId) {
        String sessionId = idGeneratorResource.generateSessionId();
        String joinGameAttempt = attemptIncrementPlayerCount(gameId);

        if (joinGameAttempt.equals("success")) {
            // add session entry to connections
            PutItemRequest addNewSessionEntry = PutItemRequest.builder()
                .tableName(this.connectionsTableName)
                .item(Map.of(
                    "sessionId", AttributeValue.fromS(sessionId),
                    "gameRef", AttributeValue.fromS(gameId),
                    "wsConnectionId", AttributeValue.fromS(wsConnectionId)
                ))
                .build();
            try {
                dynamoResource.getClient().putItem(addNewSessionEntry);
                return "success";
            } catch (DynamoDbException dynamoEx) {
                return "failure";
            }
        } else {
            return "failure";
        }
    }

    private String attemptIncrementPlayerCount(String gameId) {
        UpdateItemRequest attemptIncrementPlayerCountRequest = UpdateItemRequest.builder()
            .tableName(this.gameTableName)
            .key(Map.of(
                "gameId", AttributeValue.fromS(gameId)))
            .updateExpression("ADD occupants :inc")
            .expressionAttributeValues(Map.of(
                ":inc", AttributeValue.fromN("1")
            ))
            .conditionExpression("attribute_exists(gameId) AND occupants < maxOccupants")
            .build();
        try {
            dynamoResource.getClient().updateItem(attemptIncrementPlayerCountRequest);
        } catch (ConditionalCheckFailedException e) {
            return "failure";
        } catch (DynamoDbException dynamoEx) {
            System.out.printf("Other exception: %s", dynamoEx.getMessage());
            return "failure";
        }
        return "success";
    }

    public String disconnectUser(String wsConnectionId, Map<String, String> queryParams) {
        // TODO Could maintain small list of expired connectionIDs (maybe just 1?) to prove client
        //  can reconnect
        // TODO Update the session to remove the connection. Use LSI?
        return "success";
    }
}
