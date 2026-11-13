<?php

namespace App\Jobs;

use App\Push\Alerts;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Foundation\Queue\Queueable;

/** Sends a queued account or sync alert (App\Push\Alerts), off the request that caused it. */
final class SendAlert implements ShouldQueue
{
    use Queueable;

    /** A late alert is worth little; a failed send is logged by the pusher. */
    public int $tries = 1;

    public function __construct(
        public readonly string $userId,
        public readonly string $type,
        public readonly ?int $exceptSessionId,
    ) {}

    public function handle(Alerts $alerts): void
    {
        $alerts->deliver($this->userId, $this->type, $this->exceptSessionId);
    }
}
