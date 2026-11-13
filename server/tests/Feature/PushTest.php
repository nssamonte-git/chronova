<?php

use App\Models\Device;
use App\Models\User;
use App\Push\FakePushSender;
use App\Push\FcmPushSender;
use App\Push\Pusher;
use App\Push\PushResult;
use App\Push\PushSender;
use Illuminate\Http\Client\Factory;
use Illuminate\Http\Client\Request as ClientRequest;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Testing\TestResponse;

/** A real sign-in token (Sanctum::actingAs has none, and devices hang off the sign-in). */
function signInToken(User $user): string
{
    return $user->createToken('android', ['sync'], now()->addDays(60))->plainTextToken;
}

function registerDevice(string $session, array $overrides = []): TestResponse
{
    app('auth')->forgetGuards();

    return test()->withToken($session)->putJson('/v1/devices', $overrides + [
        'token' => 'fcm-token-1', 'platform' => 'android', 'timeZone' => 'Asia/Manila', 'reminderLead' => '15_min',
    ]);
}

it('registers this sign-in\'s phone and keeps one device per sign-in', function () {
    $user = User::factory()->create();
    $session = signInToken($user);

    registerDevice($session)->assertNoContent();
    expect(Device::sole()->only(['user_id', 'token', 'platform', 'time_zone', 'reminder_lead']))
        ->toBe(['user_id' => $user->getKey(), 'token' => 'fcm-token-1', 'platform' => 'android', 'time_zone' => 'Asia/Manila', 'reminder_lead' => '15_min']);

    // A changed setting updates it; a new Firebase token for the same sign-in replaces it.
    registerDevice($session, ['reminderLead' => 'off', 'timeZone' => '+08:00'])->assertNoContent();
    expect(Device::sole()->only(['reminder_lead', 'time_zone']))->toBe(['reminder_lead' => 'off', 'time_zone' => '+08:00']);
    registerDevice($session, ['token' => 'fcm-token-2'])->assertNoContent();
    expect(Device::pluck('token')->all())->toBe(['fcm-token-2']);

    // Another sign-in (a second phone) is a second device.
    registerDevice(signInToken($user), ['token' => 'fcm-token-3'])->assertNoContent();
    expect(Device::count())->toBe(2);
});

it('moves a token to whoever signed in on that phone last', function () {
    registerDevice(signInToken(User::factory()->create()))->assertNoContent();
    $other = User::factory()->create();
    registerDevice(signInToken($other))->assertNoContent();
    expect(Device::sole()->user_id)->toBe($other->getKey());
});

it('validates the registration', function (array $body) {
    registerDevice(signInToken(User::factory()->create()), $body)->assertUnprocessable();
    expect(Device::count())->toBe(0);
})->with([
    'no token' => [['token' => '']],
    'token too long' => [['token' => str_repeat('a', 513)]],
    'unknown platform' => [['platform' => 'symbian']],
    'bad time zone' => [['timeZone' => 'Mars/Olympus']],
    'unknown reminder setting' => [['reminderLead' => '2_hours']],
]);

it('needs a signed-in user', function () {
    $this->putJson('/v1/devices', ['token' => 'x', 'platform' => 'android', 'timeZone' => 'UTC', 'reminderLead' => 'off'])->assertUnauthorized();
});

it('forgets the phone when it says so, or when its sign-in ends', function () {
    $user = User::factory()->create();
    $first = signInToken($user);
    $second = signInToken($user);
    registerDevice($first)->assertNoContent();
    registerDevice($second, ['token' => 'fcm-token-2'])->assertNoContent();

    app('auth')->forgetGuards();
    $this->withToken($first)->deleteJson('/v1/devices')->assertNoContent();
    expect(Device::pluck('token')->all())->toBe(['fcm-token-2']);

    // Signing out removes the sign-in, and the device with it.
    app('auth')->forgetGuards();
    $this->withToken($second)->postJson('/v1/auth/logout')->assertNoContent();
    expect(Device::count())->toBe(0);
});

it('forgets the phones of expired sign-ins and deleted accounts', function () {
    $user = User::factory()->create();
    registerDevice(signInToken($user))->assertNoContent();
    $user->tokens()->update(['expires_at' => now()->subDays(3)]);
    Artisan::call('sanctum:prune-expired', ['--hours' => 24]);
    expect(Device::count())->toBe(0);

    $session = signInToken($user);
    registerDevice($session)->assertNoContent();
    app('auth')->forgetGuards();
    $this->withToken($session)->deleteJson('/v1/account')->assertNoContent();
    expect(Device::count())->toBe(0);
});

it('uses the fake sender unless Firebase is configured', function () {
    expect(app(PushSender::class))->toBeInstanceOf(FakePushSender::class);
    config(['push.sender' => 'fcm']);
    app()->forgetInstance(PushSender::class);
    expect(app(PushSender::class))->toBeInstanceOf(FcmPushSender::class);
});

/** A throwaway service account with a fresh RSA key, and its public key to check signatures. */
function serviceAccount(): array
{
    $key = openssl_pkey_new(['private_key_bits' => 2048, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
    openssl_pkey_export($key, $private);

    return [
        'json' => json_encode([
            'type' => 'service_account', 'project_id' => 'chronova-test', 'client_email' => 'push@chronova-test.iam.gserviceaccount.com',
            'private_key' => $private, 'token_uri' => 'https://oauth2.googleapis.com/token',
        ]),
        'public' => openssl_pkey_get_details($key)['key'],
    ];
}

function fcmSender(string $credentials): FcmPushSender
{
    return new FcmPushSender(app(Factory::class), Cache::store(), [
        'credentials' => null, 'credentials_json' => $credentials, 'base_url' => 'https://fcm.googleapis.com/v1', 'timeout' => 5,
    ]);
}

it('signs in to Google with the service account once, and sends data messages through FCM', function () {
    $account = serviceAccount();
    Http::fake([
        'oauth2.googleapis.com/*' => Http::response(['access_token' => 'ya29.test', 'expires_in' => 3599]),
        'fcm.googleapis.com/*' => Http::response(['name' => 'projects/chronova-test/messages/1']),
    ]);
    $sender = fcmSender($account['json']);

    expect($sender->send('device-token', ['type' => 'reminder', 'taskId' => 't1'], urgent: true))->toBe(PushResult::Sent)
        ->and($sender->send('device-token', ['type' => 'sync_available'], urgent: false, collapseKey: 'sync'))->toBe(PushResult::Sent);

    // One token exchange for both: a JWT the service account signed, for the messaging scope.
    $exchanges = Http::recorded(fn (ClientRequest $r) => str_contains($r->url(), 'oauth2.googleapis.com'));
    expect($exchanges)->toHaveCount(1);
    [$request] = $exchanges[0];
    [$header, $claims, $signature] = explode('.', $request->data()['assertion']);
    $decode = fn (string $part) => base64_decode(strtr($part, '-_', '+/'));
    expect(json_decode($decode($header), true))->toBe(['alg' => 'RS256', 'typ' => 'JWT'])
        ->and(json_decode($decode($claims), true))->toMatchArray([
            'iss' => 'push@chronova-test.iam.gserviceaccount.com',
            'scope' => 'https://www.googleapis.com/auth/firebase.messaging',
            'aud' => 'https://oauth2.googleapis.com/token',
        ])
        ->and(openssl_verify("$header.$claims", $decode($signature), $account['public'], OPENSSL_ALGO_SHA256))->toBe(1)
        ->and($request->data()['grant_type'])->toBe('urn:ietf:params:oauth:grant-type:jwt-bearer');

    Http::assertSent(fn (ClientRequest $r) => $r->url() === 'https://fcm.googleapis.com/v1/projects/chronova-test/messages:send'
        && $r->hasHeader('Authorization', 'Bearer ya29.test')
        && $r->data() === ['message' => ['token' => 'device-token', 'data' => ['type' => 'reminder', 'taskId' => 't1'], 'android' => ['priority' => 'HIGH', 'ttl' => '86400s']]]);
    Http::assertSent(fn (ClientRequest $r) => str_contains($r->url(), 'messages:send')
        && ($r->data()['message']['android'] ?? null) === ['priority' => 'NORMAL', 'ttl' => '86400s', 'collapse_key' => 'sync']);
});

it('forgets devices whose token Firebase no longer knows, and keeps them on other failures', function () {
    $user = User::factory()->create();
    registerDevice(signInToken($user), ['token' => 'gone'])->assertNoContent();
    registerDevice(signInToken($user), ['token' => 'flaky'])->assertNoContent();
    $account = serviceAccount();
    Http::fake([
        'oauth2.googleapis.com/*' => Http::response(['access_token' => 'ya29.test', 'expires_in' => 3599]),
        'fcm.googleapis.com/*' => function (ClientRequest $r) {
            return $r->data()['message']['token'] === 'gone'
                ? Http::response(['error' => ['status' => 'NOT_FOUND', 'details' => [['errorCode' => 'UNREGISTERED']]]], 404)
                : Http::response(['error' => ['status' => 'UNAVAILABLE']], 503);
        },
    ]);

    $sent = (new Pusher(fcmSender($account['json'])))->send(Device::all(), ['type' => 'signed_in'], urgent: true);

    expect($sent)->toBe(0)->and(Device::pluck('token')->all())->toBe(['flaky']);
});

it('fails safely without usable credentials', function () {
    Http::fake();
    expect(fcmSender('not json')->send('t', ['type' => 'x'], urgent: true))->toBe(PushResult::Failed);
    Http::assertNothingSent();
});
