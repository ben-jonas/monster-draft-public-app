package org.monstercubedraft.model.types;

import static org.monstercubedraft.model.constants.DraftTableConstants.K_DESCRIPTION;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_DRAFTNAME;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_IS_INITIALIZED;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_LEADER;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_MAXSIZE;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_PLAYERNAMES_MAP;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_READY_SET;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_RULESET_ID;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEATS_TO_ALIASES_MAP;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SESSION_MAP;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_TCG;
import static org.monstercubedraft.model.constants.DraftTableConstants.SK_PAGE;
import static org.monstercubedraft.model.types.enums.DraftPageName.DATA0;
import static org.monstercubedraft.model.types.enums.DraftPageName.INDEX;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.monstercubedraft.model.types.enums.DraftPageName;
import org.monstercubedraft.model.types.enums.Tcg;
import org.monstercubedraft.model.types.records.DraftLobby;
import org.monstercubedraft.model.types.records.Ruleset;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

public class DraftCore {
  private Map<DraftPageName, Map<String, AttributeValue>> rawPages;
  private DraftLobby lobby;
  private Ruleset ruleset;

  public DraftCore(QueryResponse data) {
    Map<DraftPageName, Map<String, AttributeValue>> rawPages = new TreeMap<>();
    for (Map<String, AttributeValue> item : data.items()) {
      DraftPageName pageName = DraftPageName.fromAttributeValue(item.get(SK_PAGE));
      rawPages.put(pageName, item);
    }
    this.rawPages = Map.copyOf(rawPages);
    populateLobby();
    // Lobby fields can be interpreted immediately but the draft-in-progress doesn't make sense
    // without a Ruleset applied, so we don't do that here
  }

  private void populateLobby() {
    final int maxSize = Integer.valueOf(rawPages.get(INDEX).get(K_MAXSIZE).n());
    final Tcg tcg = Tcg.valueOf(rawPages.get(INDEX).get(K_TCG).s());

    final Optional<String> draftName =
        Optional.ofNullable(rawPages.get(INDEX).get(K_DRAFTNAME)).map(AttributeValue::s);
    final Optional<SessionId> leader =
        Optional.ofNullable(rawPages.get(INDEX).get(K_LEADER)).map(l -> new SessionId(l.s()));
    final Optional<String> description =
        Optional.ofNullable(rawPages.get(INDEX).get(K_DESCRIPTION)).map(AttributeValue::s);

    // Note use of flatMap here; the //NDX page should always exist but we have no such guarantee
    // for any subsequent page (in this case, //da0).
    final Optional<String> rulesetId =
        Optional.ofNullable(rawPages.get(DATA0))
            .flatMap(page -> Optional.ofNullable(page.get(K_RULESET_ID)))
            .map(AttributeValue::s);

    final Map<SessionId, SessionAlias> sessionsToAliases =
        rawPages.get(INDEX).get(K_SESSION_MAP).m().entrySet().stream()
            .collect(
                Collectors.toMap(
                    e -> new SessionId(e.getKey()), e -> new SessionAlias(e.getValue().s())));
    final Map<SessionAlias, String> aliasesToNames =
        rawPages.get(INDEX).get(K_PLAYERNAMES_MAP).m().entrySet().stream()
            .collect(Collectors.toMap(e -> new SessionAlias(e.getKey()), e -> e.getValue().s()));
    final Map<Integer, SessionAlias> seatsToAliases =
        rawPages.get(INDEX).get(K_SEATS_TO_ALIASES_MAP).m().entrySet().stream()
            .collect(
                Collectors.toMap(
                    e -> Integer.valueOf(e.getKey()), e -> new SessionAlias(e.getValue().s())));

    final Set<SessionAlias> readyPlayers =
        Optional.ofNullable(rawPages.get(INDEX).get(K_READY_SET))
            .map(AttributeValue::ss)
            .orElse(List.of())
            .stream()
            .map(SessionAlias::new)
            .collect(Collectors.toSet());

    final boolean isDraftStarted = rawPages.get(INDEX).get(K_IS_INITIALIZED).bool();

    this.lobby =
        new DraftLobby(
            maxSize,
            tcg,
            draftName,
            leader,
            description,
            rulesetId,
            sessionsToAliases,
            aliasesToNames,
            seatsToAliases,
            readyPlayers,
            isDraftStarted);
  }

  public void applyRuleset(Ruleset ruleset) {
    this.ruleset = ruleset;
    // TODO init non-lobby fields when ruleset is more than a stub
  }

  public boolean isRulesetApplied() {
    return ruleset != null;
  }

  public DraftLobby getLobby() {
    return lobby;
  }
}
