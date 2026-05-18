import * as cdk from 'aws-cdk-lib/core';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as apigwv2 from 'aws-cdk-lib/aws-apigatewayv2';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { join } from 'path';
import { Construct } from 'constructs';
import { WebSocketLambdaIntegration } from 'aws-cdk-lib/aws-apigatewayv2-integrations';

export class MonsterDraftPublicAppInfraStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const wsConnectionsTable = new dynamodb.TableV2(this, 'MonsterDraftWsConnections', {
      partitionKey: {
        name: "sessionId",
        type: dynamodb.AttributeType.STRING
      },
      sortKey: {
        name: "gameRef",
        type: dynamodb.AttributeType.STRING
      },
      billing: dynamodb.Billing.onDemand(),
    });

    const gameTable = new dynamodb.TableV2(this, 'MonsterDraftGames', {
      partitionKey: {
        name: "gameId",
        type: dynamodb.AttributeType.STRING
      },
      billing: dynamodb.Billing.onDemand(),
    });

    const openAndCloseWSockHandler = new lambda.Function(this, "OpenAndCloseWSockHandler", {
      functionName: "monsterdraft-api-openAndCloseWSockHandler",
      runtime: lambda.Runtime.JAVA_25,
      handler: 'org.monstercubedraft.OpenAndCloseWebSocketHandler::handleRequest',
      environment: {
        WSCONNECTIONS_TABLE_NAME: wsConnectionsTable.tableName,
        GAME_TABLE_NAME: gameTable.tableName
      },
      timeout: cdk.Duration.seconds(8),
      memorySize: 256,
      code: lambda.Code.fromAsset(join(__dirname, 
        '../resources/monster-draft-handlers/open-and-close-websock-handler/target/open-and-close-websock-handler.jar')),
      snapStart: lambda.SnapStartConf.ON_PUBLISHED_VERSIONS
    });
    wsConnectionsTable.grantReadWriteData(openAndCloseWSockHandler);
    gameTable.grantReadWriteData(openAndCloseWSockHandler);

    const openAndCloseWSockHandlerVersion = openAndCloseWSockHandler.currentVersion;

    const openAndCloseWSockHandlerAlias = new lambda.Alias(this, 'openAndCloseWSockHandlerAlias',{
      aliasName: 'monsterdraft-api-openAndCloseWSockHandler-Alias',
      version: openAndCloseWSockHandlerVersion
    })

    // Create a log group for the API
    const logGroup = new logs.LogGroup(this, 'MonsterDraftApiLogGroup');

    const myApi = new apigwv2.WebSocketApi(this, 'monsterdraftapi', {
      connectRouteOptions: {
        integration: new WebSocketLambdaIntegration('devconnectintegration', openAndCloseWSockHandlerAlias)
      },
      defaultRouteOptions: {
        integration: new WebSocketLambdaIntegration('devdefaultintegration', openAndCloseWSockHandlerAlias)
      },
      disconnectRouteOptions: {
        integration: new WebSocketLambdaIntegration('devdisconnectintegration', openAndCloseWSockHandlerAlias)
      }
    });

    const myApiDevStage = new apigwv2.WebSocketStage(this, 'devstage', {
      webSocketApi: myApi,
      stageName: 'dev',
      autoDeploy: true
    });

    const wsApiLogFormat = [
      'sourceIp=$context.identity.sourceIp',
      'requestId=$context.requestId',
      'requestTime=$context.requestTime',
      'routeKey=$context.routeKey',
      'status=$context.status',
      'error.message=$context.error.message',
      'error.messageString=$context.error.messageString',
      'authorizer.error=$context.authorizer.error',
      'userAgent=$context.identity.userAgent',
      'connectionId=$context.connectionId',
      'eventType=$context.eventType',
    ].join(' ');

    // Use an escape hatch to enable logging on the L1 CfnStage
    const cfnStage = myApiDevStage.node.defaultChild as apigwv2.CfnStage;
    cfnStage.accessLogSettings = {
      destinationArn: logGroup.logGroupArn,
      format: wsApiLogFormat
    };
    cfnStage.defaultRouteSettings = {
      loggingLevel: 'INFO',
      dataTraceEnabled: true // Equivalent to "Log full message data"
    };

    new cdk.CfnOutput(this, "myApiDevStage", {
      value: myApiDevStage.url
    });
  } 
}
