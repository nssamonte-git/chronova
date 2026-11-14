#!/usr/bin/env node
import { App } from 'aws-cdk-lib';
import { ChronovaStack } from '../lib/chronova-stack';

const app = new App();
const stage: string = app.node.tryGetContext('stage') ?? 'dev';
if (!/^[a-z][a-z0-9-]{0,15}$/.test(stage)) throw new Error(`Invalid stage: ${stage}`);
const mailFrom: string | undefined = app.node.tryGetContext('mailFrom');
if (!mailFrom || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(mailFrom)) {
  throw new Error('Set the sender address: -c mailFrom=no-reply@your-domain.com');
}

const mailIdentity: string = app.node.tryGetContext('mailIdentity') ?? 'address';
if (mailIdentity !== 'address' && mailIdentity !== 'domain') {
  throw new Error(`Invalid mailIdentity: ${mailIdentity} (use -c mailIdentity=domain or -c mailIdentity=address)`);
}

const voice: string = app.node.tryGetContext('voice') ?? 'off';
if (voice !== 'on' && voice !== 'off') throw new Error(`Invalid voice: ${voice} (use -c voice=on or -c voice=off)`);
const push: string = app.node.tryGetContext('push') ?? 'off';
if (push !== 'on' && push !== 'off') throw new Error(`Invalid push: ${push} (use -c push=on or -c push=off)`);
const imports: string = app.node.tryGetContext('import') ?? 'off';
if (imports !== 'on' && imports !== 'off') throw new Error(`Invalid import: ${imports} (use -c import=on or -c import=off)`);

new ChronovaStack(app, `Chronova-${stage}`, {
  stage,
  mailFrom,
  mailDomain: mailIdentity === 'domain',
  voice: voice === 'on',
  push: push === 'on',
  import: imports === 'on',
  alarmEmail: app.node.tryGetContext('alarmEmail'),
  // Account and region come from the deploying credentials/profile.
  env: { account: process.env.CDK_DEFAULT_ACCOUNT, region: process.env.CDK_DEFAULT_REGION },
});
