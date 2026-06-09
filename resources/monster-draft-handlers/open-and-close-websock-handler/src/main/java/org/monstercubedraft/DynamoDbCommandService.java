package org.monstercubedraft;

import java.util.Map;
import java.util.Objects;

import org.monstercubedraft.crac.DynamoDbClientResource;
import org.monstercubedraft.crac.IdGeneratorResource;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

public class DynamoDbCommandService {
  static final String ENVKEY__WSCONNECTIONS_TABLE_NAME = "WSCONNECTIONS_TABLE_NAME";
  static final String ENVKEY__GAME_TABLE_NAME = "GAME_TABLE_NAME";

  private final DynamoDbClientResource dynamoResource;
  private final IdGeneratorResource idGeneratorResource;
  private final String connectionsTableName;
  private final String gameTableName;

  public sealed interface CommandResult
      permits CommandResult.Succeeded, CommandResult.FailedCondition {
    record Succeeded() implements CommandResult {}

    record FailedCondition(ConditionalCheckFailedException e) implements CommandResult {}
  }

  public DynamoDbCommandService() {
    this(
        new DynamoDbClientResource(),
        new IdGeneratorResource(),
        System.getenv(ENVKEY__WSCONNECTIONS_TABLE_NAME),
        System.getenv(ENVKEY__GAME_TABLE_NAME));
  }

  public DynamoDbCommandService(
      DynamoDbClientResource dynamoResource,
      IdGeneratorResource idGeneratorResource,
      String connectionsTableName,
      String gameTableName) {
    Objects.requireNonNull(dynamoResource);
    Objects.requireNonNull(idGeneratorResource);
    Objects.requireNonNull(connectionsTableName);
    Objects.requireNonNull(gameTableName);
    this.dynamoResource = dynamoResource;
    this.idGeneratorResource = idGeneratorResource;
    this.connectionsTableName = connectionsTableName;
    this.gameTableName = gameTableName;
  }

  public CommandResult connectToExistingSession(
      String wsConnectionId, String gameId, String sessionId) {
    Objects.requireNonNull(wsConnectionId);
    Objects.requireNonNull(gameId);
    Objects.requireNonNull(sessionId);

    UpdateItemRequest updateWsConnectionRequest =
        UpdateItemRequest.builder()
            .tableName(this.connectionsTableName)
            .key(
                Map.of(
                    "sessionId", AttributeValue.fromS(sessionId),
                    "gameRef", AttributeValue.fromS(gameId)))
            .updateExpression("SET wsConnectionId :newWsConnectionId")
            .expressionAttributeValues(
                Map.of(":newWsConnectionId", AttributeValue.fromS(wsConnectionId)))
            .conditionExpression("attribute_exists(sessionId)")
            .returnValues(ReturnValue.UPDATED_OLD)
            .build();
    try {
      UpdateItemResponse updateWsConnectionResponse =
          dynamoResource.getClient().updateItem(updateWsConnectionRequest);
      String oldWsConnectionId = updateWsConnectionResponse.attributes().get("wsConnectionId").s();
      if (oldWsConnectionId != null) {
        // TODO Attempt to disconnect the old connection in APIGW (best effort)
      }
      return new CommandResult.Succeeded();
    } catch (ConditionalCheckFailedException e) {
      return new CommandResult.FailedCondition(e);
      // If we want to write and test different
      // subcondition failures, we need to add
      // ".returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)"
      // to the UpdateItemRequest, then interrogate the thrown exception (e) for e.item().
    }
  }

  public CommandResult connectToNewSession(String wsConnectionId, String gameId) {
    Objects.requireNonNull(wsConnectionId);
    Objects.requireNonNull(gameId);
    String sessionId = idGeneratorResource.generateSessionId().toString();
    CommandResult joinGameAttempt = attemptIncrementPlayerCount(gameId);
    switch (joinGameAttempt) {
      case CommandResult.Succeeded _ -> {
        return attemptNewSessionEntry(sessionId, gameId, wsConnectionId);
      }
      case CommandResult.FailedCondition updatePlayerCountFailure -> {
        return updatePlayerCountFailure;
      }
    }
  }

  private CommandResult attemptIncrementPlayerCount(String gameId) {
    UpdateItemRequest attemptIncrementPlayerCountRequest =
        UpdateItemRequest.builder()
            .tableName(this.gameTableName)
            .key(Map.of("gameId", AttributeValue.fromS(gameId)))
            .updateExpression("ADD occupants :inc")
            .expressionAttributeValues(Map.of(":inc", AttributeValue.fromN("1")))
            .conditionExpression("attribute_exists(gameId) AND occupants < maxOccupants")
            .build();
    System.out.println("Attempting to increment player count for given game.");
    try {
      dynamoResource.getClient().updateItem(attemptIncrementPlayerCountRequest);
    } catch (ConditionalCheckFailedException e) {
      return new CommandResult.FailedCondition(e);
    }
    return new CommandResult.Succeeded();
  }

  private CommandResult attemptNewSessionEntry(
      String sessionId, String gameId, String wsConnectionId) {
    PutItemRequest addNewSessionEntry =
        PutItemRequest.builder()
            .tableName(this.connectionsTableName)
            .item(
                Map.of(
                    "sessionId", AttributeValue.fromS(sessionId),
                    "gameRef", AttributeValue.fromS(gameId),
                    "wsConnectionId", AttributeValue.fromS(wsConnectionId)))
            .conditionExpression("attribute_not_exists(gameRef)")
            .build();
    System.out.println("Attempting to add new Session.");
    try {
      dynamoResource.getClient().putItem(addNewSessionEntry);
      return new CommandResult.Succeeded();
    } catch (ConditionalCheckFailedException e) {
      // TODO if this fails, we also need to use SQS to queue up a task to decrement
      // the gameRef's player count back down.
      return new CommandResult.FailedCondition(e);
    }
  }

  public String disconnectUser(String wsConnectionId, Map<String, String> queryParams) {
    // TODO Could maintain small list of expired connectionIDs (maybe just 1?) to prove client
    //  can reconnect
    // TODO Update the session to remove the connection. Use LSI?
    return "success";
  }
}
