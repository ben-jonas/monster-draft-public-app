package org.monstercubedraft.model.access.draft;

import static java.util.Map.entry;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_ALIASES_SET;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_IS_INITIALIZED;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_MAXSIZE;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SESSION_MAP;
import static org.monstercubedraft.model.constants.DraftTableConstants.PK_GAME_ID;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromBool;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromSs;

import java.util.List;
import java.util.Map;

import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.DraftPage;
import org.monstercubedraft.model.types.SessionAlias;
import org.monstercubedraft.model.types.SessionId;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class UpdateDraftAddSession extends AbstractUpdateDraftItemPattern {

  private final SessionId sessionId;
  private final SessionAlias sessionAlias;

  UpdateDraftAddSession(
      String tableName, DraftId draftId, SessionId sessionId, SessionAlias sessionAlias) {
    super(tableName, draftId);
    this.sessionId = sessionId;
    this.sessionAlias = sessionAlias;
  }

  @Override
  protected DraftPage page() {
    return DraftPage.INDEX;
  }

  @Override
  protected String updateExpression() {
    return "SET #sessionMap.#sessionId = :sessionAlias ADD #aliasesSet :sessionAliasSs";
  }

  @Override
  protected String conditionExpression() {
    return "attribute_exists(#pk) AND "
        + "#isInitialized = :false AND "
        + "attribute_not_exists(#sessionMap.#sessionId) AND "
        + "size(#sessionMap) < #maxDraftSize AND "
        + "NOT contains(#aliasesSet, :sessionAliasSs)";
  }

  @Override
  protected Map<String, String> expressionAttributeNames() {
    return Map.ofEntries(
        entry("#pk", PK_GAME_ID),
        entry("#maxDraftSize", K_MAXSIZE),
        entry("#sessionMap", K_SESSION_MAP),
        entry("#sessionId", sessionId.toString()),
        entry("#aliasesSet", K_ALIASES_SET),
        entry("#isInitialized", K_IS_INITIALIZED));
  }

  @Override
  protected Map<String, AttributeValue> expressionAttributeValues() {
    return Map.ofEntries(
        entry(":false", fromBool(false)),
        entry(":sessionAlias", fromS(sessionAlias.toString())),
        entry(":sessionAliasSs", fromSs(List.of(sessionAlias.toString()))));
  }

  @Override
  public Map<String, String> interpretConditionFailures(Map<String, AttributeValue> oldValues) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }
}
