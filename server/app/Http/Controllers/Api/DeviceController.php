<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Device;
use Closure;
use DateTimeZone;
use Exception;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Validation\Rule;
use Laravel\Sanctum\PersonalAccessToken;

/**
 * The phone's push registration (docs/push.md): its Firebase token, time zone and reminder
 * setting. One device per sign-in: a new token for the same sign-in replaces the old one.
 */
class DeviceController extends Controller
{
    /** PUT /v1/devices */
    public function update(Request $request): Response
    {
        $data = $request->validate([
            'token' => ['required', 'string', 'max:512'],
            'platform' => ['required', Rule::in(Device::PLATFORMS)],
            // Zone names and fixed offsets, as Java and PHP both understand them.
            'timeZone' => ['required', 'string', 'max:64', function (string $attribute, mixed $value, Closure $fail) {
                try {
                    new DateTimeZone($value);
                } catch (Exception) {
                    $fail('The time zone is not valid.');
                }
            }],
            'reminderLead' => ['required', Rule::in(Device::LEADS)],
        ]);
        $session = $this->session($request);
        if ($session === null) {
            abort(403);
        }

        Device::query()->where('personal_access_token_id', $session->getKey())->where('token', '!=', $data['token'])->delete();
        // A token that moved to another account (someone else signed in on that phone) moves with it.
        Device::query()->updateOrCreate(['token' => $data['token']], [
            'user_id' => $request->user()->getKey(),
            'personal_access_token_id' => $session->getKey(),
            'platform' => $data['platform'],
            'time_zone' => $data['timeZone'],
            'reminder_lead' => $data['reminderLead'],
            'last_seen_at' => now(),
        ]);

        return response()->noContent();
    }

    /** DELETE /v1/devices — this sign-in's phone is no longer sent anything. */
    public function destroy(Request $request): Response
    {
        $session = $this->session($request);
        if ($session !== null) {
            Device::query()->where('personal_access_token_id', $session->getKey())->delete();
        }

        return response()->noContent();
    }

    private function session(Request $request): ?PersonalAccessToken
    {
        $token = $request->user()->currentAccessToken();

        return $token instanceof PersonalAccessToken ? $token : null;
    }
}
