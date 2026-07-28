package org.monstercubedraft.model.access.draft;

import static java.util.Map.entry;
import static org.monstercubedraft.model.constants.DraftTableConstants.PK_GAME_ID;
import static org.monstercubedraft.model.constants.DraftTableConstants.SK_PAGE;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;

import java.util.Map;

import org.monstercubedraft.model.access.ReadItemsPattern;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.enums.DraftPageName;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

public class QueryCoreDraftPages implements ReadItemsPattern<QueryRequest, QueryResponse> {

  private final String tableName;
  private final DraftId draftId;

  QueryCoreDraftPages(String tableName, DraftId draftId) {
    this.tableName = tableName;
    this.draftId = draftId;
  }

  @Override
  public QueryRequest request() {
    return QueryRequest.builder()
        .tableName(tableName)
        .keyConditionExpression("#draftId = :draftId and begins_with(#page, :namespace)")
        .expressionAttributeNames(
            Map.ofEntries(entry("#draftId", PK_GAME_ID), entry("#page", SK_PAGE)))
        .expressionAttributeValues(
            Map.of(
                ":namespace",
                fromS(DraftPageName.INDEX.getNamespace()),
                ":draftId",
                fromS(draftId.toString())))
        .build();
  }

  @Override
  public QueryResponse queryFrom(DynamoDbClient dynamoDb) {
    return dynamoDb.query(this.request());
  }
}
