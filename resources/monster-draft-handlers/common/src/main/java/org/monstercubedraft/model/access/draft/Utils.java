package org.monstercubedraft.model.access.draft;

import static org.monstercubedraft.model.constants.DraftTableConstants.ACTIVE_SCHEMA_VERSION;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_TTL;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_DRAFTTBL_VERSION;
import static org.monstercubedraft.model.constants.DraftTableConstants.PK_GAME_ID;
import static org.monstercubedraft.model.constants.DraftTableConstants.SK_PAGE;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromN;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.monstercubedraft.model.types.DraftPage;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

final class Utils {

  private Utils() {}

  static Map<String, AttributeValue> makeItemWithCommonFields(
      String gameId, DraftPage page, ZonedDateTime timeToLive) {
    Stream.of(gameId, page, timeToLive).map(Objects::requireNonNull);
    Map<String, AttributeValue> item = new TreeMap<>();
    item.put(PK_GAME_ID, fromS(gameId));
    item.put(SK_PAGE, page.asAttributeValue());
    item.put(K_TTL, fromN(String.valueOf(timeToLive.toEpochSecond())));
    item.put(K_DRAFTTBL_VERSION, fromN(String.valueOf(ACTIVE_SCHEMA_VERSION)));
    return item;
  }

  public static void prettyPrintQueryResponse(QueryResponse q) {
    for (Map<String, AttributeValue> item : q.items()) {
      System.out.println("Item: " + item.get(PK_GAME_ID).s() + " | " + item.get(SK_PAGE).s());
      for (String k : new TreeSet<String>(item.keySet())) {
        if (!isPartitionOrSortKeyForGameTable(k)) {
          System.out.println("  " + k + ": " + item.get(k));
        }
      }
    }
  }

  private static boolean isPartitionOrSortKeyForGameTable(String s) {
    return Set.of(PK_GAME_ID, SK_PAGE).contains(s);
  }
}
