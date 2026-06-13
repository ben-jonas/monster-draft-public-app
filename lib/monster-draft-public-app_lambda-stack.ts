import * as cdk from 'aws-cdk-lib/core';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { join } from 'path';
import { Construct } from 'constructs';

export interface MonsterDraftPublicAppLambdaStackProps extends cdk.StackProps {
  websocketSessionsTable: dynamodb.TableV2,
  draftTable: dynamodb.TableV2
}

export class MonsterDraftPublicAppLambdaStack extends cdk.Stack {
  public readonly createLobbyHandlerAlias: lambda.Alias;
  public readonly openWebsocketConnectionHandlerAlias: lambda.Alias;
  constructor(scope: Construct, id: string, props: MonsterDraftPublicAppLambdaStackProps) {
    super(scope, id, props);
    const { websocketSessionsTable, draftTable } = props;

    // handler for new lobbies and associated HTTP API
    const createLobbyHandler = new lambda.Function(this, "CreateLobbyHandler", {
        functionName: "monstercubedraft--dev--CreateLobbyHandler",
        runtime: lambda.Runtime.JAVA_25,
        handler: 'org.monstercubedraft.CreateLobbyHandler::handleRequest',
        environment: {
            "GAME_TABLE_NAME": draftTable.tableName
        },
        timeout: cdk.Duration.seconds(8),
        memorySize: 256,
        code: lambda.Code.fromAsset(join(__dirname,
        '../resources/monster-draft-handlers/create-lobby-handler/target/create-lobby-handler.jar')),
        snapStart: lambda.SnapStartConf.ON_PUBLISHED_VERSIONS,
    });
    draftTable.grantReadWriteData(createLobbyHandler);
    
    const createLobbyHandlerVersion = createLobbyHandler.currentVersion;
    this.createLobbyHandlerAlias = new lambda.Alias(this, 'CreateLobbyAlias', {
      aliasName: 'CreateLobbyAlias',
      version: createLobbyHandlerVersion,
    })

    // handler for opening new sessions and associated WebSocket API
    const openWebsockHandler = new lambda.Function(this, "OpenWebsocketConnectionHandler", {
      functionName: "monstercubedraft--dev--OpenWebsocketConnectionHandler",
      runtime: lambda.Runtime.JAVA_25,
      handler: 'org.monstercubedraft.OpenAndCloseWebSocketHandler::handleRequest',
      environment: {
        WSCONNECTIONS_TABLE_NAME: websocketSessionsTable.tableName,
        GAME_TABLE_NAME: draftTable.tableName
      },
      timeout: cdk.Duration.seconds(8),
      memorySize: 256,
      code: lambda.Code.fromAsset(join(__dirname, 
        '../resources/monster-draft-handlers/open-and-close-websock-handler/target/open-and-close-websock-handler.jar')),
      snapStart: lambda.SnapStartConf.ON_PUBLISHED_VERSIONS
    });
    websocketSessionsTable.grantReadWriteData(openWebsockHandler);
    draftTable.grantReadWriteData(openWebsockHandler);

    const openAndCloseWSockHandlerVersion = openWebsockHandler.currentVersion;
    this.openWebsocketConnectionHandlerAlias = new lambda.Alias(this, 'OpenWebsocketAlias',{
      aliasName: 'OpenWebsocketAlias',
      version: openAndCloseWSockHandlerVersion
    })
  }
}