package org.monstercubedraft;

import java.util.Map;

import org.monstercubedraft.DynamoDbCommandService.CommandResult;
import org.monstercubedraft.DynamoDbCommandService.CommandResult.FailedCondition;
import org.monstercubedraft.DynamoDbCommandService.CommandResult.Succeeded;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;

import software.amazon.awssdk.core.exception.SdkServiceException;


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
    	System.out.println(input.toString());
    	try {
    		String routeKey = input.getRequestContext().getRouteKey();
	        if (routeKey.equals("$connect")) {
	            System.out.println("Connect!");
	            
	            String connectCommandResult = connectUser(
	                input.getRequestContext().getConnectionId(),
	                input.getQueryStringParameters());
	            if (connectCommandResult.equals("success")) {
	                return generateResponse(200);
	            } else {
	                return generateResponse(400);
	            }
	        } else if (routeKey.equals("$disconnect")) {
	            System.out.println("Disconnect!");
	            if (dynamoDbCommandService.disconnectUser(
	                input.getRequestContext().getConnectionId(),
	                input.getQueryStringParameters()
	            ).equals("success")) {
	                return generateResponse(200);
	            } else {
	                return generateResponse(400);
	            }
	        } else {
	            System.out.printf(
	                "Non-connect / non-disconnect route key: %s\n", routeKey);
	            return generateResponse(400);
	        }
    	} catch (SdkServiceException sdkServiceException) {
    		System.out.println("Sdk service exception: " + sdkServiceException.toString());
    		return generateResponse(500);
    	} catch (RuntimeException e) {
    		System.out.println("Non-SDK-service exception: " + e.toString());
    		return generateResponse(500);
    	}
    }

    private String connectUser(String wsConnectionId, Map<String, String> queryParams) {
        String gameId = queryParams.get("game_id");
        if (gameId == null) {
            return "Error: Missing game ID";
        }
        String sessionIdFromParams = queryParams.get("session_id");
        CommandResult result = sessionIdFromParams == null ? 
        		dynamoDbCommandService.connectToNewSession(
            			wsConnectionId, gameId)
        		: dynamoDbCommandService.connectToExistingSession(
                        wsConnectionId, gameId, sessionIdFromParams);
        switch (result) {
    	case Succeeded _ -> { return "success"; }
    	case FailedCondition _ -> { return "failed"; }
    	}
    }

    private APIGatewayV2WebSocketResponse generateResponse(int statusCode) {
        var response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(statusCode);
        return response;
    }
}
