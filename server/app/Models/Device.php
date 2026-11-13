<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * A phone that can be sent push notifications: its Firebase token, time zone and reminder
 * setting. Removed with the sign-in it uses (see the devices migration).
 */
class Device extends Model
{
    use HasUuids;

    /** Reminder settings, as the app names them; "off" sends none. */
    public const LEADS = ['off', 'at_start', '15_min', '1_hour', '1_day'];

    public const PLATFORMS = ['android'];

    protected $fillable = ['user_id', 'personal_access_token_id', 'token', 'platform', 'time_zone', 'reminder_lead', 'last_seen_at'];

    protected $hidden = ['token'];

    protected function casts(): array
    {
        return ['last_seen_at' => 'datetime'];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
