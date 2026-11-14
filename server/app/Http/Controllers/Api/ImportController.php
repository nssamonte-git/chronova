<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Import\BarMatcher;
use App\Import\ChartAnalyzer;
use App\Import\ImportValidator;
use App\Import\ScheduleBuilder;
use App\Models\VoiceEvent;
use App\Voice\ProviderUnavailable;
use Closure;
use DateTimeZone;
use Exception;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/**
 * A schedule from an image of a chart (docs/import-api.md). Meta reads and segments the image;
 * OpenAI builds the schedule from Meta's results, never from the image. The server only proposes:
 * the app previews the answer and applies it only when the user confirms. The image is held in
 * memory for the request and never stored or logged.
 */
class ImportController extends Controller
{
    public function image(Request $request): JsonResponse
    {
        $started = hrtime(true);
        if (! config('import.enabled')) {
            $this->record($request, 503, 'disabled', $started);

            return response()->json(['error' => 'import_disabled'], 503);
        }

        $limits = config('import.limits');
        $data = $request->validate([
            'image' => ['required', 'file', 'max:'.$limits['image_kb'], 'mimetypes:image/jpeg,image/png,image/webp'],
            'mode' => ['required', 'in:new_project,add_to_project'],
            'timeZone' => ['required', 'string', 'max:64', function (string $attribute, mixed $value, Closure $fail) {
                try {
                    new DateTimeZone($value);
                } catch (Exception) {
                    $fail('The time zone is not valid.');
                }
            }],
            'today' => ['required', 'date_format:Y-m-d'],
            // Adding to a project: its name and dates help read a chart with partial dates ("Mar 3").
            'projectName' => ['required_if:mode,add_to_project', 'nullable', 'string', 'max:200'],
            'projectStart' => ['required_if:mode,add_to_project', 'nullable', 'date_format:Y-m-d'],
            'projectEnd' => ['required_if:mode,add_to_project', 'nullable', 'date_format:Y-m-d', 'after_or_equal:projectStart'],
        ]);

        $bytes = $data['image']->get();
        $size = @getimagesizefromstring($bytes);
        if ($size === false || $size[0] < 1 || $size[1] < 1 || max($size[0], $size[1]) > $limits['image_pixels']) {
            $this->record($request, 422, 'bad_image', $started);

            return response()->json(['error' => 'invalid_image'], 422);
        }

        $tokens = ['input_tokens' => 0, 'output_tokens' => 0, 'cached_tokens' => 0];
        // Resolved here, so a switched-off import never builds a provider.
        $analyzer = app(ChartAnalyzer::class);
        $builder = app(ScheduleBuilder::class);
        // Reading an image takes longer than PHP's usual 30 s allows (it counts the time spent waiting
        // for the providers): this request gets the import's own limit, and the usual one afterwards.
        $usualLimit = (int) ini_get('max_execution_time');
        set_time_limit(config('import.time_limit'));
        try {
            $analysis = $analyzer->analyze($bytes, $size['mime'], $size[0], $size[1]);
            $tokens['input_tokens'] += $analysis->inputTokens ?? 0;
            $tokens['output_tokens'] += $analysis->outputTokens ?? 0;
            unset($bytes);

            $built = $builder->build([
                'mode' => $data['mode'],
                'today' => $data['today'],
                'timeZone' => $data['timeZone'],
                'project' => $data['mode'] === 'add_to_project'
                    ? ['name' => $data['projectName'], 'startDate' => $data['projectStart'], 'endDate' => $data['projectEnd']]
                    : null,
                'chart' => BarMatcher::match($analysis),
            ]);
            $tokens['input_tokens'] += $built->inputTokens ?? 0;
            $tokens['output_tokens'] += $built->outputTokens ?? 0;
            $tokens['cached_tokens'] += $built->cachedTokens ?? 0;
        } catch (ProviderUnavailable) {
            $this->record($request, 502, 'provider_error', $started, $tokens);

            return response()->json(['error' => 'provider_unavailable'], 502);
        } finally {
            set_time_limit($usualLimit);
        }

        $schedule = (new ImportValidator($limits))->validate($built->schedule);
        if ($schedule === null) {
            $this->record($request, 502, 'invalid', $started, $tokens);

            return response()->json(['error' => 'invalid_import'], 502);
        }

        $this->record($request, 200, $schedule['result'], $started, $tokens);

        return response()->json($schedule);
    }

    /** Usage metrics only: counts and timing, never content. */
    private function record(Request $request, int $status, string $outcome, int $started, array $extra = []): void
    {
        $event = new VoiceEvent([
            'kind' => 'import',
            'status' => $status,
            'outcome' => $outcome,
            'duration_ms' => (int) ((hrtime(true) - $started) / 1_000_000),
        ] + array_filter($extra, fn ($value) => $value !== 0));
        $event->user_id = $request->user()?->getKey();
        $event->save();
    }
}
