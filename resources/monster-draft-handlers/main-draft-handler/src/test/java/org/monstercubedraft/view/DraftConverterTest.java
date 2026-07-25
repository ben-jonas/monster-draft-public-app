package org.monstercubedraft.view;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.monstercubedraft.model.types.DraftCore;
import org.monstercubedraft.model.types.SessionAlias;
import org.monstercubedraft.model.types.SessionId;
import org.monstercubedraft.model.types.enums.Tcg;
import org.monstercubedraft.model.types.records.DraftLobby;
import org.monstercubedraft.view.json.MonsterDraftObjectMapper;
import org.monstercubedraft.view.types.ClientViewableDraft;
import org.monstercubedraft.view.types.records.ClientViewableLobby;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DraftConverterTest {

  static ObjectMapper objectMapper;
  DraftConverter draftConverter;

  // fields necessary for a 'full draft' lobby
  static SessionId LEADER_ID = new SessionId("leader0001");
  static SessionAlias LEADER_ALIAS = new SessionAlias("ldr");
  static SessionId PLAYER2_ID = new SessionId("player0002");
  static SessionAlias PLAYER2_ALIAS = new SessionAlias("p02");
  static String PLAYER2_NAME = "Player2Nickname";
  static Map<SessionId, SessionAlias> SESSIONS_TO_ALIASES =
      Map.ofEntries(
          entry(LEADER_ID, LEADER_ALIAS),
          entry(PLAYER2_ID, PLAYER2_ALIAS),
          entry(new SessionId("player0003"), new SessionAlias("p03")),
          entry(new SessionId("player0004"), new SessionAlias("p04")),
          entry(new SessionId("player0005"), new SessionAlias("p05")),
          entry(new SessionId("player0006"), new SessionAlias("p06")),
          entry(new SessionId("player0007"), new SessionAlias("p07")),
          entry(new SessionId("player0008"), new SessionAlias("p08")));
  static Map<SessionAlias, String> ALIASES_TO_NAMES =
      Map.ofEntries(entry(PLAYER2_ALIAS, PLAYER2_NAME));
  static Map<Integer, SessionAlias> SEATS_TO_ALIASES = Map.ofEntries(entry(0, LEADER_ALIAS));
  static Set<SessionAlias> READY_PLAYERS = Set.of(LEADER_ALIAS);

  @BeforeAll
  static void setUpSuite() {
    objectMapper = MonsterDraftObjectMapper.create();
  }

  @BeforeEach
  void setUp() {
    draftConverter = new DraftConverter(objectMapper);
  }

  @Test
  void viewForClientSession_populatesFullLobbyFields() {
    DraftCore draft = constructDraftWithFullLobby();
    ClientViewableDraft draftView = draftConverter.viewForClientSession(draft, LEADER_ID);
    ClientViewableLobby lobbyView = draftView.getLobby();
    assertThat(lobbyView.draftName().get()).isEqualTo("someDraft");
    assertThat(lobbyView.leader().get()).isEqualTo(LEADER_ALIAS);
    assertThat(lobbyView.description().get()).isEqualTo("a description");
    assertThat(lobbyView.rulesetId().get()).isEqualTo("ruleset012345");
    assertThat(lobbyView.players().size()).isEqualTo(SESSIONS_TO_ALIASES.size());
    assertThat(lobbyView.players()).containsAll(SESSIONS_TO_ALIASES.values());
  }

  @Test
  void jsonViewForClientSession_stringContainsFullLobbyFields() throws JacksonException {
    DraftCore draft = constructDraftWithFullLobby();
    String draftViewStr = draftConverter.jsonViewForClientSession(draft, LEADER_ID);
    System.out.println(draftViewStr);
    assertThat(draftViewStr).contains("draftName");
  }

  DraftCore constructDraftWithFullLobby() {
    DraftLobby lobby =
        new DraftLobby(
            8,
            Tcg.PKMN,
            Optional.of("someDraft"),
            Optional.of(LEADER_ID),
            Optional.of("a description"),
            Optional.of("ruleset012345"),
            SESSIONS_TO_ALIASES,
            ALIASES_TO_NAMES,
            SEATS_TO_ALIASES,
            READY_PLAYERS,
            false);
    DraftCore draft = mock(DraftCore.class);
    when(draft.getLobby()).thenReturn(lobby);
    return draft;
  }
}
