<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\VoiceEvent;
use App\Voice\ChangeList;
use App\Voice\Explainer;
use App\Voice\Interpreter;
use App\Voice\KeyTerms;
use App\Voice\ProposalValidator;
use App\Voice\ProviderUnavailable;
use App\Voice\Speaker;
use App\Voice\Transcriber;
use Closure;
use DateTimeZone;
use Exception;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;

/**
 * Voice editing (docs/voice-api.md). The server only proposes: the app checks
 * every proposal against its own copy of the schedule, previews it and applies
 * it only when the user confirms. The user's words are never stored or logged.
 */
class VoiceController extends Controller
{
    /** A short recording → its words. Neither the audio nor the words are kept. */
    public function transcribe(Request $request, Transcriber $transcriber): JsonResponse
    {
        $started = hrtime(true);
        if (! config('voice.enabled')) {
            $this->record($request, 'transcribe', 503, 'disabled', $started);

            return response()->json(['error' => 'voice_disabled'], 503);
        }

        $limits = config('voice.limits');
        $data = $request->validate([
            // AAC in MPEG-4 from Android's MediaRecorder; a few other common formats for other clients.
            'audio' => ['required', 'file', 'max:'.$limits['audio_kb'], 'mimetypes:'.implode(',', [
                'audio/mp4', 'audio/x-m4a', 'audio/m4a', 'video/mp4', 'audio/aac', 'audio/mpeg', 'audio/ogg', 'audio/webm', 'audio/wav', 'audio/x-wav',
            ])],
            'durationMs' => ['required', 'integer', 'min:100', 'max:'.($limits['audio_seconds'] * 1000)],
            'language' => ['nullable', 'string', 'regex:/^[a-z]{2,3}$/'],
            // The project's task titles, to hear them right; optional.
            'keyterms' => ['nullable', 'array', 'list', 'max:'.$limits['tasks']],
            'keyterms.*' => ['string', 'max:200'],
        ]);
        $keyterms = config('voice.keyterms') ? KeyTerms::from($data['keyterms'] ?? [], $limits['tasks']) : [];

        try {
            $transcription = $transcriber->transcribe($data['audio'], $data['language'] ?? null, $keyterms);
        } catch (ProviderUnavailable) {
            $this->record($request, 'transcribe', 502, 'provider_error', $started, ['audio_ms' => $data['durationMs']]);

            return response()->json(['error' => 'provider_unavailable'], 502);
        }

        $text = trim($transcription->text);
        $metrics = ['audio_ms' => $transcription->audioMs ?? $data['durationMs'], 'characters' => mb_strlen($text)];
        if (mb_strlen($text) > $limits['transcript_chars']) {
            $this->record($request, 'transcribe', 422, 'too_long', $started, $metrics);

            return response()->json(['error' => 'transcript_too_long'], 422);
        }

        $this->record($request, 'transcribe', 200, $text === '' ? 'empty' : 'text', $started, $metrics);

        return response()->json(['text' => $text, 'language' => $transcription->language]);
    }

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
            // Zone names and fixed offsets ("+08:00"), as Java and PHP both understand them.
            'context.timeZone' => ['required', 'string', 'max:64', function (string $attribute, mixed $value, Closure $fail) {
                try {
                    new DateTimeZone($value);
                } catch (Exception) {
                    $fail('The time zone is not valid.');
                }
            }],
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

    /**
     * Says what a preview will change and asks whether to apply it (stage
     * "proposal"), says what has just changed (stage "applied"), or speaks a
     * clarifying question. Never blocks the preview: if writing
     * the words fails a plain summary is used, and if speech fails the words
     * come back without audio.
     */
    public function explain(Request $request, Explainer $explainer, Speaker $speaker): JsonResponse
    {
        $started = hrtime(true);
        if (! config('voice.enabled')) {
            $this->record($request, 'explain', 503, 'disabled', $started);

            return response()->json(['error' => 'voice_disabled'], 503);
        }

        $limits = config('voice.limits');
        $text = ['string', 'max:200'];
        $time = ['date_format:Y-m-d\TH:i'];
        $data = $request->validate([
            'question' => ['required_without:changes', 'prohibits:changes', 'string', 'max:'.$limits['question_chars']],
            'changes' => ['required_without:question', 'array', 'list', 'min:1', 'max:'.$limits['explained_changes']],
            'changes.*' => ['array', function (string $attribute, mixed $value, Closure $fail) {
                if ($problem = ChangeList::problem($value)) {
                    $fail($problem);
                }
            }],
            'changes.*.change' => ['string'],
            'changes.*.task' => $text,
            'changes.*.title' => $text,
            'changes.*.predecessor' => $text,
            'changes.*.successor' => $text,
            'changes.*.fromStart' => $time,
            'changes.*.fromEnd' => $time,
            'changes.*.toStart' => $time,
            'changes.*.toEnd' => $time,
            'today' => ['required_with:changes', 'date_format:Y-m-d'],
            'language' => ['nullable', 'string', 'regex:/^[a-z]{2,3}$/'],
            // What the words are for; a question ignores it.
            'stage' => ['nullable', 'string', Rule::in(ChangeList::STAGES)],
            // Clashes a proposal would cause, named before the question; only with changes.
            'warnings' => ['nullable', 'prohibits:question', 'array', 'list', 'max:'.$limits['explained_warnings']],
            'warnings.*' => ['array', function (string $attribute, mixed $value, Closure $fail) {
                if ($problem = ChangeList::warningProblem($value)) {
                    $fail($problem);
                }
            }],
            'warnings.*.kind' => ['string'],
            'warnings.*.task' => $text,
            'warnings.*.other' => $text,
            'warnings.*.predecessor' => $text,
            'warnings.*.successor' => $text,
        ]);
        $language = $data['language'] ?? null;
        $stage = $data['stage'] ?? ChangeList::PROPOSAL;
        // Once applied there is nothing left to warn about.
        $warnings = $stage === ChangeList::PROPOSAL ? ($data['warnings'] ?? []) : [];

        $kind = 'speak';
        $fallback = false;
        $metrics = [];
        if (isset($data['question'])) {
            $words = trim($data['question']);
        } else {
            $kind = 'explain';
            try {
                $explanation = $explainer->explain($data['changes'], $data['today'], $language, $stage, $warnings);
                $words = $explanation->text;
                $metrics = ['input_tokens' => $explanation->inputTokens, 'output_tokens' => $explanation->outputTokens];
            } catch (ProviderUnavailable) {
                $words = ChangeList::summary($data['changes'], $stage, $warnings);
                $fallback = true;
            }
        }

        try {
            $speech = $speaker->speak($words, $language);
        } catch (ProviderUnavailable) {
            $speech = null;
        }

        $outcome = ($fallback ? 'fallback_' : '').($speech ? 'spoken' : 'text');
        $this->record($request, $kind, 200, $outcome, $started, $metrics + ['characters' => $speech ? mb_strlen($words) : 0]);

        return response()->json([
            'text' => $words,
            'audio' => $speech ? base64_encode($speech->audio) : null,
            'audioType' => $speech?->type,
        ]);
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
