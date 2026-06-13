import * as cdk from 'aws-cdk-lib/core';
import * as apigwv2 from 'aws-cdk-lib/aws-apigatewayv2';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as logs from 'aws-cdk-lib/aws-logs';
import { Construct } from 'constructs';
import { HttpLambdaIntegration, WebSocketLambdaIntegration } from 'aws-cdk-lib/aws-apigatewayv2-integrations';


export interface MonsterDraftPublicAppApiStackProps extends cdk.StackProps {
  createLobbyHandlerDevAlias: lambda.Alias,
  openWebsocketConnectionHandlerDevAlias: lambda.Alias,
}

export class MonsterDraftPublicAppApiStack extends cdk.Stack {
  public readonly createLobbyDevStage: apigwv2.HttpStage
  public readonly draftDevStage: apigwv2.WebSocketStage
  constructor(scope: Construct, id: string, props: MonsterDraftPublicAppApiStackProps) {
    super(scope, id, props);
    const { createLobbyHandlerDevAlias: createLobbyHandlerAlias, openWebsocketConnectionHandlerDevAlias: openWebsocketConnectionHandlerAlias } = props;


    const createLobbyApi = new apigwv2.HttpApi(this, 'MonsterCubeDraftCreateLobbyApi', {createDefaultStage: false});
    const createLobbyIntegration = new HttpLambdaIntegration('CreateLobbyIntegration', createLobbyHandlerAlias);
    createLobbyApi.addRoutes({
      path: '/createLobby',
      methods: [apigwv2.HttpMethod.PUT],
      integration: createLobbyIntegration
    });

    const _createLobbyDevStage = new apigwv2.HttpStage(this, 'CreateLobbyDevStage', {
      httpApi: createLobbyApi,
      stageName: 'dev',
      autoDeploy: true
    });

    const createLobbyApiLogFormat = [
      'sourceIp=$context.identity.sourceIp',
      'requestId=$context.requestId',
      'requestTime=$context.requestTime',
      'httpMethod=$context.httpMethod',
      'path=$context.path',
      'routeKey=$context.routeKey',
      'status=$context.status',
      'error.message=$context.error.message',
      'error.messageString=$context.error.messageString',
      'authorizer.error=$context.authorizer.error',
      'userAgent=$context.identity.userAgent',
      'responseLatency=$context.responseLatency',
      'integrationErrorMessage=$context.integrationErrorMessage',
    ].join(' ');

    const createLobbyApiDevLogGroup = new logs.LogGroup(this, 'CreateLobbyApiLogGroup');
    // Use an escape hatch to enable logging on the L1 CfnStage
    const createLobbyDevCfnStage = _createLobbyDevStage.node.defaultChild as apigwv2.CfnStage;
    createLobbyDevCfnStage.accessLogSettings = {
      destinationArn: createLobbyApiDevLogGroup.logGroupArn,
      format: createLobbyApiLogFormat
    };


    const mainDraftApi = new apigwv2.WebSocketApi(this, 'MonsterCubeDraftMainDraftApi', {
      connectRouteOptions: {
        integration: new WebSocketLambdaIntegration('DevConnectIntegration', openWebsocketConnectionHandlerAlias)
      },
      defaultRouteOptions: {
        integration: new WebSocketLambdaIntegration('DevDefaultIntegration', openWebsocketConnectionHandlerAlias)
      },
      disconnectRouteOptions: {
        integration: new WebSocketLambdaIntegration('devdisconnectintegration', openWebsocketConnectionHandlerAlias)
      }
    });
    const _draftDevStage = new apigwv2.WebSocketStage(this, 'MainDraftApiDevStage', {
      webSocketApi: mainDraftApi,
      stageName: 'dev',
      autoDeploy: true
    });

    const mainDraftApiLogFormat = [
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

    const mainDraftApiDevLogGroup = new logs.LogGroup(this, 'MainDraftApiLogGroup');
    // Use an escape hatch to enable logging on the L1 CfnStage
    const mainDraftDevCfnStage = _draftDevStage.node.defaultChild as apigwv2.CfnStage;
    mainDraftDevCfnStage.accessLogSettings = {
      destinationArn: mainDraftApiDevLogGroup.logGroupArn,
      format: mainDraftApiLogFormat
    };
    mainDraftDevCfnStage.defaultRouteSettings = {
      loggingLevel: 'INFO',
      dataTraceEnabled: true // Equivalent to 'Log full message data'
    };

    new cdk.CfnOutput(this, 'CreateLobbyApiDevStageUrl', {
      value: _createLobbyDevStage.url
    });
    new cdk.CfnOutput(this, 'MainDraftApiDevStageUrl', {
      value: _draftDevStage.url
    });

    this.createLobbyDevStage = _createLobbyDevStage;
    this.draftDevStage = _draftDevStage;
  }
}