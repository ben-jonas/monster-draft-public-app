package org.monstercubedraft.model.access.draft;

import static org.monstercubedraft.model.constants.DraftTableConstants.K_IS_INITIALIZED;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_MAXSIZE;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_PLAYERNAMES_MAP;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEATS_TO_ALIASES_MAP;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SESSION_MAP;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_TCG;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromBool;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromM;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromN;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;

import java.time.ZonedDateTime;
import java.util.Map;

import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.enums.DraftPageName;
import org.monstercubedraft.model.types.enums.Tcg;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public class PutDraftIndexPage extends AbstractPutDraftItemPattern {

  private final Tcg tcg;
  private final int numPlayers;
  private final ZonedDateTime ttl;

  PutDraftIndexPage(String tableName, DraftId draftId, Tcg tcg, int numPlayers, ZonedDateTime ttl) {
    super(tableName, draftId);
    this.tcg = tcg;
    this.numPlayers = numPlayers;
    this.ttl = ttl;
  }

  @Override
  public PutItemRequest request() {
    Map<String, AttributeValue> ndxPgItem =
        Utils.makeItemWithCommonFields(draftId.toString(), DraftPageName.INDEX, ttl);
    ndxPgItem.put(K_TCG, fromS(tcg.name()));
    ndxPgItem.put(K_MAXSIZE, fromN(String.valueOf(numPlayers)));
    ndxPgItem.put(K_SESSION_MAP, fromM(Map.of()));
    ndxPgItem.put(K_PLAYERNAMES_MAP, fromM(Map.of()));
    ndxPgItem.put(K_SEATS_TO_ALIASES_MAP, fromM(Map.of()));
    ndxPgItem.put(K_IS_INITIALIZED, fromBool(false));
    return PutItemRequest.builder().tableName(tableName).item(ndxPgItem).build();
  }

  @Override
  public Map<String, String> interpretConditionFailures(Map<String, AttributeValue> oldValues) {
    throw new UnsupportedOperationException("Unconditional.");
  }
}
