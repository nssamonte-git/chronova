<?php

namespace App\Push;

use App\Models\Device;
use Illuminate\Support\Facades\Log;

/** Sends a message to devices and forgets those whose token is no longer valid. */
final class Pusher
{
    public function __construct(private readonly PushSender $sender) {}

    /**
     * @param  iterable<Device>  $devices
     * @param  array<string, string>  $data
     * @return int how many were sent
     */
    public function send(iterable $devices, array $data, bool $urgent, ?string $collapseKey = null): int
    {
        $sent = 0;
        $gone = 0;
        $failed = 0;
        foreach ($devices as $device) {
            match ($this->sender->send($device->token, $data, $urgent, $collapseKey)) {
                PushResult::Sent => $sent++,
                PushResult::Unregistered => $gone += (int) (bool) $device->delete(),
                PushResult::Failed => $failed++,
            };
        }
        if ($gone + $failed > 0) {
            Log::info('push: '.($data['type'] ?? 'message').' sent '.$sent.', forgotten '.$gone.', failed '.$failed);
        }

        return $sent;
    }
}
