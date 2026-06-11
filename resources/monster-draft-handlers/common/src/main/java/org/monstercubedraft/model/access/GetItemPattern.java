package org.monstercubedraft.model.access;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

public interface GetItemPattern {

  public GetItemRequest request();

  public GetItemResponse getFrom(DynamoDbClient dynamoDb);
}
