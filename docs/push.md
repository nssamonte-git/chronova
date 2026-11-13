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

## Reminders

- **When:** `App\Jobs\SendDueReminders`, every minute on the scheduler (`routes/console.php`, the scheduler container), with a daily clean-up of `sent_reminders` older than 2 days.
- **What:** for each device whose `reminderLead` isn't `off`, the user's tasks and milestones that are synced, not deleted, not done (progress under 100), scheduled, and in a live project, whose reminder time (start minus the lead) came since the last run. The first run looks back 2 minutes; no run looks back more than a day.
- **Once per device:** `sent_reminders` (device, task, reminder time) is unique; a moved task gets a new reminder time and so a new reminder.
- **The message:** `type=reminder`, `taskId`, `projectId`, `projectName`, `title`, `milestone` (`true`/`false`), `startMillis`, `atMillis`; high priority, no collapse key. The phone words it.
- **On the phone:** dropped when signed out, reminders are off, it is over a day late, or the phone's own copy of the task is done or due at another time (an unsynced change; the phone's alarm covers it). Otherwise it is posted through the same poster and notification id as the alarm, and a shared "posted" log (`taskId@atMillis`, 2 days) means push and alarm show it once between them.

## Account and sync alerts

`App\Push\Alerts`; data messages with only a `type`, worded by the phone. The phone acts on them only while signed in (`HandleAlert`).

| Type | Sent when | To | How | On the phone |
|---|---|---|---|---|
| `sync_available` | a sync applied at least one change | the user's other devices (not the caller's sign-in) | queued (`SendAlert`); normal priority, collapse key `sync`; at most once per device every 10 minutes | "Changes on another device" in the shade; tapping it opens the sync prompt. Sync never starts by itself |
| `signed_in` | a sign-in (login, confirming the email, a reset) | the devices already signed in | queued; high priority | "New sign-in … If that wasn't you, reset your password." Skipped when this phone itself signed in within 5 minutes (the server can't yet tell it apart) |
| `account_changed` | a password reset, the account deleted (from the app: not the caller), support's "Sign out everywhere" | the user's devices | at once, before the sign-ins (and so the devices) are revoked; high priority | not trusted alone: the phone calls `GET /v1/me`; only a 401 ends the session (the existing "Session ended" path); a working sign-in or no answer changes nothing |

No job is queued when the user has no other device.

## The app

- **Modules:** `:feature:push:domain` (pure Kotlin: `PushMessage.parse`, `RegisterDevice`) and `:feature:push:data` (`ChronovaMessagingService`, `FirebasePushTokens`, `OkHttpPushApi`). The app module applies the google-services plugin, which reads `app/google-services.json` (Firebase project `chronova-ece3c`).
- **When the device registers:**
  - while signed in, at each start, on sign-in (another account too), when the reminder choice changes, and when Firebase hands over a new token;
  - best effort: a failure waits for the next trigger.
  - Signing out needs nothing: the server forgets the device with the sign-in.
- **Firebase on the phone:**
  - It makes no token until the app asks, so a signed-out app makes none (`firebase_messaging_auto_init_enabled=false`).
  - Analytics is off and not included; only messaging is.
- **Messages:** the service hands them to the Application (`PushHost`); a message the app doesn't understand is ignored.

