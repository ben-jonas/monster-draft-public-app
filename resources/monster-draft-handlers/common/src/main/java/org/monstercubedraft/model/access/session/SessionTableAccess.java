package org.monstercubedraft.model.access.session;

import static java.util.Objects.requireNonNull;

import java.time.ZonedDateTime;

import org.monstercubedraft.model.access.GetItemPattern;
import org.monstercubedraft.model.access.ReadItemsPattern;
import org.monstercubedraft.model.access.WriteItemPattern;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.SessionId;

import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

public class SessionTableAccess {

  private final String tableName;

  public SessionTableAccess(String tableName) {
    this.tableName = requireNonNull(tableName);
  }

  public static class AccessOnPartition {
    private final String tableName;
    private final DraftId draftId;

    private AccessOnPartition(String tableName, DraftId draftId) {
      this.tableName = tableName;
      this.draftId = requireNonNull(draftId);
    }

    public WriteItemPattern<PutItemRequest, PutItemResponse> putSession(
        SessionId sessionId, String wsConnectionId, ZonedDateTime ttl) {
      return new PutNewSession(
          this.tableName,
          this.draftId,
          requireNonNull(sessionId),
          requireNonNull(wsConnectionId),
          requireNonNull(ttl));
    }

    public ReadItemsPattern<QueryRequest, QueryResponse> queryAll() {
      return new QueryAllSessionsForDraftId(this.tableName, this.draftId);
    }

    public GetItemPattern getSession(SessionId sessionId) {
      return new GetDraftSessionPattern(this.tableName, this.draftId, requireNonNull(sessionId));
    }
  }

  public AccessOnPartition onPartition(DraftId draftId) {
    return new AccessOnPartition(this.tableName, draftId);
  }
}
