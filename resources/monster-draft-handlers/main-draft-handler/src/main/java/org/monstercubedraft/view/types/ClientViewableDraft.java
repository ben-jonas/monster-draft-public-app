package org.monstercubedraft.view.types;

import org.monstercubedraft.view.types.records.ClientViewableLobby;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A view of the ongoing draft from a particular client's perspecive. Includes most (if not all)
 * config or "lobby" information, as well as remaining upgrades in the Shop, but hides other
 * players' collections and intended moves. Also withholds players' session IDs, referring to them
 * by their Aliases instead.
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class ClientViewableDraft {

  private final ClientViewableLobby lobby;

  public ClientViewableDraft(ClientViewableLobby lobby) {
    this.lobby = lobby;
  }

  public ClientViewableLobby getLobby() {
    return lobby;
  }
}
