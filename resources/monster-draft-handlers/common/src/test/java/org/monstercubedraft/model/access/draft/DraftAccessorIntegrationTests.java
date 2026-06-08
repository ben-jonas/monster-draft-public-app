package org.monstercubedraft.model.access.draft;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.monstercubedraft.model.access.TransactionalWritePattern;
import org.monstercubedraft.model.access.WriteItemPattern;
import org.monstercubedraft.model.access.draft.DraftTableAccess.AccessOnPartition;
import org.monstercubedraft.model.types.SessionAlias;
import org.monstercubedraft.model.types.SessionId;
import org.monstercubedraft.model.types.Tcg;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

public class DraftAccessorIntegrationTests {
  static final String TEST_GAMES_TABLENAME = "TestGames";
  static final String SOME_DRAFT_ID = "testId";
  static final ZonedDateTime SOME_TTL = ZonedDateTime.now().plusHours(2);

  static DynamoDbClient dynamoDbClient;

  DraftTableAccess draftTableAccess;

  @BeforeAll
  static void createClient() {
    String awsProfile = System.getenv("MONSTERCUBEDRAFT_TEST_AWS_PROFILE");
    Region awsRegion = Region.of(System.getenv("MONSTERCUBEDRAFT_TEST_AWS_REGION"));
    ProfileCredentialsProvider provider =
        ProfileCredentialsProvider.builder().profileName(awsProfile).build();
    dynamoDbClient =
        DynamoDbClient.builder().region(awsRegion).credentialsProvider(provider).build();
  }

  @BeforeEach
  void setup() {
    draftTableAccess = new DraftTableAccess(TEST_GAMES_TABLENAME);
  }

  @Test
  void initMinimalDraft() {
    // Draft size of 1 and fewest number of writes to get the game to start
    SessionId leaderSessionId = new SessionId("leaderId00");
    var leaderSessionAlias = new SessionAlias("leA");
    AccessOnPartition testDraftAccess = draftTableAccess.onPartition(SOME_DRAFT_ID);

    List<WriteItemPattern<? extends DynamoDbRequest, ? extends DynamoDbResponse>> writes =
        List.of(
            testDraftAccess.putIndexPage(Tcg.PKMN, 1, SOME_TTL),
            testDraftAccess.putData0Page(SOME_TTL),
            testDraftAccess.addSession(leaderSessionId, leaderSessionAlias),
            testDraftAccess.setLeader(leaderSessionId),
            testDraftAccess.sitDownPlayer(leaderSessionAlias, 0),
            testDraftAccess.setRulesetId("someRulesetId"),
            testDraftAccess.setTimeLimitScheme("someTimeLimitScheme"),
            testDraftAccess.readyPlayer(leaderSessionAlias));

    writes.forEach(w -> w.writeTo(dynamoDbClient));
    testDraftAccess.initDraft(SOME_TTL).writeTransactionTo(dynamoDbClient);

    QueryResponse q = testDraftAccess.queryCorePages().queryFrom(dynamoDbClient);
  }

  @Test
  void easy() {
    ZonedDateTime ttlAtStart = ZonedDateTime.now().plusHours(2);
    String testId = "testId";
    SessionId leaderSessionId = new SessionId("zezziom1Id");
    SessionAlias leaderSessionAlias = new SessionAlias("s1a");
    String sampleRulesetId = "011011someRuleset";
    String sampleTimeLimitScheme = "sometimelimitscheme";
    AccessOnPartition testItemAccess = draftTableAccess.onPartition(testId);

    WriteItemPattern<PutItemRequest, PutItemResponse> putIndex =
        testItemAccess.putIndexPage(Tcg.MAGI, 1, ttlAtStart);
    WriteItemPattern<PutItemRequest, PutItemResponse> putData0 =
        testItemAccess.putData0Page(ttlAtStart);
    dynamoDbClient.putItem(putIndex.request());
    dynamoDbClient.putItem(putData0.request());

    WriteItemPattern<UpdateItemRequest, UpdateItemResponse> addSession =
        testItemAccess.addSession(leaderSessionId, leaderSessionAlias);
    dynamoDbClient.updateItem(addSession.request());

    WriteItemPattern<UpdateItemRequest, UpdateItemResponse> setLeader =
        testItemAccess.setLeader(leaderSessionId);
    dynamoDbClient.updateItem(setLeader.request());

    WriteItemPattern<UpdateItemRequest, UpdateItemResponse> setName =
        testItemAccess.setNameForPlayer(leaderSessionAlias, "Susan");
    dynamoDbClient.updateItem(setName.request());

    WriteItemPattern<UpdateItemRequest, UpdateItemResponse> sitDownLeader =
        testItemAccess.sitDownPlayer(leaderSessionAlias, 0);
    dynamoDbClient.updateItem(sitDownLeader.request());

    WriteItemPattern<UpdateItemRequest, UpdateItemResponse> standUpLeader =
        testItemAccess.standUpPlayer(leaderSessionAlias, 0);
    System.out.println(standUpLeader.request());
    dynamoDbClient.updateItem(standUpLeader.request());

    dynamoDbClient.updateItem(sitDownLeader.request());
    WriteItemPattern<UpdateItemRequest, UpdateItemResponse> readyLeader =
        testItemAccess.readyPlayer(leaderSessionAlias);
    dynamoDbClient.updateItem(readyLeader.request());
    WriteItemPattern<UpdateItemRequest, UpdateItemResponse> unreadyLeader =
        testItemAccess.unreadyPlayer(leaderSessionAlias);
    dynamoDbClient.updateItem(unreadyLeader.request());

    // leader must re-ready for draft to start
    dynamoDbClient.updateItem(readyLeader.request());

    WriteItemPattern<UpdateItemRequest, UpdateItemResponse> setRulesetId =
        testItemAccess.setRulesetId(sampleRulesetId);
    dynamoDbClient.updateItem(setRulesetId.request());

    WriteItemPattern<UpdateItemRequest, UpdateItemResponse> setTimeLimitScheme =
        testItemAccess.setTimeLimitScheme(sampleTimeLimitScheme);
    dynamoDbClient.updateItem(setTimeLimitScheme.request());

    Utils.prettyPrintQueryResponse(dynamoDbClient.query(testItemAccess.queryCorePages().request()));

    TransactionalWritePattern initDraft =
        testItemAccess.initDraft(ZonedDateTime.now().plusHours(2));
    dynamoDbClient.transactWriteItems(initDraft.request());

    Utils.prettyPrintQueryResponse(dynamoDbClient.query(testItemAccess.queryCorePages().request()));

    assertThat(Boolean.valueOf(true)).isEqualTo(true);
  }
}
