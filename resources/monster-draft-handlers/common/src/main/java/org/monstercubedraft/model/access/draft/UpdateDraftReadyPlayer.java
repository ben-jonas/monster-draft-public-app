package org.monstercubedraft.model.access.draft;

import static java.util.Map.entry;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_ALIASES_SET;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_IS_INITIALIZED;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_READY_SET;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEATED_SET;
import static org.monstercubedraft.model.constants.DraftTableConstants.PK_GAME_ID;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromBool;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromSs;

import java.util.List;
import java.util.Map;

import org.monstercubedraft.model.types.DraftPage;
import org.monstercubedraft.model.types.SessionAlias;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class UpdateDraftReadyPlayer extends AbstractUpdateDraftItemPattern {

  private final SessionAlias sessionAlias;

  UpdateDraftReadyPlayer(String tableName, String draftId, SessionAlias sessionAlias) {
    super(tableName, draftId);
    this.sessionAlias = sessionAlias;
  }

  @Override
  protected DraftPage page() {
    return DraftPage.INDEX;
  }

  @Override
  protected String updateExpression() {
    return "ADD #readySet :aliasSs";
  }

  @Override
  protected String conditionExpression() {
    return "attribute_exists(#pk) AND "
        + "#isInitialized = :false AND "
        + "contains(#aliasesSet, :alias) AND "
        + "contains(#seatedSet, :alias)";
  }

  @Override
  protected Map<String, String> expressionAttributeNames() {
    return Map.ofEntries(
        entry("#pk", PK_GAME_ID),
        entry("#isInitialized", K_IS_INITIALIZED),
        entry("#aliasesSet", K_ALIASES_SET),
        entry("#seatedSet", K_SEATED_SET),
        entry("#readySet", K_READY_SET));
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
