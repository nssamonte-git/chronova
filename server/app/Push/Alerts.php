<?php

namespace App\Push;

use App\Jobs\SendAlert;
use App\Models\Device;
use App\Models\User;
use Illuminate\Contracts\Cache\Repository as Cache;
use Laravel\Sanctum\PersonalAccessToken;

/**
 * The account and sync alerts (docs/push.md): data messages with only a `type`, worded by the phone.
 * - `sync_available`: after a sync that changed something, to the user's other devices, at most
 *   once per device every 10 minutes (queued);
 * - `signed_in`: a new sign-in, to the devices already signed in (queued);
 * - `account_changed`: sent at once, before the sign-ins are revoked (a password reset, the
 *   account deleted, staff signing the user out everywhere), since the devices go with them.
 */
final class Alerts
{
    public const SYNC_AVAILABLE = 'sync_available';

    public const SIGNED_IN = 'signed_in';

    public const ACCOUNT_CHANGED = 'account_changed';

    public const SYNC_EVERY_SECONDS = 600;

    public function __construct(private readonly Pusher $pusher, private readonly Cache $cache) {}

    public function syncAvailable(User $user, ?int $exceptSessionId): void
    {
        $this->queue($user, self::SYNC_AVAILABLE, $exceptSessionId);
    }

    public function signedIn(User $user, int $newSessionId): void
    {
        $this->queue($user, self::SIGNED_IN, $newSessionId);
    }

    public function accountChanged(User $user, ?int $exceptSessionId = null): void
    {
        $this->deliver($user->getKey(), self::ACCOUNT_CHANGED, $exceptSessionId);
    }

    /** The sign-in a request was made with, to leave out of its own alerts. */
    public static function sessionOf(User $user): ?int
    {
        $token = $user->currentAccessToken();

        return $token instanceof PersonalAccessToken ? $token->getKey() : null;
    }

    /** @return int how many were sent */
    public function deliver(string $userId, string $type, ?int $exceptSessionId): int
    {
        $devices = Device::query()
            ->where('user_id', $userId)
            ->when($exceptSessionId !== null, fn ($q) => $q->where('personal_access_token_id', '!=', $exceptSessionId))
            ->get();
        $sync = $type === self::SYNC_AVAILABLE;
        if ($sync) {
            $devices = $devices->filter(fn (Device $device) => $this->cache->add('push.sync_available.'.$device->id, true, self::SYNC_EVERY_SECONDS));
        }
        if ($devices->isEmpty()) {
            return 0;
        }

        return $this->pusher->send($devices, ['type' => $type], urgent: ! $sync, collapseKey: $sync ? 'sync' : null);
    }

    private function queue(User $user, string $type, ?int $exceptSessionId): void
    {
        // Most accounts have one device: no job when no other device could get it.
        $others = Device::query()->where('user_id', $user->getKey())
            ->when($exceptSessionId !== null, fn ($q) => $q->where('personal_access_token_id', '!=', $exceptSessionId))
            ->exists();
        if ($others) {
            SendAlert::dispatch($user->getKey(), $type, $exceptSessionId);
        }
    }
}
