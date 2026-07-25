package org.monstercubedraft.view;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.monstercubedraft.model.types.DraftCore;
import org.monstercubedraft.model.types.SessionAlias;
import org.monstercubedraft.model.types.SessionId;
import org.monstercubedraft.model.types.records.DraftLobby;
import org.monstercubedraft.view.types.ClientViewableDraft;
import org.monstercubedraft.view.types.records.ClientViewableLobby;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Responsible for converting model representations to view representations that clients can access.
 */
public class DraftConverter {

  private final ObjectMapper mapper;

  public DraftConverter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String jsonViewForClientSession(DraftCore draftModel, SessionId sessionId)
      throws JacksonException {
    ClientViewableDraft draftView = viewForClientSession(draftModel, sessionId);
    return mapper.writeValueAsString(draftView);
  }

  public ClientViewableDraft viewForClientSession(DraftCore draftModel, SessionId sessionId) {
    ClientViewableLobby lobbyView = makeLobbyView(draftModel.getLobby());
    // The 'sessionId' argument doesn't matter for the lobby/config phase; only when we get into the
    // proper draft will players have hidden gamestate info
    return new ClientViewableDraft(lobbyView);
  }

  private ClientViewableLobby makeLobbyView(DraftLobby draftLobby) {

    // indicate the leader but mask their session id behind the alias
    final Optional<SessionAlias> leaderAlias =
        draftLobby.leader().map(draftLobby.sessionsToAliases()::get);

    // mask session ids of players behind aliases
    final Set<SessionAlias> players = Set.copyOf(draftLobby.sessionsToAliases().values());

    // reverse keys <-> values; the mapping is 1-to-1 so this should be fine
    final Map<SessionAlias, Integer> playerSeats =
        draftLobby.seatsToAliases().entrySet().stream()
            .collect(Collectors.toMap(Entry::getValue, Entry::getKey));

    return new ClientViewableLobby(
        draftLobby.maxSize(),
        draftLobby.tcg(),
        draftLobby.draftName(),
        leaderAlias,
        draftLobby.description(),
        draftLobby.rulesetId(),
        players,
        draftLobby.aliasesToNames(),
        playerSeats,
        draftLobby.readyPlayers(),
        draftLobby.isDraftStarted());
  }
}
