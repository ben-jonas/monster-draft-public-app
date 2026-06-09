package org.monstercubedraft.model.access.draft;

import static java.util.Map.entry;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_IS_INITIALIZED;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_RULESET_ID;
import static org.monstercubedraft.model.constants.DraftTableConstants.PK_GAME_ID;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromBool;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;

import java.util.Map;

import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.DraftPage;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class UpdateDraftSetRulesetId extends AbstractUpdateDraftItemPattern {

  private final String rulesetId;

  public UpdateDraftSetRulesetId(String tableName, DraftId draftId, String rulesetId) {
    super(tableName, draftId);
    this.rulesetId = rulesetId;
  }

  @Override
  protected DraftPage page() {
    return DraftPage.DATA0;
  }

  @Override
  protected String updateExpression() {
    return "SET #rulesetId = :rulesetId";
  }

  @Override
  protected String conditionExpression() {
    return "attribute_exists(#pk) AND #isInitialized = :false";
  }

  @Override
  protected Map<String, String> expressionAttributeNames() {

    return Map.ofEntries(
        entry("#rulesetId", K_RULESET_ID),
        entry("#pk", PK_GAME_ID),
        entry("#isInitialized", K_IS_INITIALIZED));
  }

  @Override
  protected Map<String, AttributeValue> expressionAttributeValues() {
    return Map.ofEntries(entry(":rulesetId", fromS(rulesetId)), entry(":false", fromBool(false)));
  }

  @Override
  public Map<String, String> interpretConditionFailures(Map<String, AttributeValue> oldValues) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }
}
