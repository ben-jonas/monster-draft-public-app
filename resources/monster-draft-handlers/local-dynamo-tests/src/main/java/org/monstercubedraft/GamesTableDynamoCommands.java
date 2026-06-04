package org.monstercubedraft;

import static java.util.Map.entry;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromB;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromBool;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromM;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromN;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromSs;
import static software.amazon.awssdk.services.dynamodb.model.ReturnConsumedCapacity.TOTAL;
import static software.amazon.awssdk.services.dynamodb.model.ReturnValue.ALL_NEW;
import static software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure.ALL_OLD;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

public class GamesTableDynamoCommands {

  public static final int ACTIVE_SCHEMA_VERSION = 1;
  public static final String TBL_DRAFT = "TestGames";

  public static final String PK_GAME_ID = "gId";
  public static final String SK_PAGE = "pag";
  public static final String K_TTL = "ttl";
  public static final String K_VERSION = "ver";

  public static final String K_TCG = "tcg";
  public static final String K_MAXSIZE = "maxSz";
  public static final String K_ALIASES_SET = "aliases";
  public static final String K_SESSION_MAP = "sesns";
  public static final String K_PLAYERNAMES_MAP = "playrNames";
  public static final String K_EXTENSIONS_SET = "xtensns";
  public static final String K_LEADER = "leadr";
  public static final String K_SEATED_SET = "seated";
  public static final String K_SEATS_TO_ALIASES_MAP = "seats";
  public static final String K_READY_SET = "ready";
  public static final String K_IS_INITIALIZED = "init";

  public static final String K_RULESET_ID = "rulesetId";
  public static final String K_TIME_LIMIT_SCHEME = "timeLimitScheme";
  public static final String K_ROUND_AND_TURN = "roundTurn";
  public static final String K_SEAT0 = "s0";
  public static final String K_SEAT1 = "s1";
  public static final String K_SEAT2 = "s2";
  public static final String K_SEAT3 = "s3";
  public static final String K_SEAT4 = "s4";
  public static final String K_SEAT5 = "s5";
  public static final String K_SEAT6 = "s6";
  public static final String K_SEAT7 = "s7";
  public static final String CURRENT_PACK = "pac";
  public static final String PENDING_DRAFT_ACTION = "mov";
  public static final String CURRENT_ROUND_HELD_CARDS = "own";

  public static final String K_UNOPENED_PACKS = "pacsBlok";
  public static final String K_COLLECTED_CARDS = "colsBlock";
  public static final String K_UPGRADE_SHOP_1 = "shop1";
  public static final String K_UPGRADE_SHOP_2 = "shop2";

  public static final int TTL_TIMEDELTA_HRS = 24;

  /**
   * Create a 'describe table' request for the configured 'games' table.
   *
   * @return The 'describe table' request that can be used by the Dynamo client.
   */
  public static DescribeTableRequest describeGamesTableRequest() {
    return DescribeTableRequest.builder().tableName(TBL_DRAFT).build();
  }

  /**
   * Create a PutItemRequest representing a new lobby, with the common fields, plus the immutable
   * fields 'tcg' and 'maxSz' set, and maps 'aliases', 'playrNames', and 'seats' initialized to
   * empty Maps
   *
   * @return
   */
  public static PutItemRequest putDraftIndexPage(String draftId, Tcg tcg, int numPlayers) {
    Stream.of(draftId, tcg).forEach(Objects::requireNonNull);
    Map<String, AttributeValue> ndxPgItem =
        makeItemWithCommonFields(
            draftId, GamesPage.INDEX, ZonedDateTime.now().plusHours(TTL_TIMEDELTA_HRS));
    ndxPgItem.put(K_TCG, fromS(tcg.name()));
    ndxPgItem.put(K_MAXSIZE, fromN(String.valueOf(numPlayers)));
    ndxPgItem.put(K_SESSION_MAP, fromM(Map.of()));
    ndxPgItem.put(K_PLAYERNAMES_MAP, fromM(Map.of()));
    ndxPgItem.put(K_SEATS_TO_ALIASES_MAP, fromM(Map.of()));
    ndxPgItem.put(K_IS_INITIALIZED, fromBool(false));
    return PutItemRequest.builder().tableName(TBL_DRAFT).item(ndxPgItem).build();
  }

  /**
   * Create a PutItemRequest representing a new 'da0' (quick access) page with no fields initialized
   * except the common fields, the default time limit scheme of (untimed), and the 'seat' fields
   * initialized Maps, with each of their keys pointing to null values
   *
   * @return
   */
  public static PutItemRequest putDraftData0Page(String draftId, ZonedDateTime timeToLive) {
    Stream.of(draftId, timeToLive).forEach(Objects::requireNonNull);
    Map<String, AttributeValue> da0PgItem =
        makeItemWithCommonFields(draftId, GamesPage.DATA0, timeToLive);
    da0PgItem.put(K_TIME_LIMIT_SCHEME, fromS("NONE"));
    da0PgItem.put(K_IS_INITIALIZED, fromBool(false));

    Map<String, AttributeValue> commonSeatFields =
        Map.ofEntries(
            entry(CURRENT_PACK, AttributeValue.fromNul(true)),
            entry(PENDING_DRAFT_ACTION, AttributeValue.fromNul(true)),
            entry(CURRENT_ROUND_HELD_CARDS, AttributeValue.fromNul(true)));
    for (String k :
        Set.of(K_SEAT0, K_SEAT1, K_SEAT2, K_SEAT3, K_SEAT4, K_SEAT5, K_SEAT6, K_SEAT7)) {
      da0PgItem.put(k, fromM(commonSeatFields));
    }

    return PutItemRequest.builder().tableName(TBL_DRAFT).item(da0PgItem).build();
  }

  /**
   * Get the four 'core' pages for the draft, with eventual consistency.
   *
   * @return
   */
  public static QueryRequest queryCoreDraftPages(String draftId) {
    Objects.requireNonNull(draftId);
    return QueryRequest.builder()
        .tableName(TBL_DRAFT)
        .keyConditionExpression(
            String.format("%s = :draftId and begins_with(%s, :namespace)", PK_GAME_ID, SK_PAGE))
        .expressionAttributeValues(
            Map.of(":namespace", fromS(GamesPage.INDEX.getNamespace()), ":draftId", fromS(draftId)))
        .build();
  }

  /**
   * Add player session to draft if draft is not full. Set 'alias' as the given 3-char string.
   * Conditional on the draft not having started yet and the 'alias' not having been used yet.
   *
   * @return
   */
  public static UpdateItemRequest updateDraftAddSession(
      String draftId, String sessionId, String sessionAlias) {
    Stream.of(draftId, sessionId, sessionAlias).forEach(Objects::requireNonNull);
    String updateExpression =
        "SET #sessionMap.#sessionId = :sessionAlias ADD #aliasesSet :sessionAliasSs";

    String conditionExpression =
        "attribute_exists(#pk) AND "
            + "#isInitialized = :false AND "
            + "attribute_not_exists(#sessionMap.#sessionId) AND "
            + "size(#sessionMap) <= #maxDraftSize AND "
            + "NOT contains(#aliasesSet, :sessionAliasSs)";

    return UpdateItemRequest.builder()
        .tableName(TBL_DRAFT)
        .key(
            Map.ofEntries(
                entry(PK_GAME_ID, fromS(draftId)),
                entry(SK_PAGE, GamesPage.INDEX.asAttributeValue())))
        .updateExpression(updateExpression)
        .conditionExpression(conditionExpression)
        .expressionAttributeNames(
            Map.ofEntries(
                entry("#pk", PK_GAME_ID),
                entry("#maxDraftSize", K_MAXSIZE),
                entry("#sessionMap", K_SESSION_MAP),
                entry("#sessionId", sessionId),
                entry("#aliasesSet", K_ALIASES_SET),
                entry("#isInitialized", K_IS_INITIALIZED)))
        .expressionAttributeValues(
            Map.ofEntries(
                entry(":false", fromBool(false)),
                entry(":sessionAlias", fromS(sessionAlias)),
                entry(":sessionAliasSs", fromSs(List.of(sessionAlias)))))
        .returnValuesOnConditionCheckFailure(ALL_OLD)
        .build();
  }

  /**
   * Set session as 'leader' if session is present. (Only the leader may change game settings. This
   * is enforced by the app logic, not at the dynamo API level.)
   *
   * @return
   */
  public static UpdateItemRequest updateDraftSetSessionAsLeader(String draftId, String sessionId) {
    Stream.of(draftId, sessionId).forEach(Objects::requireNonNull);
    String updateExpression = "SET #leader = :sessionId";
    String conditionExpression =
        "attribute_exists(#pk) AND attribute_exists(#sessionMap.#sessionId)";

    return UpdateItemRequest.builder()
        .tableName(TBL_DRAFT)
        .key(
            Map.ofEntries(
                entry(PK_GAME_ID, fromS(draftId)),
                entry(SK_PAGE, GamesPage.INDEX.asAttributeValue())))
        .updateExpression(updateExpression)
        .conditionExpression(conditionExpression)
        .expressionAttributeNames(
            Map.ofEntries(
                entry("#pk", PK_GAME_ID),
                entry("#sessionMap", K_SESSION_MAP),
                entry("#sessionId", sessionId),
                entry("#leader", K_LEADER)))
        .expressionAttributeValues(Map.ofEntries(entry(":sessionId", fromS(sessionId))))
        .returnValuesOnConditionCheckFailure(ALL_OLD)
        .build();
  }

  /**
   * Updates the map connecting aliases to player names, to include the given mapping of alias to
   * name
   *
   * @return
   */
  public static UpdateItemRequest updateDraftSetPlayerName(
      String draftId, String sessionAlias, String playerName) {
    Stream.of(draftId, sessionAlias, playerName).forEach(Objects::requireNonNull);
    String updateExpression = "SET #playerNamesMap.#alias = :playerName";
    String conditionExpression =
        "attribute_exists(#pk) AND attribute_exists(#aliasesSet) AND contains(#aliasesSet, :alias)";
    return UpdateItemRequest.builder()
        .tableName(TBL_DRAFT)
        .key(
            Map.ofEntries(
                entry(PK_GAME_ID, fromS(draftId)),
                entry(SK_PAGE, GamesPage.INDEX.asAttributeValue())))
        .updateExpression(updateExpression)
        .conditionExpression(conditionExpression)
        .expressionAttributeNames(
            Map.ofEntries(
                entry("#pk", PK_GAME_ID),
                entry("#playerNamesMap", K_PLAYERNAMES_MAP),
                entry("#alias", sessionAlias),
                entry("#aliasesSet", K_ALIASES_SET)))
        .expressionAttributeValues(
            Map.ofEntries(
                entry(":playerName", fromS(playerName)), entry(":alias", fromS(sessionAlias))))
        .returnValuesOnConditionCheckFailure(ALL_OLD)
        .build();
  }

  /**
   * Updates the 'seated' set on da0 to include the given player alias, and updates the 'seats' map
   * to point the given seat # at the given player alias. Conditional on the draft not having
   * started yet.
   *
   * @return
   */
  public static UpdateItemRequest updateDraftSitDownPlayer(
      String draftId, String sessionAlias, int seat) {
    Stream.of(draftId, sessionAlias).forEach(Objects::requireNonNull);
    String updateExpression = "SET #seatsToAliasesMap.#seat = :alias ADD #seatedSet :aliasSs";
    String conditionExpression =
        "attribute_exists(#pk) AND "
            + "#isInitialized = :false AND "
            + "contains(#aliasesSet, :alias) AND "
            + "attribute_not_exists(#seatsToAliasesMap.#seat) AND "
            + "(attribute_not_exists(#seatedSet) OR NOT contains(#seatedSet, :alias))";

    return UpdateItemRequest.builder()
        .tableName(TBL_DRAFT)
        .key(
            Map.ofEntries(
                entry(PK_GAME_ID, fromS(draftId)),
                entry(SK_PAGE, GamesPage.INDEX.asAttributeValue())))
        .updateExpression(updateExpression)
        .conditionExpression(conditionExpression)
        .expressionAttributeNames(
            Map.ofEntries(
                entry("#pk", PK_GAME_ID),
                entry("#isInitialized", K_IS_INITIALIZED),
                entry("#aliasesSet", K_ALIASES_SET),
                entry("#seatsToAliasesMap", K_SEATS_TO_ALIASES_MAP),
                entry("#seat", String.valueOf(seat)),
                entry("#seatedSet", K_SEATED_SET)))
        .expressionAttributeValues(
            Map.ofEntries(
                entry(":false", fromBool(false)),
                entry(":alias", fromS(sessionAlias)),
                entry(":aliasSs", fromSs(List.of(sessionAlias)))))
        .returnValuesOnConditionCheckFailure(ALL_OLD)
        .build();
  }

  /**
   * Updates the 'seated' set on da0 to remove the given player alias, and updates the 'seats' map
   * to remove the entry linking that player to whatever seat they were in. Also unreadies the
   * player. Conditional on the draft not having started yet.
   *
   * @return
   */
  public static UpdateItemRequest updateDraftStandUpPlayer(
      String draftId, String sessionAlias, int seat) {
    Stream.of(draftId, sessionAlias).forEach(Objects::requireNonNull);
    String updateExpression =
        "REMOVE #seatsToAliasesMap.#seat DELETE #seatedSet:aliasSs, #readySet:aliasSs";
    String conditionExpression =
        "attribute_exists(#pk) AND "
            + "#isInitialized = :false AND "
            + "contains(#aliasesSet, :alias) AND "
            + "attribute_exists(#seatsToAliasesMap.#seat) AND "
            + "contains(#seatedSet, :alias)";

    return UpdateItemRequest.builder()
        .tableName(TBL_DRAFT)
        .key(
            Map.ofEntries(
                entry(PK_GAME_ID, fromS(draftId)),
                entry(SK_PAGE, GamesPage.INDEX.asAttributeValue())))
        .updateExpression(updateExpression)
        .conditionExpression(conditionExpression)
        .expressionAttributeNames(
            Map.ofEntries(
                entry("#pk", PK_GAME_ID),
                entry("#isInitialized", K_IS_INITIALIZED),
                entry("#aliasesSet", K_ALIASES_SET),
                entry("#seatsToAliasesMap", K_SEATS_TO_ALIASES_MAP),
                entry("#seat", String.valueOf(seat)),
                entry("#seatedSet", K_SEATED_SET),
                entry("#readySet", K_READY_SET)))
        .expressionAttributeValues(
            Map.ofEntries(
                entry(":false", fromBool(false)),
                entry(":alias", fromS(sessionAlias)),
                entry(":aliasSs", fromSs(List.of(sessionAlias)))))
        .returnValuesOnConditionCheckFailure(ALL_OLD)
        .build();
  }

  /**
   * Adds a given player alias to the 'ready' set. Conditional on the draft not having started yet
   * and the player being seated.
   *
   * @return
   */
  public static UpdateItemRequest updateDraftReadyPlayer(String draftId, String sessionAlias) {
    Stream.of(draftId, sessionAlias).forEach(Objects::requireNonNull);
    String updateExpression = "ADD #readySet :aliasSs";
    String conditionExpression =
        "attribute_exists(#pk) AND "
            + "#isInitialized = :false AND "
            + "contains(#aliasesSet, :alias) AND "
            + "contains(#seatedSet, :alias)";

    return UpdateItemRequest.builder()
        .tableName(TBL_DRAFT)
        .key(
            Map.ofEntries(
                entry(PK_GAME_ID, fromS(draftId)),
                entry(SK_PAGE, GamesPage.INDEX.asAttributeValue())))
        .updateExpression(updateExpression)
        .conditionExpression(conditionExpression)
        .expressionAttributeNames(
            Map.ofEntries(
                entry("#pk", PK_GAME_ID),
                entry("#isInitialized", K_IS_INITIALIZED),
                entry("#aliasesSet", K_ALIASES_SET),
                entry("#seatedSet", K_SEATED_SET),
                entry("#readySet", K_READY_SET)))
        .expressionAttributeValues(
            Map.ofEntries(
                entry(":false", fromBool(false)),
                entry(":alias", fromS(sessionAlias)),
                entry(":aliasSs", fromSs(List.of(sessionAlias)))))
        .returnValues(ALL_NEW)
        .returnValuesOnConditionCheckFailure(ALL_OLD)
        .build();
  }

  /**
   * Removes a given player alias to the 'ready' set. Conditional on the draft not having started
   * yet.
   *
   * @return
   */
  public static UpdateItemRequest updateDraftUnreadyPlayer(String draftId, String sessionAlias) {
    Stream.of(draftId, sessionAlias).forEach(Objects::requireNonNull);
    String updateExpression = "DELETE #readySet :aliasSs";
    String conditionExpression = "attribute_exists(#pk) AND #isInitialized = :false";

    return UpdateItemRequest.builder()
        .tableName(TBL_DRAFT)
        .key(
            Map.ofEntries(
                entry(PK_GAME_ID, fromS(draftId)),
                entry(SK_PAGE, GamesPage.INDEX.asAttributeValue())))
        .updateExpression(updateExpression)
        .conditionExpression(conditionExpression)
        .expressionAttributeNames(
            Map.ofEntries(
                entry("#pk", PK_GAME_ID),
                entry("#isInitialized", K_IS_INITIALIZED),
                entry("#readySet", K_READY_SET)))
        .expressionAttributeValues(
            Map.ofEntries(
                entry(":false", fromBool(false)), entry(":aliasSs", fromSs(List.of(sessionAlias)))))
        .returnValues(ALL_NEW)
        .returnConsumedCapacity(TOTAL)
        .returnValuesOnConditionCheckFailure(ALL_OLD)
        .build();
  }

  /**
   * Add ruleset to the draft. Conditional on the draft not having started yet and leader being set.
   *
   * @return
   */
  public static UpdateItemRequest updateDraftRulesetId(String draftId, String rulesetId) {
    Stream.of(draftId, rulesetId).forEach(Objects::requireNonNull);
    String updateExpression = "SET #rulesetId = :rulesetId";
    String conditionExpression = "attribute_exists(#pk) AND #isInitialized = :false";
    return UpdateItemRequest.builder()
        .tableName(TBL_DRAFT)
        .key(
            Map.ofEntries(
                entry(PK_GAME_ID, fromS(draftId)),
                entry(SK_PAGE, GamesPage.DATA0.asAttributeValue())))
        .updateExpression(updateExpression)
        .conditionExpression(conditionExpression)
        .expressionAttributeNames(
            Map.ofEntries(
                entry("#rulesetId", K_RULESET_ID),
                entry("#pk", PK_GAME_ID),
                entry("#isInitialized", K_IS_INITIALIZED)))
        .expressionAttributeValues(
            Map.ofEntries(entry(":rulesetId", fromS(rulesetId)), entry(":false", fromBool(false))))
        .returnValuesOnConditionCheckFailure(ALL_OLD)
        .build();
  }

  /**
   * Sets the structure of how turns will be timed. Conditional on the draft not having started yet.
   *
   * @return
   */
  public static UpdateItemRequest updateDraftSetTimeLimitScheme(
      String draftId, String timeLimitScheme) {
    Stream.of(draftId, timeLimitScheme).forEach(Objects::requireNonNull);
    String updateExpression = "SET #timeLimitScheme = :timeLimitScheme";
    String conditionExpression = "attribute_exists(#pk) AND #isInitialized = :false";
    return UpdateItemRequest.builder()
        .tableName(TBL_DRAFT)
        .key(
            Map.ofEntries(
                entry(PK_GAME_ID, fromS(draftId)),
                entry(SK_PAGE, GamesPage.DATA0.asAttributeValue())))
        .updateExpression(updateExpression)
        .conditionExpression(conditionExpression)
        .expressionAttributeNames(
            Map.ofEntries(
                entry("#pk", PK_GAME_ID),
                entry("#isInitialized", K_IS_INITIALIZED),
                entry("#timeLimitScheme", K_TIME_LIMIT_SCHEME)))
        .expressionAttributeValues(
            Map.ofEntries(
                entry(":false", fromBool(false)),
                entry(":timeLimitScheme", fromS(timeLimitScheme))))
        .returnValuesOnConditionCheckFailure(ALL_OLD)
        .build();
  }

  /**
   * Adds the initialized da1 and da2 pages; sets the 'init' flag on NDX and da0 to true.
   * Conditional on: - NDX and da0 pages must not be initialized - NDX: All players being seated and
   * ready. Seated & ready set sizes must match the maxSz value. - da0: A rulesetId must be present.
   *
   * @return
   */
  public static TransactWriteItemsRequest initDraftCorePages(String draftId, ZonedDateTime ttl) {
    Stream.of(draftId, ttl).forEach(Objects::requireNonNull);
    TransactWriteItem indexWrite =
        TransactWriteItem.builder()
            .update(
                u ->
                    u.tableName(TBL_DRAFT)
                        .key(
                            Map.ofEntries(
                                entry(PK_GAME_ID, fromS(draftId)),
                                entry(SK_PAGE, GamesPage.INDEX.asAttributeValue())))
                        .updateExpression("SET #isInitialized = :true, #ttl = :ttl")
                        .conditionExpression(
                            "attribute_exists(#pk) AND "
                                + "#isInitialized = :false AND "
                                + "size(#readySet) = #maxDraftSize")
                        .expressionAttributeNames(
                            Map.ofEntries(
                                entry("#pk", PK_GAME_ID),
                                entry("#isInitialized", K_IS_INITIALIZED),
                                entry("#ttl", K_TTL),
                                entry("#readySet", K_READY_SET),
                                entry("#maxDraftSize", K_MAXSIZE)))
                        .expressionAttributeValues(
                            Map.ofEntries(
                                entry(":ttl", fromN(String.valueOf(ttl.toEpochSecond()))),
                                entry(":false", fromBool(false)),
                                entry(":true", fromBool(true)))))
            .build();
    TransactWriteItem data0Write =
        TransactWriteItem.builder()
            .update(
                u ->
                    u.tableName(TBL_DRAFT)
                        .key(
                            Map.ofEntries(
                                entry(PK_GAME_ID, fromS(draftId)),
                                entry(SK_PAGE, GamesPage.DATA0.asAttributeValue())))
                        .updateExpression(
                            "SET #isInitialized = :true, "
                                + "#roundAndTurn = :startingRoundAndTurn, "
                                + "#ttl = :ttl, "
                                + "#seat0.#currentPack = :packData, "
                                + "#seat1.#currentPack = :packData, "
                                + "#seat2.#currentPack = :packData, "
                                + "#seat3.#currentPack = :packData, "
                                + "#seat4.#currentPack = :packData, "
                                + "#seat5.#currentPack = :packData, "
                                + "#seat6.#currentPack = :packData, "
                                + "#seat7.#currentPack = :packData") // TODO This is dummy data
                        .conditionExpression(
                            "attribute_exists(#pk) AND "
                                + "#isInitialized = :false AND "
                                + "attribute_exists(#rulesetId) AND "
                                + "attribute_exists(#timeLimitScheme)")
                        .expressionAttributeNames(
                            Map.ofEntries(
                                entry("#pk", PK_GAME_ID),
                                entry("#isInitialized", K_IS_INITIALIZED),
                                entry("#ttl", K_TTL),
                                entry("#rulesetId", K_RULESET_ID),
                                entry("#timeLimitScheme", K_TIME_LIMIT_SCHEME),
                                entry("#roundAndTurn", K_ROUND_AND_TURN),
                                entry("#seat0", K_SEAT0),
                                entry("#seat1", K_SEAT1),
                                entry("#seat2", K_SEAT2),
                                entry("#seat3", K_SEAT3),
                                entry("#seat4", K_SEAT4),
                                entry("#seat5", K_SEAT5),
                                entry("#seat6", K_SEAT6),
                                entry("#seat7", K_SEAT7),
                                entry("#currentPack", CURRENT_PACK)))
                        .expressionAttributeValues(
                            Map.ofEntries(
                                entry(":ttl", fromN(String.valueOf(ttl.toEpochSecond()))),
                                entry(":false", fromBool(false)),
                                entry(":true", fromBool(true)),
                                entry(":startingRoundAndTurn", fromS("r01t01")),
                                entry(":packData", fromB(SdkBytes.fromUtf8String("b64encPack"))))))
            .build();

    var data1Item = makeItemWithCommonFields(draftId, GamesPage.DATA1, ttl);
    data1Item.put(K_UNOPENED_PACKS, fromB(SdkBytes.fromUtf8String("b64encUnopenedPacks")));
    data1Item.put(K_COLLECTED_CARDS, fromB(SdkBytes.fromUtf8String("b64encCollectedCards")));
    data1Item.put(K_UPGRADE_SHOP_1, fromB(SdkBytes.fromUtf8String("b64encStoreInventory1")));
    TransactWriteItem data1Write =
        TransactWriteItem.builder()
            .put(
                p ->
                    p.tableName(TBL_DRAFT)
                        .item(data1Item)
                        .expressionAttributeNames(Map.of())
                        .expressionAttributeValues(Map.of()))
            .build();

    var data2Item = makeItemWithCommonFields(draftId, GamesPage.DATA2, ttl);
    data2Item.put(K_UPGRADE_SHOP_2, fromB(SdkBytes.fromUtf8String("b64encStoreInventory2")));

    TransactWriteItem data2Write =
        TransactWriteItem.builder()
            .put(
                p ->
                    p.tableName(TBL_DRAFT)
                        .item(data2Item) // TODO setup data
                        .expressionAttributeNames(Map.of())
                        .expressionAttributeValues(Map.of()))
            .build();

    return TransactWriteItemsRequest.builder()
        .transactItems(indexWrite, data0Write, data1Write, data2Write)
        .build();
  }

  /*
   * Returns a map representing the 'core' fields that must exist on a page.
   * The map is mutable, so page-specific fields can be added.
   */
  private static Map<String, AttributeValue> makeItemWithCommonFields(
      String gameId, GamesPage page, ZonedDateTime timeToLive) {
    Stream.of(gameId, page, timeToLive).map(Objects::requireNonNull);
    Map<String, AttributeValue> item = new TreeMap<>();
    item.put(PK_GAME_ID, fromS(gameId));
    item.put(SK_PAGE, page.asAttributeValue());
    item.put(K_TTL, fromN(String.valueOf(timeToLive.toEpochSecond())));
    item.put(K_VERSION, fromN(String.valueOf(ACTIVE_SCHEMA_VERSION)));
    return item;
  }
}
