import * as cdk from 'aws-cdk-lib/core';
import * as apigwv2 from 'aws-cdk-lib/aws-apigatewayv2';
import * as lambda from 'aws-cdk-lib/aws-lambda';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as sqs from 'aws-cdk-lib/aws-sqs';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';
import {
  HttpLambdaIntegration,
  WebSocketLambdaIntegration,
  WebSocketAwsIntegration,
  WebSocketMockIntegration,
} from 'aws-cdk-lib/aws-apigatewayv2-integrations';


export interface MonsterDraftPublicAppApiStackProps extends cdk.StackProps {
  createLobbyHandlerDevAlias: lambda.Alias,
  openWebsocketConnectionHandlerDevAlias: lambda.Alias,
  draftQueue: sqs.Queue,
}

export class MonsterDraftPublicAppApiStack extends cdk.Stack {
  public readonly createLobbyDevStage: apigwv2.HttpStage;
  public readonly draftDevStage: apigwv2.WebSocketStage;

  constructor(scope: Construct, id: string, props: MonsterDraftPublicAppApiStackProps) {
    super(scope, id, props);
    const {
      createLobbyHandlerDevAlias,
      openWebsocketConnectionHandlerDevAlias,
      draftQueue,
    } = props;


    // -------------------------------------------------------------------------
    // HTTP API: create lobby
    // -------------------------------------------------------------------------

    const createLobbyApi = new apigwv2.HttpApi(this, 'MonsterCubeDraftCreateLobbyApi', { createDefaultStage: false });
    const createLobbyIntegration = new HttpLambdaIntegration('CreateLobbyIntegration', createLobbyHandlerDevAlias);
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


    // -------------------------------------------------------------------------
    // WebSocket API: main draft
    // -------------------------------------------------------------------------

    // IAM role that allows API Gateway to send messages to the draft queue
    const apigwSqsRole = new iam.Role(this, 'ApigwSqsRole', {
      assumedBy: new iam.ServicePrincipal('apigateway.amazonaws.com'),
      description: 'Allows API Gateway $default route to enqueue messages on the draft SQS queue',
    });
    draftQueue.grantSendMessages(apigwSqsRole);

    // Velocity template for the $default → SQS integration.
    // Produces envelope:
    //   { "source": "APIGW_CLIENT", "item": { "connectionId": "...", "domainName": "...",
    //     "stage": "...", "body": "<escaped raw string>" } }
    // body is kept as a raw escaped string so that MainDraftHandler owns all JSON parsing.
    // domainName + stage let MainDraftHandler build the ApiGatewayManagementApi callback endpoint
    // (https://{domainName}/{stage}) per message without a CDK env var — passing the WebSocket
    // API's endpoint via env var would create a circular dependency between this stack and the
    // Lambda stack (see MonsterDraftPublicAppLambdaStack).
    const defaultRouteRequestTemplate =
      'Action=SendMessage' +
      '&MessageBody={' +
        '"source":"APIGW_CLIENT",' +
        '"item":{' +
          '"connectionId":"$context.connectionId",' +
          '"domainName":"$context.domainName",' +
          '"stage":"$context.stage",' +
          '"body":"$util.escapeJavaScript($input.body)"' +
        '}' +
      '}';

    // Everything but $connect/$disconnect goes through the queue to the MainDraftHandler and is evaluated there; hence
    // the static routeSelectionExpression.
    const mainDraftApi = new apigwv2.WebSocketApi(this, 'MonsterCubeDraftMainDraftApi', {
      routeSelectionExpression: '\\$default',
      connectRouteOptions: {
        integration: new WebSocketLambdaIntegration(
          'DevConnectIntegration',
          openWebsocketConnectionHandlerDevAlias,
        ),
      },
      disconnectRouteOptions: {
        // $disconnect is intentionally a no-op. Session cleanup is handled lazily: stale connection IDs are detected via
        // GoneException when the server tries to push to them, and session records expire via DynamoDB TTL.
        integration: new WebSocketMockIntegration('DevDisconnectIntegration'),
      },
    });

    const defaultRoute = mainDraftApi.addRoute('$default', {
      integration: new WebSocketAwsIntegration('DefaultSqsIntegration', {
        integrationUri: `arn:aws:apigateway:${this.region}:sqs:path/${this.account}/${draftQueue.queueName}`,
        integrationMethod: apigwv2.HttpMethod.POST,
        credentialsRole: apigwSqsRole,
        requestTemplates: {
          'application/json': defaultRouteRequestTemplate,
        },
        requestParameters: {
          'integration.request.header.Content-Type': '\'application/x-www-form-urlencoded\'',
        },
        passthroughBehavior: apigwv2.PassthroughBehavior.NEVER,
        contentHandling: apigwv2.ContentHandling.CONVERT_TO_TEXT,
      }),
      returnResponse: true,
    });

    // Integration response: passes the raw SQS response through to the WebSocket client with no transformation.
    // Required for non-proxy AWS integrations — without this APIGW cannot return any response and gives 500. CDK already
    // adds a CfnRouteResponse when returnResponse == true, so we only need the CfnIntegrationResponse here. The
    // integration ID is read from the CfnRoute child's target, which is of the form "integrations/<id>".
    const defaultCfnRoute = defaultRoute.node.findChild('Resource') as apigwv2.CfnRoute;
    const defaultIntegrationId = cdk.Fn.select(1, cdk.Fn.split('/', defaultCfnRoute.target as string));

    new apigwv2.CfnIntegrationResponse(this, 'DefaultSqsIntegrationResponse', {
      apiId: mainDraftApi.apiId,
      integrationId: defaultIntegrationId,
      integrationResponseKey: '$default',
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
      'integrationErrorMessage=$context.integrationErrorMessage',
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


    // -------------------------------------------------------------------------
    // Outputs
    // -------------------------------------------------------------------------

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
