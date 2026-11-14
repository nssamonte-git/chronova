# Chronova EC2 operations

Chronova runs in Sydney (`ap-southeast-2`) on a single EC2 instance. Cloudflare DNS points `chronova.meenux.tech` to the instance, and Caddy provides HTTPS. The Android build uses `https://chronova.meenux.tech` by default (`app/build.gradle.kts`); `chronova.apiUrl` in its local Gradle properties overrides it. Use `/admin` for staff and `/up` for health checks.

## Deployment files

| Path on EC2 | Purpose |
| --- | --- |
| `~/chronova/source` | GitHub checkout on `main`. The server build context is in `source/server`. |
| `~/chronova/compose.yaml` | Services and persistent PostgreSQL volume. |
| `~/chronova/compose.override.yaml` | Existing host overrides. Include it in deploy commands. |
| `~/chronova/compose.secrets.yaml` | Maps shell variables to the services that need them. |
| `~/chronova/app.env` | Laravel settings shared by the application containers. The app key and database password were removed after the Secrets Manager cutover. |

The instance role reads `chronova/prod/runtime` from Secrets Manager in Sydney. `APP_KEY` and `DB_PASSWORD` go to `web`, `worker`, `scheduler`, and `migrate`. The OpenAI, ElevenLabs, and Meta keys go to `web`. `FCM_CREDENTIALS_JSON` goes to `web`, `worker`, and `scheduler`, along with `PUSH_SENDER=fcm`, which overrides `PUSH_SENDER=fake` in `app.env`. Keep the existing Laravel key when redeploying so encrypted data such as staff 2FA secrets remains readable.

## Redeploy a GitHub release

Push the reviewed release to `main`, then run the following as `ec2-user` on EC2. Git pulls as the checkout owner. The deployment uses `sudo` for Compose and fetches the required secrets without printing them.

```bash
cd ~/chronova &&
git -C source status --short &&
test -z "$(git -C source status --porcelain)" &&
git -C source pull --ff-only origin main &&
sudo python3 - <<'PY'
import json
import os
import subprocess

secret = json.loads(subprocess.check_output([
    "aws", "secretsmanager", "get-secret-value", "--region", "ap-southeast-2",
    "--secret-id", "chronova/prod/runtime", "--query", "SecretString",
    "--output", "text", "--no-cli-pager",
], text=True))

env = os.environ.copy()
for key in (
    "APP_KEY", "DB_PASSWORD", "OPENAI_API_KEY", "ELEVENLABS_API_KEY",
    "META_API_KEY", "FCM_CREDENTIALS_JSON",
):
    value = secret.get(key)
    if not isinstance(value, str) or not value.strip():
        raise SystemExit(f"Missing or empty secret entry: {key}")
    env[key] = value

compose = [
    "docker", "compose", "--profile", "public",
    "-f", "compose.yaml", "-f", "compose.override.yaml", "-f", "compose.secrets.yaml",
]
subprocess.run(compose + ["config", "--quiet"], env=env, check=True)
subprocess.run(compose + ["build", "migrate"], env=env, check=True)
subprocess.run(compose + ["up", "-d", "--wait"], env=env, check=True)
PY
```

Run `git -C source log -1 --oneline` and confirm the intended release. Inspect status with `sudo docker compose --profile public ps -a`. The one-shot `migrate` service should exit with code 0. Check `curl -fsS -o /dev/null -w '%{http_code}\n' https://chronova.meenux.tech/up` for `200`, then verify staff sign-in and Android sync. To roll back a code-only release, revert the release commit on `main`, push it, and redeploy. For a release that changes the database schema, check migration compatibility and the restore plan first.

## Client verification

- Voice uses OpenAI for interpretation and explanations and ElevenLabs for transcription and speech. Test one voice command and verify the preview before applying it.
- Firebase is enabled. The three service containers reported `sender=fcm, credential=SET`. Verify delivery using the same test account on two Android devices with notifications allowed. Sync a change from one and check for “Changes on another device” on the other.
- Image import is off. If it is approved and in scope, configure `IMPORT_ENABLED=true`, `IMPORT_ANALYZER=meta`, and `IMPORT_BUILDER=openai`, then verify a sample image preview.
- `MAIL_MAILER=array` keeps mail in memory. Configure a real mail provider and verify registration and password reset before relying on self-service accounts. Confirm a PostgreSQL backup can be restored before storing client data.
