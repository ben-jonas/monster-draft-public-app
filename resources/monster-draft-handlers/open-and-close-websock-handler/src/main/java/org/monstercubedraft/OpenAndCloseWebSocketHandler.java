package org.monstercubedraft;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;


public class OpenAndCloseWebSocketHandler 
implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private final DynamoDbCommandService dynamoDbCommandService;

    public OpenAndCloseWebSocketHandler() {
        this(new DynamoDbCommandService());
    }

    OpenAndCloseWebSocketHandler(DynamoDbCommandService dynamoDbCommandService) {
        this.dynamoDbCommandService = dynamoDbCommandService;
    }

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(
        APIGatewayV2WebSocketEvent input, Context context) {
        final APIGatewayV2WebSocketResponse response;
        String routeKey = input.getRequestContext().getRouteKey();
        if (routeKey.equals("$connect")) {
            System.out.println("Connect!");
            String connectCommandResult = connectUser(
                input.getRequestContext().getConnectionId(),
                input.getQueryStringParameters());
            if (connectCommandResult.equals("success")) {
                response = generateResponse(200);
            } else {
                response = generateResponse(400);
            }
        } else if (routeKey.equals("$disconnect")) {
            System.out.println("Disconnect!");
            if (dynamoDbCommandService.disconnectUser(
                input.getRequestContext().getConnectionId(),
                input.getQueryStringParameters()
            ).equals("success")) {
                response = generateResponse(200);
            } else {
                response = generateResponse(400);
            }
        } else {
            System.out.printf(
                "Non-connect / non-disconnect route key: %s\n", routeKey);
            response = generateResponse(400);
        }
        System.out.println(input.toString());
        return response;
    }

    private String connectUser(String wsConnectionId, Map<String, String> queryParams) {
        String gameId = queryParams.get("game_id");
        if (gameId == null) {
            return "Error: Missing game ID";
        }
        String sessionIdFromParams = queryParams.get("session_id");
        if (sessionIdFromParams != null) {
            return dynamoDbCommandService.connectToExistingSession(
                wsConnectionId, gameId, sessionIdFromParams);
        } else { // e.g. join a game as new player, and create new game session
            return dynamoDbCommandService.connectToNewSession(wsConnectionId, gameId);
        }
    }

    private APIGatewayV2WebSocketResponse generateResponse(int statusCode) {
        var response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(statusCode);
        return response;
    }
}
