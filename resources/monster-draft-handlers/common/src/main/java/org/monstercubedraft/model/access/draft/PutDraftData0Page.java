package org.monstercubedraft.model.access.draft;

import static java.util.Map.entry;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_IS_INITIALIZED;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT0;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT1;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT2;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT3;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT4;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT5;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT6;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT7;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEATX_CURRENT_PACK;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEATX_HELD_CARDS;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEATX_PENDING_MOVE;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_TIME_LIMIT_SCHEME;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromBool;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromM;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromNul;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.DraftPage;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public class PutDraftData0Page extends AbstractPutDraftItemPattern {

  private final ZonedDateTime timeToLive;

  PutDraftData0Page(String tableName, DraftId draftId, ZonedDateTime timeToLive) {
    super(tableName, draftId);
    this.timeToLive = timeToLive;
  }

  @Override
  public PutItemRequest request() {
    Map<String, AttributeValue> da0PgItem =
        Utils.makeItemWithCommonFields(draftId.toString(), DraftPage.DATA0, timeToLive);
    da0PgItem.put(K_TIME_LIMIT_SCHEME, fromS("NONE"));
    da0PgItem.put(K_IS_INITIALIZED, fromBool(false));

    Map<String, AttributeValue> commonSeatFields =
        Map.ofEntries(
            entry(K_SEATX_CURRENT_PACK, fromNul(true)),
            entry(K_SEATX_PENDING_MOVE, fromNul(true)),
            entry(K_SEATX_HELD_CARDS, fromNul(true)));
    for (String k :
        Set.of(K_SEAT0, K_SEAT1, K_SEAT2, K_SEAT3, K_SEAT4, K_SEAT5, K_SEAT6, K_SEAT7)) {
      da0PgItem.put(k, fromM(commonSeatFields));
    }

    return PutItemRequest.builder().tableName(tableName).item(da0PgItem).build();
  }

  @Override
  public Map<String, String> interpretConditionFailures(Map<String, AttributeValue> oldValues) {
    throw new UnsupportedOperationException("Unconditional.");
  }
}
