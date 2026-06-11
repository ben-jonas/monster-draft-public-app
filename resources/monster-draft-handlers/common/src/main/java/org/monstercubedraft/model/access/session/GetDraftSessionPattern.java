package org.monstercubedraft.model.access.session;

import static java.util.Map.entry;
import static org.monstercubedraft.model.constants.SessionTableConstants.PK_DRAFT_ID;
import static org.monstercubedraft.model.constants.SessionTableConstants.SK_SESSION_ID;

import java.util.Map;

import org.monstercubedraft.model.access.GetItemPattern;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.SessionId;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

public class GetDraftSessionPattern implements GetItemPattern {

  private final String tableName;
  private final DraftId draftId;
  private final SessionId sessionId;

  GetDraftSessionPattern(String tableName, DraftId draftId, SessionId sessionId) {
    this.tableName = tableName;
    this.draftId = draftId;
    this.sessionId = sessionId;
  }

  @Override
  public GetItemRequest request() {
    return GetItemRequest.builder()
        .tableName(tableName)
        .key(
            Map.ofEntries(
                entry(PK_DRAFT_ID, AttributeValue.fromS(draftId.toString())),
                entry(SK_SESSION_ID, AttributeValue.fromS(sessionId.toString()))))
        .build();
  }

  @Override
  public GetItemResponse getFrom(DynamoDbClient dynamoDb) {
    return dynamoDb.getItem(this.request());
  }
}
