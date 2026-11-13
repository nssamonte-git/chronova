# Deploying the Chronova server to AWS

Infrastructure as code (CDK, TypeScript) for the Laravel server in `../`. Nothing here deploys on its own; every step below is run by a person with AWS credentials.

```
phone / browser ──HTTPS──▶ CloudFront ──VPC origin (private)──▶ internal ALB ──▶ ECS Fargate "web"
                                                                ECS Fargate "jobs" (queue worker + scheduler)
                                                                RDS Postgres 16 (isolated subnets)
```

- **No custom domain needed**: the app is served at `https://<id>.cloudfront.net`.
- The load balancer is **internal**; only CloudFront reaches it (VPC origin), so the app trusts `CloudFront-Viewer-Address` for client IPs (rate limits, audit log).
- **No NAT gateway**: tasks run in public subnets so they can pull the image and call AWS APIs, but their security group only accepts the load balancer.
- **Secrets** (`APP_KEY`, database credentials) are generated into Secrets Manager and injected at start. Email goes through **SES** with the task role.
- **Voice editing** is off until you turn it on (`-c voice=on`, below). Its provider keys live in a `VoiceKeys` secret that only the web container can read.
- **Push notifications** (Firebase Cloud Messaging) are off until you turn them on (`-c push=on`, below). The service-account key lives in a `PushKey` secret that the web, worker and scheduler containers read.
- `dev` deletes the database with the stack; `prod` keeps it (`Retain`), runs Multi-AZ, enables deletion protection and keeps 14 days of backups.

## Cost (rough, us-east-1, before traffic)

Web task (0.5 vCPU, 1 GB, ARM) ≈ $15 · jobs task (0.25 vCPU, 0.5 GB) ≈ $7 · RDS db.t4g.micro + 20 GB ≈ $15 · internal ALB ≈ $17 · public IPv4 for 2 tasks ≈ $7 · CloudFront, Secrets Manager, CloudWatch ≈ a few dollars. **≈ $60–70 / month for dev**; prod with Multi-AZ adds ≈ $13.

## Prerequisites

AWS CLI v2 with a profile for the target account, Node 20+, Docker (the image is built for ARM64 during deploy), and the [Session Manager plugin](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html) for `execute-command`.

```bash
cd server/infra
npm ci
npm test                      # infrastructure assertions
npx cdk synth -c mailFrom=no-reply@your-domain.com
```

## First deploy

```bash
npx cdk bootstrap --profile chronova                                     # once per account/region
npx cdk deploy -c stage=dev -c mailFrom=no-reply@your-domain.com \
  [-c alarmEmail=ops@your-domain.com] --profile chronova
```

Outputs: `ApiUrl` (put it in the app's `Chronova - Android App/local.properties` as `chronova.apiUrl`), `AdminUrl`, `ClusterName`, `MigrateTaskDefinition`, `TaskSubnets`, `TaskSecurityGroup`, `JobsService`, `PushKeySecret`, `VoiceKeysSecret`.

1. **Verify the sender**: SES emails `mailFrom` a verification link — click it. New accounts start in the SES *sandbox* (mail only to verified addresses); request production access in the SES console before real users sign up.
2. **Run migrations** (also after every deploy that adds one):

   ```bash
   aws ecs run-task --profile chronova --cluster <ClusterName> --launch-type FARGATE \
     --task-definition <MigrateTaskDefinition> \
     --network-configuration "awsvpcConfiguration={subnets=[<TaskSubnets>],securityGroups=[<TaskSecurityGroup>],assignPublicIp=ENABLED}"
   ```

3. **Create the first admin** (interactive; the password is never in the task configuration):

   ```bash
   TASK=$(aws ecs list-tasks --profile chronova --cluster <ClusterName> --service-name <JobsService> --query 'taskArns[0]' --output text)
   aws ecs execute-command --profile chronova --cluster <ClusterName> --task "$TASK" --container worker \
     --interactive --command "php artisan chronova:create-staff you@your-domain.com --name='Your Name' --role=admin"
   ```

   Then open `AdminUrl`, sign in, and enrol an authenticator app.
4. **Smoke test**: `curl -fsS <ApiUrl>/up`, sign up from the app, confirm the code arrives, sync.

## Voice editing

The app's assistant (`../../docs/voice-api.md`) uses OpenAI and ElevenLabs. It is deployed **off** (`VOICE_ENABLED=false`: every voice endpoint answers 503 and the app says it's switched off) until you turn it on:

1. **Put the keys in the secret** (never in code, `cdk.json` or the command history of a shared machine). Read them from a prompt so they stay out of your shell history:

   ```bash
   read -rs OPENAI && read -rs ELEVENLABS
   aws secretsmanager put-secret-value --profile chronova --secret-id <VoiceKeysSecret> \
     --secret-string "$(jq -cn --arg o "$OPENAI" --arg e "$ELEVENLABS" '{openai: $o, elevenlabs: $e}')"
   unset OPENAI ELEVENLABS
   ```

   Use keys limited to what this needs (an OpenAI project key; an ElevenLabs key with speech-to-text and text-to-speech only) and set spending limits at both providers.
2. **Deploy with voice on**: add `-c voice=on` to the `cdk deploy` command (and keep it on later deploys). The web container then gets `OPENAI_API_KEY` and `ELEVENLABS_API_KEY` from the secret; no other container does.
3. **Before real users**: update the privacy policy and the Play Data safety form (recordings go to ElevenLabs; words and task names/times go to OpenAI). The ElevenLabs voice is set (`VOICE_ELEVENLABS_VOICE_ID`, default `OYTbf65OHHFELVut7v2H`); override it only to change the voice.

**Kill switch**: deploy again with `-c voice=off` (a few minutes, while tasks roll). Faster stopgaps: revoke the key at the provider, or lower the per-user limit (`VOICE_REQUESTS_PER_MINUTE`). **Rotating a key**: put the new value in the secret, then force a new deployment of the web service (`aws ecs update-service --force-new-deployment …`) — tasks read secrets only when they start.

**Usage**: the admin dashboard shows 24-hour voice requests, error rate, audio minutes, characters spoken and tokens, and a 14-day chart. For a cost estimate, set `VOICE_PRICE_OPENAI_INPUT`, `VOICE_PRICE_OPENAI_OUTPUT` (per million tokens), `VOICE_PRICE_TRANSCRIPTION_HOUR` and `VOICE_PRICE_SPEECH_1K_CHARS` (dollars) from the providers' current pricing pages in the stack's `environment` — none are assumed. Rough volume per spoken command: ~2–10 s of audio, ~1–2 K model tokens over two calls, ~80–150 characters of speech.

## Push notifications

Task reminders and the account and sync alerts (`../../docs/push.md`) go through Firebase project `chronova-ece3c`. Deployed **off** (`PUSH_SENDER=fake`: pushes are recorded, not sent; the app's own reminder alarm still works) until you turn them on:

1. **Put the service-account key in the secret** (Firebase console → Project settings → Service accounts → Generate new private key; never commit it):
   ```bash
   aws secretsmanager put-secret-value --profile chronova --secret-id <PushKeySecret> \
     --secret-string file://path/to/firebase-service-account.json
   ```
2. **Deploy with push on**: add `-c push=on` to the `cdk deploy` command (and keep it on later deploys). The web, worker and scheduler containers then get `FCM_CREDENTIALS_JSON`; the migrate task does not.
3. **Before real users**: the privacy policy and the Play Data safety form must name Firebase Cloud Messaging (a device token per phone; reminder pushes carry task titles and project names through Google; notes are never sent). Restrict the app's Firebase API key to the package and signing certificate in Google Cloud.

**Turning it off**: deploy again with `-c push=off`. **Rotating the key**: create a new key in Firebase, put it in the secret, force a new deployment of the web and jobs services (tasks read secrets only when they start), then delete the old key in Google Cloud.

## Updates and rollback

`npx cdk deploy …` builds and rolls out a new image; ECS replaces tasks only once the new ones pass `/up`, and the deployment circuit breaker rolls back a failing release automatically. Run the migrate task when a release adds migrations (migrations must stay backward compatible with the previous release while both run).

## Tear down (dev)

```bash
npx cdk destroy -c stage=dev -c mailFrom=no-reply@your-domain.com --profile chronova
```

## Custom domain (later)

With your own domain, add an ACM certificate (us-east-1) and `domainNames` on the distribution: that also enforces TLS 1.2+ (the default `*.cloudfront.net` certificate has a fixed, older TLS policy).
