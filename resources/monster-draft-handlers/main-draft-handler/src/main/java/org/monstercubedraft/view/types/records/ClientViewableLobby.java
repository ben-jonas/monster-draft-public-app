package org.monstercubedraft.view.types.records;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.monstercubedraft.model.types.SessionAlias;
import org.monstercubedraft.model.types.enums.Tcg;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Similar to {@link org.monstercubedraft.model.types.records.DraftLobby} but withholds {@link
 * org.monstercubedraft.model.types.SessionId} info, relying only on {@link SessionAlias}.
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record ClientViewableLobby(
    int maxSize,
    Tcg tcg,
    Optional<String> draftName,
    Optional<SessionAlias> leader,
    Optional<String> description,
    Optional<String> rulesetId,
    Set<SessionAlias> players,
    Map<SessionAlias, String> playerNames,
    Map<SessionAlias, Integer> playerSeats,
    Set<SessionAlias> readyPlayers,
    boolean isDraftStarted) {}
