<?php

use App\Auth\EmailCodes;
use App\Filament\Resources\Users\Pages\ViewUser;
use App\Jobs\SendAlert;
use App\Models\Device;
use App\Models\EmailCode;
use App\Models\User;
use App\Push\PushSender;
use Filament\Facades\Filament;
use Illuminate\Support\Facades\Mail;
use Illuminate\Support\Facades\Queue;
use Livewire\Livewire;

beforeEach(function () {
    Mail::fake();
    $this->sender = app(PushSender::class);
    $this->ana = User::factory()->create(['email' => 'ana@example.com']);
});

/** Signs $user in on a phone with push token $fcm; returns that sign-in's bearer token. */
function phoneOf(User $user, string $fcm): string
{
    $session = $user->createToken('android', ['sync'], now()->addDays(60));
    Device::create([
        'user_id' => $user->getKey(), 'personal_access_token_id' => $session->accessToken->getKey(), 'token' => $fcm,
        'platform' => 'android', 'time_zone' => 'UTC', 'reminder_lead' => 'off', 'last_seen_at' => now(),
    ]);

    return $session->plainTextToken;
}

/** The pushes sent so far, as "fcm token: type". */
function alertsSent(): array
{
    return array_map(fn ($m) => $m['token'].': '.$m['data']['type'], test()->sender->sent);
}

function syncChange(string $session, string $title): void
{
    app('auth')->forgetGuards();
    test()->withToken($session)->postJson('/v1/sync', ['cursor' => '0', 'changes' => [[
        'entity' => 'project', 'id' => 'p1', 'deleted' => false, 'updatedAt' => (int) (microtime(true) * 1000),
        'data' => ['name' => $title, 'startDate' => '2026-03-02', 'endDate' => '2026-03-04'],
    ]]])->assertOk();
}

it('tells the devices already signed in about a new sign-in, and no one else', function () {
    phoneOf($this->ana, 'ana-phone');
    phoneOf(User::factory()->create(), 'someone-else');

    $this->postJson('/v1/auth/login', ['email' => 'ana@example.com', 'password' => 'Correct-horse-1'])->assertOk();

    expect(alertsSent())->toBe(['ana-phone: signed_in'])
        ->and($this->sender->sent[0])->toMatchArray(['urgent' => true, 'collapseKey' => null, 'data' => ['type' => 'signed_in']]);
});

it('queues no alert when there is no other device', function () {
    Queue::fake();
    $this->postJson('/v1/auth/login', ['email' => 'ana@example.com', 'password' => 'Correct-horse-1'])->assertOk();
    Queue::assertNothingPushed();

    phoneOf($this->ana, 'ana-phone');
    $this->postJson('/v1/auth/login', ['email' => 'ana@example.com', 'password' => 'Correct-horse-1'])->assertOk();
    Queue::assertPushed(SendAlert::class, fn (SendAlert $job) => $job->type === 'signed_in' && $job->userId === $this->ana->id);
});

it('tells the other devices that a sync changed something, at most every ten minutes each', function () {
    $phone = phoneOf($this->ana, 'ana-phone');
    phoneOf($this->ana, 'ana-tablet');

    syncChange($phone, 'Launch');
    expect(alertsSent())->toBe(['ana-tablet: sync_available'])
        ->and($this->sender->sent[0])->toMatchArray(['urgent' => false, 'collapseKey' => 'sync']);

    $this->travel(5)->minutes();
    syncChange($phone, 'Launch 2');
    expect(alertsSent())->toHaveCount(1);

    $this->travel(6)->minutes();
    syncChange($phone, 'Launch 3');
    expect(alertsSent())->toBe(['ana-tablet: sync_available', 'ana-tablet: sync_available']);
});

it('sends nothing for a sync that changed nothing', function () {
    $phone = phoneOf($this->ana, 'ana-phone');
    phoneOf($this->ana, 'ana-tablet');

    app('auth')->forgetGuards();
    $this->withToken($phone)->postJson('/v1/sync', ['cursor' => '0', 'changes' => []])->assertOk();
    // Older than the server's copy: stale, not applied.
    syncChange($phone, 'Launch');
    $this->sender->sent = [];
    $this->travel(11)->minutes();
    app('auth')->forgetGuards();
    $this->withToken($phone)->postJson('/v1/sync', ['cursor' => '0', 'changes' => [[
        'entity' => 'project', 'id' => 'p1', 'deleted' => false, 'updatedAt' => 1,
        'data' => ['name' => 'Old', 'startDate' => '2026-03-02', 'endDate' => '2026-03-04'],
    ]]])->assertOk()->assertJsonPath('results.0.status', 'stale');

    expect(alertsSent())->toBe([]);
});

it('tells every device about a password reset before signing them out', function () {
    phoneOf($this->ana, 'ana-phone');
    phoneOf($this->ana, 'ana-tablet');
    $code = app(EmailCodes::class)->issue($this->ana, EmailCode::PURPOSE_RESET);

    $this->postJson('/v1/auth/reset', ['email' => 'ana@example.com', 'code' => $code, 'password' => 'New-password-42'])->assertOk();

    expect(alertsSent())->toBe(['ana-phone: account_changed', 'ana-tablet: account_changed'])
        ->and($this->sender->sent[0])->toMatchArray(['urgent' => true])
        ->and(Device::count())->toBe(0);
});

it('tells the other devices when the account is deleted from one of them', function () {
    $phone = phoneOf($this->ana, 'ana-phone');
    phoneOf($this->ana, 'ana-tablet');

    $this->withToken($phone)->deleteJson('/v1/account')->assertNoContent();

    expect(alertsSent())->toBe(['ana-tablet: account_changed'])->and(Device::count())->toBe(0);
});

it('tells every device when support signs the user out everywhere', function () {
    phoneOf($this->ana, 'ana-phone');
    Filament::setCurrentPanel('admin');
    $this->actingAs(staffMember(), 'staff');

    Livewire::test(ViewUser::class, ['record' => $this->ana->getRouteKey()])->callAction('signOutEverywhere');

    expect(alertsSent())->toBe(['ana-phone: account_changed'])->and(Device::count())->toBe(0);
});
