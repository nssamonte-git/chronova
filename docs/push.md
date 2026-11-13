# Push notifications

Task reminders and account and sync alerts reach the phone through Firebase Cloud Messaging (FCM), project `chronova-ece3c`. The server sends **data messages only**; the app builds every notification itself, so channels, wording, tap actions, the reminder setting, the notification permission and duplicate handling stay in one place.

## Devices

| Method | Path | Body | Answer |
|---|---|---|---|
| `PUT` | `/v1/devices` | `token` (the FCM token, ≤ 512), `platform` (`android`), `timeZone` (a zone name or offset), `reminderLead` (`off`/`at_start`/`15_min`/`1_hour`/`1_day`) | 204 |
| `DELETE` | `/v1/devices` | | 204 |

- **Authentication:** both need a signed-in session (`auth:sanctum`, ability `sync`).
- **One device per sign-in:** a new FCM token for the same sign-in replaces the old one. A token that moves to another account (someone else signed in on that phone) moves with it.
- **When a device goes:** it hangs off its sign-in (`personal_access_token_id`, cascade), so signing out, a password reset, the sign-in expiring and deleting the account all remove it. A token FCM reports as unregistered (404 / `UNREGISTERED`) is forgotten when a send fails.

## Sending

- **The sender:** `App\Push\PushSender` is either `FcmPushSender` (HTTP v1) or `FakePushSender` (the default; it records and sends nothing), chosen by `PUSH_SENDER=fcm|fake`.
- **Authorization:** the service account signs a JWT (RS256, PHP's openssl, no extra package), exchanged at Google's token endpoint. The access token is cached for most of its hour.
- **Message settings:** a TTL of 24 hours. Priority is `HIGH` for reminders and account alerts, `NORMAL` with a collapse key for sync.
- **Logging:** outcomes are counted, never tokens or message content.

| Variable | Default | |
|---|---|---|
| `PUSH_SENDER` | `fake` | `fcm` to send through Firebase |
| `FCM_CREDENTIALS` | | Path to the service-account JSON; locally `storage/credentials/firebase-service-account.json` (ignored by git and Docker) |
| `FCM_CREDENTIALS_JSON` | | The same JSON as a string (Secrets Manager → container environment) |
| `FCM_BASE_URL` | `https://fcm.googleapis.com/v1` | |

The service-account key is a private key: never in git, never in the app.
