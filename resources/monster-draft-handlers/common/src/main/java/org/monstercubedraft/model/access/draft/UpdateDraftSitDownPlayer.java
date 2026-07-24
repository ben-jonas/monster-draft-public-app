package org.monstercubedraft.model.access.draft;

import static java.util.Map.entry;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_ALIASES_SET;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_IS_INITIALIZED;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEATED_SET;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEATS_TO_ALIASES_MAP;
import static org.monstercubedraft.model.constants.DraftTableConstants.PK_GAME_ID;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromBool;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromSs;

import java.util.List;
import java.util.Map;

import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.SessionAlias;
import org.monstercubedraft.model.types.enums.DraftPageName;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class UpdateDraftSitDownPlayer extends AbstractUpdateDraftItemPattern {

  private final SessionAlias sessionAlias;
  private final int seat;

  public UpdateDraftSitDownPlayer(
      String tableName, DraftId draftId, SessionAlias sessionAlias, int seat) {
    super(tableName, draftId);
    this.sessionAlias = sessionAlias;
    this.seat = seat;
  }

  @Override
  protected DraftPageName page() {
    return DraftPageName.INDEX;
  }

  @Override
  protected String updateExpression() {
    return "SET #seatsToAliasesMap.#seat = :alias ADD #seatedSet :aliasSs";
  }

  @Override
  protected String conditionExpression() {
    return "attribute_exists(#pk) AND "
        + "#isInitialized = :false AND "
        + "contains(#aliasesSet, :alias) AND "
        + "attribute_not_exists(#seatsToAliasesMap.#seat) AND "
        + "(attribute_not_exists(#seatedSet) OR NOT contains(#seatedSet, :alias))";
  }

  @Override
  protected Map<String, String> expressionAttributeNames() {
    return Map.ofEntries(
        entry("#pk", PK_GAME_ID),
        entry("#isInitialized", K_IS_INITIALIZED),
        entry("#aliasesSet", K_ALIASES_SET),
        entry("#seatsToAliasesMap", K_SEATS_TO_ALIASES_MAP),
        entry("#seat", String.valueOf(seat)),
        entry("#seatedSet", K_SEATED_SET));
  }

  @Override
  protected Map<String, AttributeValue> expressionAttributeValues() {
    return Map.ofEntries(
        entry(":false", fromBool(false)),
        entry(":alias", fromS(sessionAlias.toString())),
        entry(":aliasSs", fromSs(List.of(sessionAlias.toString()))));
  }

  @Override
  public Map<String, String> interpretConditionFailures(Map<String, AttributeValue> oldValues) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }
}
