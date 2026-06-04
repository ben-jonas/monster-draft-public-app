package org.monstercubedraft;

import static java.util.Map.entry;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.monstercubedraft.crac.IdGeneratorResource;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

public class Main {

  private static String PROFILE_KWD = "--profile";

  private static boolean isPartitionOrSortKeyForGameTable(String s) {
    return Set.of(GamesTableDynamoCommands.PK_GAME_ID, GamesTableDynamoCommands.SK_PAGE)
        .contains(s);
  }

  private static void prettyPrintQueryResponse(QueryResponse q) {
    for (Map<String, AttributeValue> item : q.items()) {
      System.out.println(
          "Item: "
              + item.get(GamesTableDynamoCommands.PK_GAME_ID).s()
              + " | "
              + item.get(GamesTableDynamoCommands.SK_PAGE).s());
      for (String k : new TreeSet<String>(item.keySet())) {
        if (!isPartitionOrSortKeyForGameTable(k)) {
          System.out.println("  " + k + ": " + item.get(k));
        }
      }
    }
  }

  public static void main(String[] args) {
    if (args.length != 2 || !(args[0].equals(PROFILE_KWD))) {
      System.err.println("Usage: \"java local-dynamo-tests " + PROFILE_KWD + " <profile>\"");
      System.exit(1);
    }

    ProfileCredentialsProvider provider =
        ProfileCredentialsProvider.builder().profileName(args[1]).build();

    var idGenerator = new IdGeneratorResource();

    DynamoDbClient dynamoDb =
        DynamoDbClient.builder().region(Region.US_EAST_1).credentialsProvider(provider).build();

    String newDraftId = "test0001_wajfiVLO_mYJbjt3O"; // idGenerator.generateGameId();
    ZonedDateTime ttl = ZonedDateTime.now().plusHours(24L);

    QueryResponse dataOut;

    dynamoDb.putItem(GamesTableDynamoCommands.putDraftIndexPage(newDraftId, Tcg.PKMN, 8));
    dynamoDb.putItem(GamesTableDynamoCommands.putDraftData0Page(newDraftId, ttl));
    dataOut = dynamoDb.query(GamesTableDynamoCommands.queryCoreDraftPages(newDraftId));
    prettyPrintQueryResponse(dataOut);

    String session1Id = "session_1";
    String session2Id = "session_2";
    String session3Id = "session_3";
    String session4Id = "session_4";
    String session5Id = "session_5";
    String session6Id = "session_6";
    String session7Id = "session_7";
    String session8Id = "session_8";
    String alias1 = "al1";
    String alias2 = "al2";
    String alias3 = "al3";
    String alias4 = "al4";
    String alias5 = "al5";
    String alias6 = "al6";
    String alias7 = "al7";
    String alias8 = "al8";

    Map<String, String> sessionsToAliases =
        Map.ofEntries(
            entry(session1Id, alias1),
            entry(session2Id, alias2),
            entry(session3Id, alias3),
            entry(session4Id, alias4),
            entry(session5Id, alias5),
            entry(session6Id, alias6),
            entry(session7Id, alias7),
            entry(session8Id, alias8));

    try {
      {
        int i = 0;
        for (String sessionId : sessionsToAliases.keySet()) {
          String alias = sessionsToAliases.get(sessionId);
          dynamoDb.updateItem(
              GamesTableDynamoCommands.updateDraftAddSession(newDraftId, sessionId, alias));
          dynamoDb.updateItem(
              GamesTableDynamoCommands.updateDraftSitDownPlayer(newDraftId, alias, i++));
          dynamoDb.updateItem(GamesTableDynamoCommands.updateDraftReadyPlayer(newDraftId, alias));
        }
      }
      dynamoDb.updateItem(
          GamesTableDynamoCommands.updateDraftSetSessionAsLeader(newDraftId, session1Id));
      dynamoDb.updateItem(
          GamesTableDynamoCommands.updateDraftRulesetId(newDraftId, "someKindaRuleset"));
      dynamoDb.updateItem(
          GamesTableDynamoCommands.updateDraftSetTimeLimitScheme(
              newDraftId, "SOME_TIME_LIMIT_SCHEME"));
      dynamoDb.updateItem(
          GamesTableDynamoCommands.updateDraftRulesetId(newDraftId, "SOME_RULESET_ID"));
    } catch (ConditionalCheckFailedException e) {
      System.out.println("Condition check failure. Item:");
      System.out.println(e.item());
    }

    dataOut = dynamoDb.query(GamesTableDynamoCommands.queryCoreDraftPages(newDraftId));
    System.out.println("\nData after update: ");
    prettyPrintQueryResponse(dataOut);

    try {
      dynamoDb.transactWriteItems(GamesTableDynamoCommands.initDraftCorePages(newDraftId, ttl));
    } catch (TransactionCanceledException e) {
      System.out.println("Transaction canceled reasons:");
      System.out.println(e.cancellationReasons());
    }

    dataOut = dynamoDb.query(GamesTableDynamoCommands.queryCoreDraftPages(newDraftId));
    System.out.println("\nData after transaction: ");
    prettyPrintQueryResponse(dataOut);
  }
}
