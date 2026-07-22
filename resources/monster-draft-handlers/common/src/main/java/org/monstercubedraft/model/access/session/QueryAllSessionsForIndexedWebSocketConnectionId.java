package org.monstercubedraft.model.access.session;

import java.util.Map;

import org.monstercubedraft.model.access.ReadItemsPattern;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

public class QueryAllSessionsForIndexedWebSocketConnectionId
    implements ReadItemsPattern<QueryRequest, QueryResponse> {

  private static final String INDEX_NAME = "SessionsByWsId";
  private static final String GSI_KEY_NAME = "wsConnectionId";

  private final String tableName;
  private final String wsConnectionId;

  public QueryAllSessionsForIndexedWebSocketConnectionId(String tableName, String wsConnectionId) {
    this.tableName = tableName;
    this.wsConnectionId = wsConnectionId;
  }

  @Override
  public QueryRequest request() {
    return QueryRequest.builder()
        .tableName(tableName)
        .indexName(INDEX_NAME)
        .keyConditionExpression("#wsConnectionId = :wsConnectionId")
        .expressionAttributeNames(Map.of("#wsConnectionId", GSI_KEY_NAME))
        .expressionAttributeValues(Map.of(":wsConnectionId", AttributeValue.fromS(wsConnectionId)))
        .build();
  }

  @Override
  public QueryResponse queryFrom(DynamoDbClient dynamoDb) {
    return dynamoDb.query(request());
  }
}
