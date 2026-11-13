<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\SyncEvent;
use App\Push\Alerts;
use App\Sync\SyncRequestParser;
use App\Sync\SyncService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/** POST /v1/sync (docs/sync-protocol.md). */
class SyncController extends Controller
{
    public function __construct(
        private readonly SyncRequestParser $parser,
        private readonly SyncService $sync,
        private readonly Alerts $alerts,
    ) {}

    public function __invoke(Request $request): JsonResponse
    {
        $started = hrtime(true);
        $user = $request->user();
        // Read the raw body: global input middleware (trimming, empty → null) must not alter synced data.
        $parsed = $this->parser->parse(json_decode($request->getContent(), true));

        if (isset($parsed['error'])) {
            $this->record($user->getKey(), 400, 0, 0, 0, $started);

            return response()->json(['error' => $parsed['error']], 400);
        }

        $result = $this->sync->sync($user, $parsed, now()->getTimestampMs());
        $this->record($user->getKey(), 200, count($parsed['changes']), count($result['response']['changes']), $result['stale'], $started);
        if (count($parsed['changes']) > $result['stale']) {
            // Something changed: the user's other devices can sync it.
            $this->alerts->syncAvailable($user, Alerts::sessionOf($user));
        }

        return response()->json($result['response']);
    }

    /** Health metrics only: counts and timing, never content. */
    private function record(string $userId, int $status, int $pushed, int $pulled, int $stale, int $started): void
    {
        $event = new SyncEvent([
            'status' => $status,
            'pushed' => $pushed,
            'pulled' => $pulled,
            'stale' => $stale,
            'duration_ms' => (int) ((hrtime(true) - $started) / 1_000_000),
        ]);
        $event->user_id = $userId;
        $event->save();
    }
}
