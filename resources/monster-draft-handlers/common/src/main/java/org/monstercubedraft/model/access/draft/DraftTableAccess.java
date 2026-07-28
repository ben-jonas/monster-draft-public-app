package org.monstercubedraft.model.access.draft;

import static java.util.Objects.requireNonNull;

import java.time.ZonedDateTime;

import org.monstercubedraft.model.access.ReadItemsPattern;
import org.monstercubedraft.model.access.TransactionalWritePattern;
import org.monstercubedraft.model.access.WriteItemPattern;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.SessionAlias;
import org.monstercubedraft.model.types.SessionId;
import org.monstercubedraft.model.types.enums.Tcg;

import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

public class DraftTableAccess {

  private final String tableName;

  public DraftTableAccess(String tableName) {
    this.tableName = requireNonNull(tableName);
  }

  public static class AccessOnPartition {
    private final String tableName;
    private final DraftId draftId;

    private AccessOnPartition(String tableName, DraftId draftId) {
      this.tableName = tableName;
      this.draftId = requireNonNull(draftId);
    }

    public ReadItemsPattern<QueryRequest, QueryResponse> queryCorePages() {
      return new QueryCoreDraftPages(this.tableName, this.draftId);
    }

    public WriteItemPattern<PutItemRequest, PutItemResponse> putIndexPage(
        Tcg tcg, int numPlayers, ZonedDateTime ttl) {
      return new PutDraftIndexPage(
          this.tableName,
          this.draftId,
          requireNonNull(tcg),
          requireNonNull(numPlayers),
          requireNonNull(ttl));
    }

    public WriteItemPattern<PutItemRequest, PutItemResponse> putData0Page(
        ZonedDateTime timeToLive) {
      return new PutDraftData0Page(this.tableName, this.draftId, requireNonNull(timeToLive));
    }

    public WriteItemPattern<UpdateItemRequest, UpdateItemResponse> addSession(
        SessionId sessionId, SessionAlias sessionAlias) {
      return new UpdateDraftAddSession(
          this.tableName, this.draftId, requireNonNull(sessionId), requireNonNull(sessionAlias));
    }

    public WriteItemPattern<UpdateItemRequest, UpdateItemResponse> setLeader(SessionId sessionId) {
      return new UpdateDraftSetLeader(this.tableName, this.draftId, requireNonNull(sessionId));
    }

    public WriteItemPattern<UpdateItemRequest, UpdateItemResponse> setNameForPlayer(
        SessionAlias sessionAlias, String sessionName) {
      return new UpdateDraftSetPlayerName(
          this.tableName, this.draftId, requireNonNull(sessionAlias), requireNonNull(sessionName));
    }

    public WriteItemPattern<UpdateItemRequest, UpdateItemResponse> sitDownPlayer(
        SessionAlias sessionAlias, int seat) {
      return new UpdateDraftSitDownPlayer(
          this.tableName, this.draftId, requireNonNull(sessionAlias), seat);
    }

    public WriteItemPattern<UpdateItemRequest, UpdateItemResponse> standUpPlayer(
        SessionAlias sessionAlias, int seat) {
      return new UpdateDraftStandUpPlayer(
          this.tableName, this.draftId, requireNonNull(sessionAlias), seat);
    }

    public WriteItemPattern<UpdateItemRequest, UpdateItemResponse> readyPlayer(
        SessionAlias sessionAlias) {
      return new UpdateDraftReadyPlayer(this.tableName, this.draftId, requireNonNull(sessionAlias));
    }

    public WriteItemPattern<UpdateItemRequest, UpdateItemResponse> unreadyPlayer(
        SessionAlias sessionAlias) {
      return new UpdateDraftUnreadyPlayer(
          this.tableName, this.draftId, requireNonNull(sessionAlias));
    }

    public WriteItemPattern<UpdateItemRequest, UpdateItemResponse> setRulesetId(String rulesetId) {
      return new UpdateDraftSetRulesetId(this.tableName, this.draftId, requireNonNull(rulesetId));
    }

    public WriteItemPattern<UpdateItemRequest, UpdateItemResponse> setTimeLimitScheme(
        String timeLimitScheme) {
      return new UpdateDraftSetTimeLimitScheme(
          this.tableName, this.draftId, requireNonNull(timeLimitScheme));
    }

    public TransactionalWritePattern initDraft(ZonedDateTime ttl) {
      return new InitDraft(this.tableName, this.draftId, requireNonNull(ttl));
    }
  }

  public AccessOnPartition onPartition(DraftId draftId) {
    return new AccessOnPartition(this.tableName, draftId);
  }
}
