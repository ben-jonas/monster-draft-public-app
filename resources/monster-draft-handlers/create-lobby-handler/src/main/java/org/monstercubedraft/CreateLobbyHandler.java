package org.monstercubedraft;

import java.time.ZonedDateTime;
import java.util.Objects;

import org.monstercubedraft.crac.DynamoDbClientResource;
import org.monstercubedraft.crac.IdGeneratorResource;
import org.monstercubedraft.model.access.draft.DraftTableAccess;
import org.monstercubedraft.model.types.DraftId;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

public class CreateLobbyHandler
    implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

  static final String ENVKEY__GAME_TABLE_NAME = "GAME_TABLE_NAME";

  private final DynamoDbClientResource dynamoResource;
  private final DraftTableAccess draftTableAccess;
  private final IdGeneratorResource idGenResource;

  public CreateLobbyHandler() {
    this(
        new DynamoDbClientResource(),
        new IdGeneratorResource(),
        new DraftTableAccess(ENVKEY__GAME_TABLE_NAME));
  }

  public CreateLobbyHandler(
      DynamoDbClientResource dynamoResource,
      IdGeneratorResource idGenResource,
      DraftTableAccess draftTableAccess) {
    this.draftTableAccess = Objects.requireNonNull(draftTableAccess);
    this.dynamoResource = Objects.requireNonNull(dynamoResource);
    this.idGenResource = Objects.requireNonNull(idGenResource);
  }

  @Override
  public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
    try {
      DraftId newGameId = idGenResource.generateGameId();
      draftTableAccess
          .onPartition(newGameId)
          .putData0Page(ZonedDateTime.now().plusHours(24))
          .writeTo(dynamoResource.getClient());
      return APIGatewayV2HTTPResponse.builder()
          .withBody(newGameId.getApiRepresentation())
          .withStatusCode(200)
          .build();
    } catch (Exception e) {
      System.out.print(e);
      return APIGatewayV2HTTPResponse.builder().withStatusCode(500).build();
    }
  }
}
