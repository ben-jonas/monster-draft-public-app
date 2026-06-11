package org.monstercubedraft.model.access.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.monstercubedraft.model.constants.SessionTableConstants;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.SessionId;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class SessionAccessIntegrationTests {
  static final String TEST_SESSIONS_TABLENAME = "TestSessions";
  static final SessionId SOME_SESSION_ID = new SessionId("teztTEZT01");
  static final DraftId SOME_DRAFT_ID = DraftId.fromApiRepresentation("DraftTzt_DraftTzt_DraftTzt");

  static DynamoDbClient dynamoDbClient;

  SessionTableAccess sessionTableAccess;

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
    sessionTableAccess = new SessionTableAccess(TEST_SESSIONS_TABLENAME);
  }

  @Test
  void putAndRetrieveSessionId() {
    String testWebsocketConnectionId = "someWsConn=";
    sessionTableAccess
        .onPartition(SOME_DRAFT_ID)
        .putSession(SOME_SESSION_ID, testWebsocketConnectionId, ZonedDateTime.now().plusHours(1))
        .writeTo(dynamoDbClient);

    Map<String, AttributeValue> item =
        sessionTableAccess
            .onPartition(SOME_DRAFT_ID)
            .getSession(SOME_SESSION_ID)
            .getFrom(dynamoDbClient)
            .item();

    assertThat(item.get(SessionTableConstants.K_WS_CONNECTION_ID).s())
        .isEqualTo(testWebsocketConnectionId);
  }

  @Test
  void putInAndQueryMultipleSessionIds() {
    String testWebsocketConnectionId = "someWsConn=";
    for (int i = 0; i < 8; i++) {
      SessionId iSessionId = new SessionId("teztTEZT0" + i);
      sessionTableAccess
          .onPartition(SOME_DRAFT_ID)
          .putSession(iSessionId, testWebsocketConnectionId, ZonedDateTime.now().plusHours(1))
          .writeTo(dynamoDbClient);
    }

    List<Map<String, AttributeValue>> items =
        sessionTableAccess.onPartition(SOME_DRAFT_ID).queryAll().queryFrom(dynamoDbClient).items();

    assertThat(items.size()).isEqualTo(8);
    for (var item : items) {
      assertThat(item.get(SessionTableConstants.K_WS_CONNECTION_ID).s())
          .isEqualTo(testWebsocketConnectionId);
    }
  }
}
