package org.monstercubedraft.model.access.draft;

import static java.util.Map.entry;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_ALIASES_SET;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_PLAYERNAMES_MAP;
import static org.monstercubedraft.model.constants.DraftTableConstants.PK_GAME_ID;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;

import java.util.Map;

import org.monstercubedraft.model.types.DraftPage;
import org.monstercubedraft.model.types.SessionAlias;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class UpdateDraftSetPlayerName extends AbstractUpdateDraftItemPattern {

  static String updateExpression = "SET #playerNamesMap.#alias = :playerName";
  static String conditionExpression =
      "attribute_exists(#pk) AND attribute_exists(#aliasesSet) AND contains(#aliasesSet, :alias)";

  private final SessionAlias sessionAlias;
  private final String sessionName;

  UpdateDraftSetPlayerName(
      String tableName, String draftId, SessionAlias sessionAlias, String sessionName) {
    super(tableName, draftId);
    this.sessionAlias = sessionAlias;
    this.sessionName = sessionName;
  }

  @Override
  protected DraftPage page() {
    return DraftPage.INDEX;
  }

  @Override
  protected String updateExpression() {
    return "SET #playerNamesMap.#alias = :playerName";
  }

  @Override
  protected String conditionExpression() {
    return "attribute_exists(#pk) AND "
        + "attribute_exists(#aliasesSet) AND "
        + "contains(#aliasesSet, :alias)";
  }

  @Override
  protected Map<String, String> expressionAttributeNames() {
    return Map.ofEntries(
        entry("#pk", PK_GAME_ID),
        entry("#aliasesSet", K_ALIASES_SET),
        entry("#alias", sessionAlias.toString()),
        entry("#playerNamesMap", K_PLAYERNAMES_MAP));
  }

  @Override
  protected Map<String, AttributeValue> expressionAttributeValues() {
    return Map.ofEntries(
        entry(":alias", fromS(sessionAlias.toString())), entry(":playerName", fromS(sessionName)));
  }

  @Override
  public Map<String, String> interpretConditionFailures(Map<String, AttributeValue> oldValues) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }
}
