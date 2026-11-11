<?php

use App\Auth\EmailCodes;
use App\Mail\EmailCodeMail;
use App\Models\Dependency;
use App\Models\EmailCode;
use App\Models\Project;
use App\Models\SyncEvent;
use App\Models\Task;
use App\Models\User;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Mail;
use Laravel\Sanctum\PersonalAccessToken;

beforeEach(fn () => Mail::fake());

function resetCode(): string
{
    $mail = Mail::queued(EmailCodeMail::class)->last();
    expect($mail->purpose)->toBe(EmailCode::PURPOSE_RESET);

    return $mail->code;
}

describe('password reset', function () {
    it('answers the same whether or not the account exists', function () {
        User::factory()->create(['email' => 'ana@example.com']);
        $this->postJson('/v1/auth/forgot', ['email' => 'ana@example.com'])
            ->assertStatus(202)->assertExactJson(['status' => 'reset_code_sent']);
        $this->postJson('/v1/auth/forgot', ['email' => 'nobody@example.com'])
            ->assertStatus(202)->assertExactJson(['status' => 'reset_code_sent']);
        Mail::assertQueuedCount(1);
    });

    it('sets the new password, revokes every token and signs in', function () {
        $user = User::factory()->create(['email' => 'ana@example.com']);
        $old = $user->createToken('old phone', ['sync'])->plainTextToken;
        $this->postJson('/v1/auth/forgot', ['email' => 'ana@example.com']);

        $this->postJson('/v1/auth/reset', ['email' => 'ana@example.com', 'code' => resetCode(), 'password' => 'New-password-42'])
            ->assertOk()->assertJsonStructure(['token']);

        expect(Hash::check('New-password-42', $user->fresh()->password))->toBeTrue()
            ->and($user->tokens()->count())->toBe(1); // only the new one
        app('auth')->forgetGuards();
        $this->withToken($old)->getJson('/v1/me')->assertUnauthorized();
    });

    it('rejects a wrong code, a weak password and a verification code', function () {
        $user = User::factory()->unverified()->create(['email' => 'ana@example.com']);
        $this->postJson('/v1/auth/forgot', ['email' => 'ana@example.com']);
        $code = resetCode();

        $this->postJson('/v1/auth/reset', ['email' => 'ana@example.com', 'code' => $code, 'password' => 'weak'])
            ->assertUnprocessable()->assertJsonValidationErrors('password');
        $wrong = $code === '000000' ? '111111' : '000000';
        $this->postJson('/v1/auth/reset', ['email' => 'ana@example.com', 'code' => $wrong, 'password' => 'New-password-42'])
            ->assertStatus(422)->assertExactJson(['error' => 'invalid_code']);

        // A code issued for email verification can't reset a password.
        $verifyCode = app(EmailCodes::class)->issue($user, EmailCode::PURPOSE_VERIFY);
        $this->postJson('/v1/auth/reset', ['email' => 'ana@example.com', 'code' => $verifyCode, 'password' => 'New-password-42'])
            ->assertStatus(422);
    });

    it('confirms an unverified email, since the code proves the inbox', function () {
        $user = User::factory()->unverified()->create(['email' => 'ana@example.com']);
        $this->postJson('/v1/auth/forgot', ['email' => 'ana@example.com']);
        $this->postJson('/v1/auth/reset', ['email' => 'ana@example.com', 'code' => resetCode(), 'password' => 'New-password-42'])->assertOk();
        expect($user->fresh()->email_verified_at)->not->toBeNull();
    });

    it('does nothing for disabled accounts', function () {
        User::factory()->disabled()->create(['email' => 'ana@example.com']);
        $this->postJson('/v1/auth/forgot', ['email' => 'ana@example.com'])->assertStatus(202);
        Mail::assertNothingQueued();
    });
});

describe('delete account', function () {
    it('removes the user, every synced record and every token, and keeps anonymous metrics', function () {
        $user = User::factory()->create();
        $other = User::factory()->create();
        Project::factory()->for($user)->create();
        Task::factory()->for($user)->count(2)->create();
        Dependency::factory()->for($user)->create();
        Project::factory()->for($other)->create();
        $event = new SyncEvent(['status' => 200, 'duration_ms' => 5]);
        $event->user_id = $user->id;
        $event->save();
        $token = $user->createToken('phone', ['sync'])->plainTextToken;

        $this->withToken($token)->deleteJson('/v1/account')->assertNoContent();

        expect(User::find($user->id))->toBeNull()
            ->and(Project::where('user_id', $user->id)->count() + Task::count() + Dependency::count())->toBe(0)
            ->and(Project::where('user_id', $other->id)->count())->toBe(1)
            ->and(PersonalAccessToken::count())->toBe(0)
            ->and($event->fresh()->user_id)->toBeNull();
    });

    it('requires a valid token', function () {
        $this->deleteJson('/v1/account')->assertUnauthorized();
        expect(User::count())->toBe(0);
    });
});
