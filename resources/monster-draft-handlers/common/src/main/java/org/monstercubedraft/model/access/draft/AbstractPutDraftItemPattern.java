package org.monstercubedraft.model.access.draft;

import org.monstercubedraft.model.access.WriteItemPattern;
import org.monstercubedraft.model.types.DraftId;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

public abstract class AbstractPutDraftItemPattern
    implements WriteItemPattern<PutItemRequest, PutItemResponse> {

  protected final String tableName;
  protected final DraftId draftId;

  AbstractPutDraftItemPattern(String tableName, DraftId draftId) {
    this.tableName = tableName;
    this.draftId = draftId;
  }

  @Override
  public PutItemResponse writeTo(DynamoDbClient dynamoDb) {
    return dynamoDb.putItem(this.request());
  }
}
