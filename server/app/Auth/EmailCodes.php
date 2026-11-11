<?php

namespace App\Auth;

use App\Models\EmailCode;
use App\Models\User;

/**
 * Six-digit one-time codes sent by email. Only an HMAC (keyed with the app key) is
 * stored, so a leaked database alone doesn't reveal or brute-force codes. A code
 * expires after 15 minutes or 5 wrong attempts; issuing a new one replaces the old.
 */
class EmailCodes
{
    public const TTL_MINUTES = 15;

    public const MAX_ATTEMPTS = 5;

    /** Creates a new code for the purpose and returns it in plain text (to email it). */
    public function issue(User $user, string $purpose): string
    {
        $code = str_pad((string) random_int(0, 999_999), 6, '0', STR_PAD_LEFT);
        $user->emailCodes()->where('purpose', $purpose)->delete();
        $user->emailCodes()->forceCreate([
            'purpose' => $purpose,
            'code_hash' => $this->hash($code),
            'expires_at' => now()->addMinutes(self::TTL_MINUTES),
            'created_at' => now(),
        ]);

        return $code;
    }

    /** True once for a valid code (which is then used up); every wrong guess counts. */
    public function consume(User $user, string $purpose, string $code): bool
    {
        /** @var EmailCode|null $stored */
        $stored = $user->emailCodes()->where('purpose', $purpose)->latest('id')->first();
        if ($stored === null || $stored->expires_at->isPast() || $stored->attempts >= self::MAX_ATTEMPTS) {
            return false;
        }
        if (! hash_equals($stored->code_hash, $this->hash($code))) {
            $stored->increment('attempts');

            return false;
        }
        $user->emailCodes()->where('purpose', $purpose)->delete();

        return true;
    }

    private function hash(string $code): string
    {
        return hash_hmac('sha256', $code, (string) config('app.key'));
    }
}
