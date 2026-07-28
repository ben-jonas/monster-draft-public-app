package org.monstercubedraft.model.types.records;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.monstercubedraft.model.types.SessionAlias;
import org.monstercubedraft.model.types.SessionId;
import org.monstercubedraft.model.types.enums.Tcg;

public record DraftLobby(
    int maxSize, /* //NDX.maxSz */
    Tcg tcg, /*//NDX.tcg */
    Optional<String> draftName, /* //NDX.gamName */
    Optional<SessionId> leader, /* //NDX.leadr */
    Optional<String> description, /* //NDX.desc */
    Optional<String> rulesetId, /*//da0.rulesetId */
    Map<SessionId, SessionAlias> sessionsToAliases, /* //NDX.sesns */
    Map<SessionAlias, String> aliasesToNames, /* //NDX.playrNames */
    Map<Integer, SessionAlias> seatsToAliases, /*//NDX.seats */
    /* Empty and nonexistent Sets in DynamoDB are the same thing! */
    Set<SessionAlias> readyPlayers, /* //NDX.ready */
    /* //NDX.aliases and //NDX.seated only exist in the DB to enable
     * efficient conditional failures when updating; they're a reverse
     * lookup on an associated Map. (seats <-> seated; sesns <-> aliases)
     *  Hence, we don't need to represent those Sets in the Java class.*/
    boolean isDraftStarted /* //NDX.init (and //da0.init) */) {}
