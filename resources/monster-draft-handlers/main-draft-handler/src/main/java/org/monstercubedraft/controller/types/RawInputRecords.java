package org.monstercubedraft.controller.types;

import org.monstercubedraft.model.types.DraftId;

public class RawInputRecords {

  public sealed interface RawInputMessage permits RawWebsocketClientMessage, RawServerSentMessage {
    public String body();
  }

  public record RawWebsocketClientMessage(String wsConnectionId, String body)
      implements RawInputMessage {}

  public record RawServerSentMessage(DraftId draftId, String body) implements RawInputMessage {}

  private RawInputRecords() {}
}
