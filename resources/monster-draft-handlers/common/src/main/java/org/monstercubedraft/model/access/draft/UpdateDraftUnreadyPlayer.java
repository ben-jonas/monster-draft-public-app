package org.monstercubedraft.model.access.draft;

import static java.util.Map.entry;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_IS_INITIALIZED;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_READY_SET;
import static org.monstercubedraft.model.constants.DraftTableConstants.PK_GAME_ID;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromBool;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromSs;

import java.util.List;
import java.util.Map;

import org.monstercubedraft.model.types.DraftPage;
import org.monstercubedraft.model.types.SessionAlias;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class UpdateDraftUnreadyPlayer extends AbstractUpdateDraftItemPattern {

  private final SessionAlias sessionAlias;

  public UpdateDraftUnreadyPlayer(String tableName, String draftId, SessionAlias sessionAlias) {
    super(tableName, draftId);
    this.sessionAlias = sessionAlias;
  }

  @Override
  protected DraftPage page() {
    return DraftPage.INDEX;
  }

  @Override
  protected String updateExpression() {
    return "DELETE #readySet :aliasSs";
  }

  @Override
  protected String conditionExpression() {
    return "attribute_exists(#pk) AND #isInitialized = :false";
  }

  @Override
  protected Map<String, String> expressionAttributeNames() {
    return Map.ofEntries(
        entry("#pk", PK_GAME_ID),
        entry("#isInitialized", K_IS_INITIALIZED),
        entry("#readySet", K_READY_SET));
  }

  @Override
  protected Map<String, AttributeValue> expressionAttributeValues() {
    return Map.ofEntries(
        entry(":false", fromBool(false)),
        entry(":aliasSs", fromSs(List.of(sessionAlias.toString()))));
  }

  @Override
  public Map<String, String> interpretConditionFailures(Map<String, AttributeValue> oldValues) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }
}
