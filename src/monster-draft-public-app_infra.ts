#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib/core';
import { MonsterDraftPublicAppDataStack } from '../lib/monster-draft-public-app_data-stack';
import { MonsterDraftPublicAppLambdaStack } from '../lib/monster-draft-public-app_lambda-stack';
import { MonsterDraftPublicAppApiStack } from '../lib/monster-draft-public-app_api-stack';

const env = { account: '556789079449', region: 'us-east-1' }
const app = new cdk.App();

const dataStack = new MonsterDraftPublicAppDataStack(app, 'MonsterCubeDraftPublicAppDataStack', {env});

const lambdaStack = new MonsterDraftPublicAppLambdaStack(app, 'MonsterCubeDraftPublicAppLambdaStack', {
  env,
  websocketSessionsTable: dataStack.websocketSessionsTable,
  draftTable: dataStack.draftTable,
  draftQueue: dataStack.draftQueue,
});

new MonsterDraftPublicAppApiStack(app, 'MonsterCubeDraftPublicAppApiStack', {
  env,
  createLobbyHandlerDevAlias: lambdaStack.createLobbyHandlerAlias,
  openWebsocketConnectionHandlerDevAlias: lambdaStack.openWebsocketConnectionHandlerAlias,
  draftQueue: dataStack.draftQueue,
});
