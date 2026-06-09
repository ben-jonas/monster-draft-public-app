package org.monstercubedraft.model.access.draft;

import static java.util.Map.entry;
import static org.monstercubedraft.model.constants.DraftTableConstants.PK_GAME_ID;
import static org.monstercubedraft.model.constants.DraftTableConstants.SK_PAGE;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;
import static software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure.ALL_OLD;

import java.util.Map;

import org.monstercubedraft.model.access.WriteItemPattern;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.DraftPage;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

public abstract class AbstractUpdateDraftItemPattern
    implements WriteItemPattern<UpdateItemRequest, UpdateItemResponse> {

  protected final String tableName;
  protected final DraftId draftId;

  AbstractUpdateDraftItemPattern(String tableName, DraftId draftId) {
    this.tableName = tableName;
    this.draftId = draftId;
  }

  protected abstract DraftPage page();

  protected abstract String updateExpression();

  protected abstract String conditionExpression();

  protected abstract Map<String, String> expressionAttributeNames();

  protected abstract Map<String, AttributeValue> expressionAttributeValues();

  @Override
  public UpdateItemRequest request() {
    return UpdateItemRequest.builder()
        .tableName(this.tableName)
        .key(
            Map.ofEntries(
                entry(PK_GAME_ID, fromS(this.draftId.toString())),
                entry(SK_PAGE, page().asAttributeValue())))
        .updateExpression(updateExpression())
        .conditionExpression(conditionExpression())
        .expressionAttributeNames(expressionAttributeNames())
        .expressionAttributeValues(expressionAttributeValues())
        .returnValuesOnConditionCheckFailure(ALL_OLD)
        .build();
  }

  @Override
  public UpdateItemResponse writeTo(DynamoDbClient dynamoDb) {
    return dynamoDb.updateItem(this.request());
  }
}
