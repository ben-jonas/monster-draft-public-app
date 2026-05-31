package org.monstercubedraft;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;

public class GamesTableDynamoCommands {

    public static final String GAMES_TABLENAME = "TestGames";
    public static final String PK_GAME_ID = "gId";
    public static final String SORTK_PAGE = "pag";

    public static final String PK_NOT_EXISTS_EXPR = String.format("attribute_not_exists(%s)", PK_GAME_ID);

    public static DescribeTableRequest.Builder describeGamesTableRequest() {
        return DescribeTableRequest.builder().tableName(GAMES_TABLENAME);
    }

    public static PutItemRequest.Builder putGameItemIfNotExists(String gameId, GamesPage page) {
        Map<String, AttributeValue> item = new TreeMap<>();
        item.put(PK_GAME_ID, AttributeValue.fromS(Objects.requireNonNull(gameId)));
        item.put(SORTK_PAGE, Objects.requireNonNull(page).asAttributeValue());
        return PutItemRequest.builder().tableName(GAMES_TABLENAME).item(item).conditionExpression(PK_NOT_EXISTS_EXPR);
    }

    public static BatchWriteItemRequest initializeGame(String gameId) {
        Objects.requireNonNull(gameId);
        List<WriteRequest> writes = Stream.of(
            GamesPage.INDEX,
            GamesPage.DATA0,
            GamesPage.DATA1,
            GamesPage.DATA2)
        .map(page -> 
            WriteRequest.builder()
            .putRequest(putGame(gameId, page).build())
            .build())
        .toList();
        BatchWriteItemRequest.Builder batchWriteBuilder = BatchWriteItemRequest.builder();
        batchWriteBuilder.requestItems(Map.of("TestGames", writes));
        return batchWriteBuilder.build();
    }

    private static PutRequest.Builder putGame(String gameId, GamesPage page) {
        Map<String, AttributeValue> item = new TreeMap<>();
        item.put(PK_GAME_ID, AttributeValue.fromS(Objects.requireNonNull(gameId)));
        item.put(SORTK_PAGE, Objects.requireNonNull(page).asAttributeValue());
        return PutRequest.builder().item(item);
    }
}