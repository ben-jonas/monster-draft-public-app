#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib/core';
import { MonsterDraftPublicAppInfraStack } from '../lib/monster-draft-public-app_infra-stack';

const app = new cdk.App();
new MonsterDraftPublicAppInfraStack(app, 'MonsterDraftPublicAppInfraStack', {
  env: { account: '556789079449', region: 'us-east-1' },
});
