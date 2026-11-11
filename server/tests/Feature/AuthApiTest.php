<?php

use App\Mail\AlreadyRegisteredMail;
use App\Mail\EmailCodeMail;
use App\Models\User;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Mail;

beforeEach(fn () => Mail::fake());

/** The code from the last queued code email. */
function lastCode(): string
{
    return Mail::queued(EmailCodeMail::class)->last()->code;
}

/** Each request authenticates afresh (the test app otherwise caches the resolved user). */
function freshAuth(): void
{
    app('auth')->forgetGuards();
}

const PASSWORD = 'Correct-horse-1';

describe('register', function () {
    it('creates an unverified account and emails a code', function () {
        $this->postJson('/v1/auth/register', ['email' => ' Ana@Example.com ', 'password' => PASSWORD])
            ->assertStatus(202)->assertExactJson(['status' => 'verification_required']);

        $user = User::sole();
        expect($user->email)->toBe('ana@example.com')
            ->and($user->email_verified_at)->toBeNull()
            ->and(Hash::check(PASSWORD, $user->password))->toBeTrue();
        Mail::assertQueued(EmailCodeMail::class, fn ($mail) => $mail->hasTo('ana@example.com') && preg_match('/^\d{6}$/', $mail->code));
        expect($user->emailCodes()->sole()->code_hash)->not->toBe(lastCode());
    });

    it('enforces the password rules', function () {
        $this->postJson('/v1/auth/register', ['email' => 'ana@example.com', 'password' => 'short'])
            ->assertUnprocessable()->assertJsonValidationErrors('password');
        $this->postJson('/v1/auth/register', ['email' => 'ana@example.com', 'password' => 'alllowercase1'])
            ->assertUnprocessable();
        expect(User::count())->toBe(0);
    });

    it('answers the same for an existing account and only sends a notice', function () {
        $user = User::factory()->create(['email' => 'ana@example.com']);
        $before = $user->password;

        $this->postJson('/v1/auth/register', ['email' => 'ANA@example.com', 'password' => 'Another-pass-2'])
            ->assertStatus(202)->assertExactJson(['status' => 'verification_required']);

        Mail::assertQueued(AlreadyRegisteredMail::class);
        Mail::assertNotQueued(EmailCodeMail::class);
        expect($user->fresh()->password)->toBe($before)->and(User::count())->toBe(1);
    });

    it('lets whoever owns the inbox finish an unverified sign-up', function () {
        User::factory()->unverified()->create(['email' => 'ana@example.com']);
        $this->postJson('/v1/auth/register', ['email' => 'ana@example.com', 'password' => 'Brand-new-pass-3'])->assertStatus(202);
        expect(Hash::check('Brand-new-pass-3', User::sole()->password))->toBeTrue();
        Mail::assertQueued(EmailCodeMail::class);
    });
});

describe('verify', function () {
    beforeEach(fn () => $this->postJson('/v1/auth/register', ['email' => 'ana@example.com', 'password' => PASSWORD]));

    it('confirms the email and returns a working token', function () {
        $response = $this->postJson('/v1/auth/verify', ['email' => 'ana@example.com', 'code' => lastCode()])
            ->assertOk()->assertJsonPath('user.email', 'ana@example.com');

        expect(User::sole()->email_verified_at)->not->toBeNull();
        freshAuth();
        $this->withToken($response->json('token'))->getJson('/v1/me')->assertOk()->assertJsonPath('user.email', 'ana@example.com');
    });

    it('rejects wrong codes and locks after five attempts', function () {
        $code = lastCode();
        $wrong = $code === '000000' ? '111111' : '000000';
        // Reset the request rate limit (the sign-up counted too) to reach the per-code limit.
        $this->app['cache']->flush();
        foreach (range(1, 5) as $_) {
            $this->postJson('/v1/auth/verify', ['email' => 'ana@example.com', 'code' => $wrong])
                ->assertStatus(422)->assertExactJson(['error' => 'invalid_code']);
        }
        $this->app['cache']->flush(); // the auth rate limit is separate from the per-code limit
        $this->postJson('/v1/auth/verify', ['email' => 'ana@example.com', 'code' => $code])->assertStatus(422);
        expect(User::sole()->email_verified_at)->toBeNull();
    });

    it('rejects an expired code', function () {
        $code = lastCode();
        $this->travel(16)->minutes();
        $this->postJson('/v1/auth/verify', ['email' => 'ana@example.com', 'code' => $code])->assertStatus(422);
    });

    it('gives the same answer for unknown emails', function () {
        $this->postJson('/v1/auth/verify', ['email' => 'nobody@example.com', 'code' => '123456'])
            ->assertStatus(422)->assertExactJson(['error' => 'invalid_code']);
    });

    it('replaces the code on resend and stays quiet about unknown emails', function () {
        $first = lastCode();
        $this->postJson('/v1/auth/resend', ['email' => 'ana@example.com'])->assertStatus(202);
        $second = lastCode();
        Mail::assertQueuedCount(2);

        $this->postJson('/v1/auth/resend', ['email' => 'nobody@example.com'])->assertStatus(202);
        Mail::assertQueuedCount(2);

        if ($first !== $second) {
            $this->postJson('/v1/auth/verify', ['email' => 'ana@example.com', 'code' => $first])->assertStatus(422);
        }
        $this->postJson('/v1/auth/verify', ['email' => 'ana@example.com', 'code' => $second])->assertOk();
    });
});

describe('login', function () {
    it('returns a 60-day sync token', function () {
        User::factory()->create(['email' => 'ana@example.com']);
        $this->postJson('/v1/auth/login', ['email' => 'Ana@example.com', 'password' => PASSWORD])
            ->assertOk()->assertJsonStructure(['token', 'user' => ['id', 'email']]);

        $token = User::sole()->tokens()->sole();
        expect($token->abilities)->toBe(['sync'])
            ->and($token->expires_at->diffInDays(now()->addDays(60)))->toBeLessThan(1);
    });

    it('answers the same for a wrong password and an unknown email', function () {
        User::factory()->create(['email' => 'ana@example.com']);
        $this->postJson('/v1/auth/login', ['email' => 'ana@example.com', 'password' => 'Wrong-pass-1'])
            ->assertUnauthorized()->assertExactJson(['error' => 'invalid_credentials']);
        $this->postJson('/v1/auth/login', ['email' => 'nobody@example.com', 'password' => PASSWORD])
            ->assertUnauthorized()->assertExactJson(['error' => 'invalid_credentials']);
    });

    it('asks unverified accounts to confirm and sends a fresh code', function () {
        User::factory()->unverified()->create(['email' => 'ana@example.com']);
        $this->postJson('/v1/auth/login', ['email' => 'ana@example.com', 'password' => PASSWORD])
            ->assertForbidden()->assertExactJson(['error' => 'verification_required']);
        Mail::assertQueued(EmailCodeMail::class);
    });

    it('refuses disabled accounts', function () {
        User::factory()->disabled()->create(['email' => 'ana@example.com']);
        $this->postJson('/v1/auth/login', ['email' => 'ana@example.com', 'password' => PASSWORD])
            ->assertForbidden()->assertExactJson(['error' => 'account_disabled']);
    });

    it('is rate limited per email', function () {
        foreach (range(1, 5) as $_) {
            $this->postJson('/v1/auth/login', ['email' => 'ana@example.com', 'password' => 'x'])->assertUnauthorized();
        }
        $this->postJson('/v1/auth/login', ['email' => 'ana@example.com', 'password' => 'x'])->assertTooManyRequests();
    });
});

describe('tokens', function () {
    it('requires a token', function () {
        $this->getJson('/v1/me')->assertUnauthorized();
    });

    it('answers 401 JSON even when the client sends no Accept header', function () {
        // The app's HTTP client doesn't always ask for JSON; this used to end in a 500.
        $this->post('/v1/sync', [], ['Authorization' => 'Bearer 999|not-a-token'])
            ->assertUnauthorized()->assertHeader('Content-Type', 'application/json');
        $this->call('GET', '/v1/me')->assertUnauthorized();
    });

    it('stops working when revoked, expired, disabled, or lacking the sync ability', function () {
        $user = User::factory()->create();
        $sync = $user->createToken('phone', ['sync'], now()->addDays(60))->plainTextToken;

        $this->withToken($sync)->getJson('/v1/me')->assertOk();

        $user->forceFill(['disabled_at' => now()])->save();
        freshAuth();
        $this->withToken($sync)->getJson('/v1/me')->assertForbidden()->assertExactJson(['error' => 'account_disabled']);
        $user->forceFill(['disabled_at' => null])->save();

        $other = $user->createToken('other', ['something-else'])->plainTextToken;
        freshAuth();
        $this->withToken($other)->getJson('/v1/me')->assertForbidden();

        $this->travel(61)->days();
        freshAuth();
        $this->withToken($sync)->getJson('/v1/me')->assertUnauthorized();
    });

    it('logout revokes only the current token', function () {
        $user = User::factory()->create();
        $phone = $user->createToken('phone', ['sync'])->plainTextToken;
        $tablet = $user->createToken('tablet', ['sync'])->plainTextToken;

        $this->withToken($phone)->postJson('/v1/auth/logout')->assertNoContent();
        freshAuth();
        $this->withToken($phone)->getJson('/v1/me')->assertUnauthorized();
        freshAuth();
        $this->withToken($tablet)->getJson('/v1/me')->assertOk();
    });

    it('is not authenticated by a web session', function () {
        $user = User::factory()->create();
        $this->actingAs($user, 'web');
        freshAuth();
        $this->getJson('/v1/me')->assertUnauthorized();
    });
});
