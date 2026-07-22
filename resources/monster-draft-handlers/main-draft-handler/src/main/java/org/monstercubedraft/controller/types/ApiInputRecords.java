package org.monstercubedraft.controller.types;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

public class ApiInputRecords {

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.PROPERTY,
      property = "command")
  public static sealed interface DraftItem permits SystemQueuedRequest, UserQueuedRequest {}

  @JsonSubTypes({
    @JsonSubTypes.Type(value = AcknowledgeRequest.class, name = "acknowledge"),
    @JsonSubTypes.Type(value = WhoAmIRequest.class, name = "who_am_i"),
    @JsonSubTypes.Type(value = WhoAmIRequest.class, name = "set_self_as_leader"),
    @JsonSubTypes.Type(value = WhoAmIRequest.class, name = "sit_down"),
    @JsonSubTypes.Type(value = WhoAmIRequest.class, name = "ready"),
    @JsonSubTypes.Type(value = WhoAmIRequest.class, name = "begin_game")
  })
  public static sealed interface SystemQueuedRequest extends DraftItem permits BeginGameRequest {
    String draftId();
  }

  public static record BeginGameRequest(String draftId) implements SystemQueuedRequest {}

  public static sealed interface UserQueuedRequest extends DraftItem
      permits AcknowledgeRequest,
          WhoAmIRequest,
          GetVisibleStateRequest,
          SetSelfAsLeaderRequest,
          SitDownRequest,
          ReadyRequest {
    String connectionId();
  }

  public static record AcknowledgeRequest(String connectionId, String tempAlias)
      implements UserQueuedRequest {}

  public static record WhoAmIRequest(String connectionId, String tempAlias)
      implements UserQueuedRequest {}

  public static record GetVisibleStateRequest(String connectionId) implements UserQueuedRequest {}

  public static record SetSelfAsLeaderRequest(String connectionId) implements UserQueuedRequest {}

  public static record SitDownRequest(String connectionId) implements UserQueuedRequest {}

  public static record ReadyRequest(String connectionId) implements UserQueuedRequest {}

  private ApiInputRecords() {}
}
