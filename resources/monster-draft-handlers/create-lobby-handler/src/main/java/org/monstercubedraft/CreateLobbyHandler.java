package org.monstercubedraft;

import java.util.Map;
import java.util.Objects;

import org.monstercubedraft.crac.DynamoDbClientResource;
import org.monstercubedraft.crac.IdGeneratorResource;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public class CreateLobbyHandler
    implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

  static final String ENVKEY__GAME_TABLE_NAME = "GAME_TABLE_NAME";

  private final DynamoDbClientResource dynamoResource;
  private final IdGeneratorResource idGenResource;

  public CreateLobbyHandler() {
    this(new DynamoDbClientResource(), new IdGeneratorResource());
  }

  public CreateLobbyHandler(
      DynamoDbClientResource dynamoResource, IdGeneratorResource idGenResource) {
    this.dynamoResource = Objects.requireNonNull(dynamoResource);
    this.idGenResource = Objects.requireNonNull(idGenResource);
  }

  @Override
  public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
    try {
      String newGameId = idGenResource.generateGameId();
      PutItemRequest newLobbyRequest =
          PutItemRequest.builder()
              .tableName(ENVKEY__GAME_TABLE_NAME)
              .conditionExpression("NOTEXISTS_PLACEHOLDER")
              .item(
                  Map.of(
                      "PLACEHOLDER_PARTITIONK",
                      AttributeValue.fromS("PARTITION_V"),
                      "PLACEHOLDER_SORTK",
                      AttributeValue.fromS("SORTK"),
                      "PLACEHOLDER_K",
                      AttributeValue.fromS("PLACEHOLDER_V"),
                      "PLACEHOLDER_K2",
                      AttributeValue.fromN("PLACEHOLDER_N")))
              .build();
      dynamoResource.getClient().putItem(newLobbyRequest);
      return APIGatewayV2HTTPResponse.builder().withBody(newGameId).withStatusCode(200).build();
    } catch (Exception e) {
      System.out.print(e);
      return APIGatewayV2HTTPResponse.builder().withStatusCode(500).build();
    }
  }
}
