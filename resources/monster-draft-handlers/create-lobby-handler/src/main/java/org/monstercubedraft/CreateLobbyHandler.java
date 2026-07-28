package org.monstercubedraft;

import java.time.ZonedDateTime;
import java.util.Objects;

import org.monstercubedraft.crac.DynamoDbClientResource;
import org.monstercubedraft.crac.IdGeneratorResource;
import org.monstercubedraft.model.access.draft.DraftTableAccess;
import org.monstercubedraft.model.types.DraftId;
import org.monstercubedraft.model.types.enums.Tcg;

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
        new DraftTableAccess(System.getenv(ENVKEY__GAME_TABLE_NAME)));
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
    System.out.println(input.toString());
    try {
      System.out.println("Generating game ID...");
      DraftId newGameId = idGenResource.generateGameId();
      System.out.println("Done generating game ID. Writing new Draft to table...");
      draftTableAccess
          .onPartition(newGameId)
          .putIndexPage(Tcg.PKMN, 8, ZonedDateTime.now().plusHours(24))
          .writeTo(dynamoResource.getClient());
      System.out.println("Done writing new Draft to table.");
      return APIGatewayV2HTTPResponse.builder()
          .withBody(newGameId.getApiRepresentation())
          .withStatusCode(200)
          .build();
    } catch (Exception e) {
      System.out.print(e.toString());
      return APIGatewayV2HTTPResponse.builder().withStatusCode(500).build();
    }
  }
}
