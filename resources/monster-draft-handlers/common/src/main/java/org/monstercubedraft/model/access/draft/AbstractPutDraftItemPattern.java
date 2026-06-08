package org.monstercubedraft.model.access.draft;

import org.monstercubedraft.model.access.WriteItemPattern;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

public abstract class AbstractPutDraftItemPattern
    implements WriteItemPattern<PutItemRequest, PutItemResponse> {

  protected final String tableName;
  protected final String draftId;

  AbstractPutDraftItemPattern(String tableName, String draftId) {
    this.tableName = tableName;
    this.draftId = draftId;
  }

  @Override
  public PutItemResponse writeTo(DynamoDbClient dynamoDb) {
    return dynamoDb.putItem(this.request());
  }
}
