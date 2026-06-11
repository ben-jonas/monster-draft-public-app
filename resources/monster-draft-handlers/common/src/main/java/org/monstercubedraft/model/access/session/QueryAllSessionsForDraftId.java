package org.monstercubedraft.model.access.session;

import static org.monstercubedraft.model.constants.SessionTableConstants.PK_DRAFT_ID;

import java.util.Map;

import org.monstercubedraft.model.access.ReadItemsPattern;
import org.monstercubedraft.model.types.DraftId;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

public class QueryAllSessionsForDraftId implements ReadItemsPattern<QueryRequest, QueryResponse> {

  private final String tableName;
  private final DraftId draftId;

  QueryAllSessionsForDraftId(String tableName, DraftId draftId) {
    this.tableName = tableName;
    this.draftId = draftId;
  }

  @Override
  public QueryRequest request() {
    return QueryRequest.builder()
        .tableName(tableName)
        .keyConditionExpression("#draftId = :draftId")
        .expressionAttributeNames(Map.of("#draftId", PK_DRAFT_ID))
        .expressionAttributeValues(Map.of(":draftId", AttributeValue.fromS(draftId.toString())))
        .build();
  }

  @Override
  public QueryResponse queryFrom(DynamoDbClient dynamoDb) {
    return dynamoDb.query(request());
  }
}
