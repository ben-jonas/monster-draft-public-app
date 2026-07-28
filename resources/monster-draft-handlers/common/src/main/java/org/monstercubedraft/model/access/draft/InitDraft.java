package org.monstercubedraft.model.access.draft;

import static java.util.Map.entry;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_COLLECTED_CARDS;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_IS_INITIALIZED;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_MAXSIZE;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_READY_SET;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_ROUND_AND_TURN;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_RULESET_ID;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT0;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT1;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT2;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT3;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT4;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT5;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT6;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEAT7;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_SEATX_CURRENT_PACK;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_TIME_LIMIT_SCHEME;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_TTL;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_UNOPENED_PACKS;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_UPGRADE_SHOP_1;
import static org.monstercubedraft.model.constants.DraftTableConstants.K_UPGRADE_SHOP_2;
import static org.monstercubedraft.model.constants.DraftTableConstants.PK_GAME_ID;
import static org.monstercubedraft.model.constants.DraftTableConstants.SK_PAGE;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromB;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromBool;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromN;
import static software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS;

import java.time.ZonedDateTime;
import java.util.Map;

import org.monstercubedraft.model.access.TransactionalWritePattern;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.enums.DraftPageName;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;

class InitDraft implements TransactionalWritePattern {

  private String tableName;
  private DraftId draftId;
  private ZonedDateTime ttl;

  public InitDraft(String tableName, DraftId draftId2, ZonedDateTime ttl) {
    this.tableName = tableName;
    this.draftId = draftId2;
    this.ttl = ttl;
  }

  @Override
  public TransactWriteItemsRequest request() {
    return TransactWriteItemsRequest.builder()
        .transactItems(updateIndex(), updateData0(), putData1(), putData2())
        .build();
  }

  @Override
  public TransactWriteItemsResponse writeTransactionTo(DynamoDbClient dynamoDb) {
    return dynamoDb.transactWriteItems(this.request());
  }

  @Override
  public Map<String, String> interpretTransactionFailures() {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  private TransactWriteItem updateIndex() {
    return TransactWriteItem.builder()
        .update(
            u ->
                u.tableName(tableName)
                    .key(
                        Map.ofEntries(
                            entry(PK_GAME_ID, fromS(draftId.toString())),
                            entry(SK_PAGE, DraftPageName.INDEX.asAttributeValue())))
                    .updateExpression("SET #isInitialized = :true, #ttl = :ttl")
                    .conditionExpression(
                        "attribute_exists(#pk) AND "
                            + "#isInitialized = :false AND "
                            + "size(#readySet) = #maxDraftSize")
                    .expressionAttributeNames(
                        Map.ofEntries(
                            entry("#pk", PK_GAME_ID),
                            entry("#isInitialized", K_IS_INITIALIZED),
                            entry("#ttl", K_TTL),
                            entry("#readySet", K_READY_SET),
                            entry("#maxDraftSize", K_MAXSIZE)))
                    .expressionAttributeValues(
                        Map.ofEntries(
                            entry(":ttl", fromN(String.valueOf(ttl.toEpochSecond()))),
                            entry(":false", fromBool(false)),
                            entry(":true", fromBool(true)))))
        .build();
  }

  private TransactWriteItem updateData0() {
    return TransactWriteItem.builder()
        .update(
            u ->
                u.tableName(tableName)
                    .key(
                        Map.ofEntries(
                            entry(PK_GAME_ID, fromS(draftId.toString())),
                            entry(SK_PAGE, DraftPageName.DATA0.asAttributeValue())))
                    .updateExpression(
                        "SET #isInitialized = :true, "
                            + "#roundAndTurn = :startingRoundAndTurn, "
                            + "#ttl = :ttl, "
                            + "#seat0.#currentPack = :packData, "
                            + "#seat1.#currentPack = :packData, "
                            + "#seat2.#currentPack = :packData, "
                            + "#seat3.#currentPack = :packData, "
                            + "#seat4.#currentPack = :packData, "
                            + "#seat5.#currentPack = :packData, "
                            + "#seat6.#currentPack = :packData, "
                            + "#seat7.#currentPack = :packData") // TODO This is dummy data
                    .conditionExpression(
                        "attribute_exists(#pk) AND "
                            + "#isInitialized = :false AND "
                            + "attribute_exists(#rulesetId) AND "
                            + "attribute_exists(#timeLimitScheme)")
                    .expressionAttributeNames(
                        Map.ofEntries(
                            entry("#pk", PK_GAME_ID),
                            entry("#isInitialized", K_IS_INITIALIZED),
                            entry("#ttl", K_TTL),
                            entry("#rulesetId", K_RULESET_ID),
                            entry("#timeLimitScheme", K_TIME_LIMIT_SCHEME),
                            entry("#roundAndTurn", K_ROUND_AND_TURN),
                            entry("#seat0", K_SEAT0),
                            entry("#seat1", K_SEAT1),
                            entry("#seat2", K_SEAT2),
                            entry("#seat3", K_SEAT3),
                            entry("#seat4", K_SEAT4),
                            entry("#seat5", K_SEAT5),
                            entry("#seat6", K_SEAT6),
                            entry("#seat7", K_SEAT7),
                            entry("#currentPack", K_SEATX_CURRENT_PACK)))
                    .expressionAttributeValues(
                        Map.ofEntries(
                            entry(":ttl", fromN(String.valueOf(ttl.toEpochSecond()))),
                            entry(":false", fromBool(false)),
                            entry(":true", fromBool(true)),
                            entry(":startingRoundAndTurn", fromS("r01t01")),
                            entry(":packData", fromB(SdkBytes.fromUtf8String("b64encPack"))))))
        .build();
  }

  private TransactWriteItem putData1() {
    Map<String, AttributeValue> data1Fields =
        Utils.makeItemWithCommonFields(draftId.toString(), DraftPageName.DATA1, ttl);
    // TODO This is dummy data
    data1Fields.put(K_UNOPENED_PACKS, fromB(SdkBytes.fromUtf8String("b64encUnopenedPacks")));
    data1Fields.put(K_COLLECTED_CARDS, fromB(SdkBytes.fromUtf8String("b64encCollectedCards")));
    data1Fields.put(K_UPGRADE_SHOP_1, fromB(SdkBytes.fromUtf8String("b64encStoreInventory1")));
    return TransactWriteItem.builder().put(p -> p.tableName(tableName).item(data1Fields)).build();
  }

  private TransactWriteItem putData2() {
    Map<String, AttributeValue> data2Fields =
        Utils.makeItemWithCommonFields(draftId.toString(), DraftPageName.DATA2, ttl);
    // TODO This is dummy data
    data2Fields.put(K_UPGRADE_SHOP_2, fromB(SdkBytes.fromUtf8String("b64encStoreInventory2")));
    return TransactWriteItem.builder().put(p -> p.tableName(tableName).item(data2Fields)).build();
  }
}
