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

Outputs: `ApiUrl` (put it in the app's `local.properties` as `chronova.apiUrl`), `AdminUrl`, `ClusterName`, `MigrateTaskDefinition`, `TaskSubnets`, `TaskSecurityGroup`, `JobsService`.

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

## Updates and rollback

`npx cdk deploy …` builds and rolls out a new image; ECS replaces tasks only once the new ones pass `/up`, and the deployment circuit breaker rolls back a failing release automatically. Run the migrate task when a release adds migrations (migrations must stay backward compatible with the previous release while both run).

## Tear down (dev)

```bash
npx cdk destroy -c stage=dev -c mailFrom=no-reply@your-domain.com --profile chronova
```

## Custom domain (later)

With your own domain, add an ACM certificate (us-east-1) and `domainNames` on the distribution: that also enforces TLS 1.2+ (the default `*.cloudfront.net` certificate has a fixed, older TLS policy).
