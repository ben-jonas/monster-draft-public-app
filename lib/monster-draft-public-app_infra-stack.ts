import * as cdk from 'aws-cdk-lib/core';
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
    

    const myApi = new apigwv2.WebSocketApi(this, 'mywebsocketapi', {
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
      autoDeploy: true,
    });

    // const connectHandlerDev = new lambda.Function(this, 'devconnectlambda', {
    //   runtime: lambda.Runtime.NODEJS_20_X,
    //   handler: 'index.handler',
    //   code: lambda.Code.fromInline(`
    //     exports.handler = async (event) => {
    //       console.log("Event: ", event);
    //       return { statusCode: 200, body: "devconnectlambda" };
    //     };
    //     `)
    // });

    // const defaultHandlerDev = new lambda.Function(this, 'devdefaultlambda', {
    //   runtime: lambda.Runtime.NODEJS_20_X,
    //   handler: 'index.handler',
    //   code: lambda.Code.fromInline(`
    //     exports.handler = async (event) => {
    //       console.log("Event: ", event);
    //       return { statusCode: 200, body: "devdefaultlambda" };
    //     };
    //     `)
    // })

    // const disconnectHandlerDev = new lambda.Function(this, 'devdisconnectlambda', {
    //   runtime: lambda.Runtime.NODEJS_20_X,
    //   handler: 'index.handler',
    //   code: lambda.Code.fromInline(`
    //     exports.handler = async (event) => {
    //       console.log("Event: ", event);
    //       return { statusCode: 200, body: "devdisconnectlambda!" };
    //     };
    //     `)
    // })

    // Define a CloudFormation output for your URL
    new cdk.CfnOutput(this, "myApiDevStage", {
      value: myApiDevStage.url
    });
  } 
}
