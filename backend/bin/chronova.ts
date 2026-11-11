#!/usr/bin/env node
import { App } from 'aws-cdk-lib';
import { ChronovaStack } from '../lib/chronova-stack';

const app = new App();
const stage: string = app.node.tryGetContext('stage') ?? 'dev';
if (!/^[a-z][a-z0-9-]{0,15}$/.test(stage)) throw new Error(`Invalid stage: ${stage}`);

new ChronovaStack(app, `Chronova-${stage}`, {
  stage,
  // Account and region come from the deploying credentials/profile.
  env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION },
});
