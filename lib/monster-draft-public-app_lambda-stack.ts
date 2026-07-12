import * as cdk from 'aws-cdk-lib/core';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as lambdaEventSources from 'aws-cdk-lib/aws-lambda-event-sources';
import * as iam from 'aws-cdk-lib/aws-iam';
import { join } from 'path';
import { Construct } from 'constructs';
import { WEBSOCKET_CALLBACK_URL_PARAM_NAME } from './constants';

export interface MonsterDraftPublicAppLambdaStackProps extends cdk.StackProps {
  websocketSessionsTable: dynamodb.TableV2,
  draftTable: dynamodb.TableV2,
  draftQueue: sqs.Queue,
}

export class MonsterDraftPublicAppLambdaStack extends cdk.Stack {
  public readonly createLobbyHandlerAlias: lambda.Alias;
  public readonly openWebsocketConnectionHandlerAlias: lambda.Alias;
  public readonly mainDraftHandlerAlias: lambda.Alias;

  constructor(scope: Construct, id: string, props: MonsterDraftPublicAppLambdaStackProps) {
    super(scope, id, props);
    const { websocketSessionsTable, draftTable, draftQueue } = props;

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
    });

    // handler for opening new sessions and associated WebSocket API
    const openWebsockHandler = new lambda.Function(this, "OpenWebsocketConnectionHandler", {
      functionName: "monstercubedraft--dev--OpenWebsocketConnectionHandler",
      runtime: lambda.Runtime.JAVA_25,
      handler: 'org.monstercubedraft.OpenWebsocketHandler::handleRequest',
      environment: {
        WSCONNECTIONS_TABLE_NAME: websocketSessionsTable.tableName,
        GAME_TABLE_NAME: draftTable.tableName
      },
      timeout: cdk.Duration.seconds(8),
      memorySize: 256,
      code: lambda.Code.fromAsset(join(__dirname,
        '../resources/monster-draft-handlers/open-websock-handler/target/open-websock-handler.jar')),
      snapStart: lambda.SnapStartConf.ON_PUBLISHED_VERSIONS
    });
    websocketSessionsTable.grantReadWriteData(openWebsockHandler);
    draftTable.grantReadWriteData(openWebsockHandler);

    const openWebsockHandlerVersion = openWebsockHandler.currentVersion;
    this.openWebsocketConnectionHandlerAlias = new lambda.Alias(this, 'OpenWebsocketAlias', {
      aliasName: 'OpenWebsocketAlias',
      version: openWebsockHandlerVersion
    });

    // handler for in-game actions, triggered by SQS
    const mainDraftHandler = new lambda.Function(this, "MainDraftHandler", {
      functionName: "monstercubedraft--dev--MainDraftHandler",
      runtime: lambda.Runtime.JAVA_25,
      handler: 'org.monstercubedraft.MainDraftHandler::handleRequest',
      environment: {
        WSCONNECTIONS_TABLE_NAME: websocketSessionsTable.tableName,
        GAME_TABLE_NAME: draftTable.tableName,
        DRAFT_QUEUE_URL: draftQueue.queueUrl,
        WEBSOCKET_CALLBACK_URL_PARAM_NAME: WEBSOCKET_CALLBACK_URL_PARAM_NAME,
      },
      timeout: cdk.Duration.seconds(28), // must be less than queue visibility timeout (30s)
      memorySize: 512,
      code: lambda.Code.fromAsset(join(__dirname,
        '../resources/monster-draft-handlers/main-draft-handler/target/main-draft-handler.jar')),
      snapStart: lambda.SnapStartConf.ON_PUBLISHED_VERSIONS,
    });
    websocketSessionsTable.grantReadWriteData(mainDraftHandler);
    draftTable.grantReadWriteData(mainDraftHandler);
    draftQueue.grantConsumeMessages(mainDraftHandler);
    draftQueue.grantSendMessages(mainDraftHandler); // needed for self-requeue with delayed visibility

    // MainDraftHandler also needs execute-api:ManageConnections to push messages back to WebSocket
    // clients, scoped to the specific mainDraftApi. That API is created in the API stack, which
    // already depends on this stack for mainDraftHandlerAlias below — so the permission is granted
    // there instead, via a standalone iam.Policy attached to mainDraftHandlerAlias.role. Granting it
    // here (e.g. via .grant()/.addToRolePolicy()) would attach to this function's own default role
    // policy, which lives in this stack, and would force this stack to import the API's ARN from the
    // API stack — a circular stack dependency.

    // Lets MainDraftHandler read its WebSocket callback URL from SSM (see WebSocketEndpointResource
    // in main-draft-handler and the SSM parameter published in the API stack). This grant, unlike
    // the one above, CAN be made here without creating a cycle: the parameter's ARN is built purely
    // from the hardcoded name in constants.ts, not from any API-stack-generated token.
    mainDraftHandler.addToRolePolicy(new iam.PolicyStatement({
      actions: ['ssm:GetParameter'],
      resources: [`arn:aws:ssm:${this.region}:${this.account}:parameter${WEBSOCKET_CALLBACK_URL_PARAM_NAME}`],
    }));

    const mainDraftHandlerVersion = mainDraftHandler.currentVersion;
    this.mainDraftHandlerAlias = new lambda.Alias(this, 'MainDraftHandlerAlias', {
      aliasName: 'MainDraftHandlerAlias',
      version: mainDraftHandlerVersion,
    });

    // Attach the SQS event source to the alias so that SnapStart applies.
    // SnapStart only activates on aliased invocations, not on the function directly.
    this.mainDraftHandlerAlias.addEventSource(new lambdaEventSources.SqsEventSource(draftQueue, {
      batchSize: 10,
      reportBatchItemFailures: true, // pairs with SQSBatchResponse return type
    }));
  }
}