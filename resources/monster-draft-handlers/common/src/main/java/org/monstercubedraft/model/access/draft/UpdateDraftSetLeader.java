package org.monstercubedraft.model.access.draft;

import static java.util.Map.entry;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_LEADER;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SESSION_MAP;
import static org.monstercubedraft.model.constants.DraftTableConstants.PK_GAME_ID;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;

import java.util.Map;

import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.SessionId;
import org.monstercubedraft.model.types.enums.DraftPageName;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class UpdateDraftSetLeader extends AbstractUpdateDraftItemPattern {

  String updateExpression = "SET #leader = :sessionId";
  String conditionExpression = "attribute_exists(#pk) AND attribute_exists(#sessionMap.#sessionId)";

  private final SessionId sessionId;

  UpdateDraftSetLeader(String tableName, DraftId draftId, SessionId sessionId) {
    super(tableName, draftId);
    this.sessionId = sessionId;
  }

  @Override
  protected DraftPageName page() {
    return DraftPageName.INDEX;
  }

  @Override
  protected String updateExpression() {
    return "SET #leader = :sessionId";
  }

  @Override
  protected String conditionExpression() {
    return "attribute_exists(#pk) AND attribute_exists(#sessionMap.#sessionId)";
  }

  @Override
  protected Map<String, String> expressionAttributeNames() {
    return Map.ofEntries(
        entry("#pk", PK_GAME_ID),
        entry("#sessionMap", K_SESSION_MAP),
        entry("#sessionId", sessionId.toString()),
        entry("#leader", K_LEADER));
  }

  @Override
  protected Map<String, AttributeValue> expressionAttributeValues() {
    return Map.ofEntries(entry(":sessionId", fromS(sessionId.toString())));
  }

  @Override
  public Map<String, String> interpretConditionFailures(Map<String, AttributeValue> oldValues) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }
}
