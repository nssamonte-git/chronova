<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\VoiceEvent;
use App\Voice\Interpreter;
use App\Voice\ProposalValidator;
use App\Voice\ProviderUnavailable;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * Voice editing (docs/voice-api.md). The server only proposes: the app checks
 * every proposal against its own copy of the schedule, previews it and applies
 * it only when the user confirms. The user's words are never stored or logged.
 */
class VoiceController extends Controller
{
    public function interpret(Request $request, Interpreter $interpreter, ProposalValidator $validator): JsonResponse
    {
        $started = hrtime(true);
        if (! config('voice.enabled')) {
            $this->record($request, 'interpret', 503, 'disabled', $started);

            return response()->json(['error' => 'voice_disabled'], 503);
        }

        $limits = config('voice.limits');
        $data = $request->validate([
            'transcript' => ['required', 'string', 'max:'.$limits['transcript_chars']],
            'context' => ['required', 'array'],
            'context.projectId' => ['required', 'string', 'max:100'],
            'context.projectName' => ['required', 'string', 'max:200'],
            'context.startDate' => ['required', 'date_format:Y-m-d'],
            'context.endDate' => ['required', 'date_format:Y-m-d', 'after_or_equal:context.startDate'],
            'context.timeZone' => ['required', 'timezone:all'],
            'context.today' => ['required', 'date_format:Y-m-d'],
            'context.now' => ['required', 'date_format:Y-m-d\TH:i'],
            'context.revision' => ['nullable', 'string', 'max:100'],
            'context.tasks' => ['present', 'array', 'list', 'max:'.$limits['tasks']],
            'context.tasks.*.id' => ['required', 'string', 'max:100', 'distinct'],
            'context.tasks.*.title' => ['required', 'string', 'max:200'],
            'context.tasks.*.start' => ['required', 'date_format:Y-m-d\TH:i'],
            'context.tasks.*.end' => ['required', 'date_format:Y-m-d\TH:i'],
            'context.tasks.*.predecessorIds' => ['present', 'array', 'list', 'max:'.$limits['tasks']],
            'context.tasks.*.predecessorIds.*' => ['string', 'max:100'],
        ]);
        $context = $this->context($data['context']);

        try {
            $interpretation = $interpreter->interpret($data['transcript'], $context);
        } catch (ProviderUnavailable) {
            $this->record($request, 'interpret', 502, 'provider_error', $started);

            return response()->json(['error' => 'provider_unavailable'], 502);
        }

        $proposal = $validator->validate($interpretation->proposal, $context, $limits['operations']);
        $tokens = ['input_tokens' => $interpretation->inputTokens, 'output_tokens' => $interpretation->outputTokens];
        if ($proposal === null) {
            $this->record($request, 'interpret', 502, 'invalid', $started, $tokens);

            return response()->json(['error' => 'invalid_proposal'], 502);
        }

        $this->record($request, 'interpret', 200, $proposal['result'], $started, $tokens);

        return response()->json(['revision' => $context['revision']] + $proposal);
    }

    /** Only the fields the interpreter needs, in a fixed shape. */
    private function context(array $context): array
    {
        return [
            'projectId' => $context['projectId'],
            'projectName' => $context['projectName'],
            'startDate' => $context['startDate'],
            'endDate' => $context['endDate'],
            'timeZone' => $context['timeZone'],
            'today' => $context['today'],
            'now' => $context['now'],
            'revision' => $context['revision'] ?? null,
            'tasks' => array_map(fn (array $task) => [
                'id' => $task['id'],
                'title' => $task['title'],
                'start' => $task['start'],
                'end' => $task['end'],
                'predecessorIds' => array_values($task['predecessorIds']),
            ], $context['tasks']),
        ];
    }

    /** Usage metrics only: counts and timing, never content. */
    private function record(Request $request, string $kind, int $status, string $outcome, int $started, array $extra = []): void
    {
        $event = new VoiceEvent([
            'kind' => $kind,
            'status' => $status,
            'outcome' => $outcome,
            'duration_ms' => (int) ((hrtime(true) - $started) / 1_000_000),
        ] + $extra);
        $event->user_id = $request->user()?->getKey();
        $event->save();
    }
}
