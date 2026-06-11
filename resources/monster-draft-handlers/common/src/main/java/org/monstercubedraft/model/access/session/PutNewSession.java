package org.monstercubedraft.model.access.session;

import static java.util.Map.entry;
import static org.monstercubedraft.model.constants.SessionTableConstants.K_TTL;
import static org.monstercubedraft.model.constants.SessionTableConstants.K_WS_CONNECTION_ID;
import static org.monstercubedraft.model.constants.SessionTableConstants.PK_DRAFT_ID;
import static org.monstercubedraft.model.constants.SessionTableConstants.SK_SESSION_ID;

import java.time.ZonedDateTime;
import java.util.Map;

import org.monstercubedraft.model.access.WriteItemPattern;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.SessionId;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

public class PutNewSession implements WriteItemPattern<PutItemRequest, PutItemResponse> {

  private final String tableName;
  private final SessionId sessionId;
  private final DraftId draftId;
  private final String wsConnectionId;
  private final ZonedDateTime ttl;

  PutNewSession(
      String tableName,
      DraftId draftId,
      SessionId sessionId,
      String wsConnectionId,
      ZonedDateTime ttl) {
    this.tableName = tableName;
    this.sessionId = sessionId;
    this.draftId = draftId;
    this.wsConnectionId = wsConnectionId;
    this.ttl = ttl;
  }

  @Override
  public PutItemRequest request() {
    return PutItemRequest.builder()
        .tableName(tableName)
        .item(
            Map.ofEntries(
                entry(PK_DRAFT_ID, AttributeValue.fromS(draftId.toString())),
                entry(SK_SESSION_ID, AttributeValue.fromS(sessionId.toString())),
                entry(K_WS_CONNECTION_ID, AttributeValue.fromS(wsConnectionId)),
                entry(K_TTL, AttributeValue.fromN(String.valueOf(ttl.toEpochSecond())))))
        .build();
  }

  @Override
  public Map<String, String> interpretConditionFailures(Map<String, AttributeValue> oldValues) {
    throw new UnsupportedOperationException("Request is unconditional");
  }

  @Override
  public PutItemResponse writeTo(DynamoDbClient dynamoDb) {
    return dynamoDb.putItem(request());
  }
}
