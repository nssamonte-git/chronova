<?php

use App\Import\BarMatcher;
use App\Import\ChartAnalysis;
use App\Import\ImportSchema;
use App\Import\MetaChartAnalyzer;
use App\Import\OpenAiScheduleBuilder;
use App\Import\SamBoxes;
use App\Models\User;
use App\Models\VoiceEvent;
use App\Voice\ProviderUnavailable;
use Illuminate\Http\Client\Factory;
use Illuminate\Http\Client\Request;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Laravel\Sanctum\Sanctum;

const META = 'https://api.meta.ai/v1';

function useRealImport(): void
{
    config([
        'import.analyzer' => 'meta', 'import.builder' => 'openai',
        'import.meta.key' => 'meta-test-key-not-real', 'voice.openai.key' => 'openai-test-key-not-real',
    ]);
}

function metaReading(array $overrides = []): array
{
    return array_merge([
        'kind' => 'gantt', 'title' => 'Office move',
        'axis' => ['unit' => 'week', 'labels' => [['text' => 'W1', 'x' => 150], ['text' => 'W2', 'x' => 250]]],
        'rows' => [['label' => '1. Pack', 'y' => 100, 'text' => ''], ['label' => '2. Move', 'y' => 200, 'text' => '']],
        'links' => [], 'notes' => [],
    ], $overrides);
}

function museAnswer(array $reading, string $finish = 'stop'): array
{
    return ['choices' => [['message' => ['role' => 'assistant', 'content' => json_encode($reading)], 'finish_reason' => $finish]],
        'usage' => ['prompt_tokens' => 1500, 'completion_tokens' => 700, 'total_tokens' => 2200]];
}

/** SAM's one-line image output for these boxes, each with a (dummy) one-bit mask. */
function samAnswer(array $boxes): array
{
    $records = [];
    foreach ($boxes as $id => [$x1, $y1, $x2, $y2]) {
        $records[] = $id."<|box;x1=$x1;y1=$y1;x2=$x2;y2=$y2;w=500;h=300|><|mask;x=0;y=0;data=2,3,!0R>B~{}|>";
    }

    return ['status' => 'completed', 'output' => [['type' => 'message', 'role' => 'assistant', 'content' => [
        ['type' => 'output_text', 'text' => $records === [] ? '' : '<0f>'.implode(',', $records)],
    ]]]];
}

function openAiSchedule(array $schedule): array
{
    return ['status' => 'completed', 'output' => [['type' => 'message', 'content' => [['type' => 'output_text', 'text' => json_encode($schedule)]]]],
        'usage' => ['input_tokens' => 2000, 'output_tokens' => 400, 'input_tokens_details' => ['cached_tokens' => 1500]]];
}

function builtSchedule(): array
{
    $task = fn ($ref, $title, $start, $end, $after = []) => ['ref' => $ref, 'title' => $title, 'startDate' => null, 'endDate' => null,
        'startDay' => $start, 'endDay' => $end, 'startTime' => null, 'endTime' => null, 'milestone' => false, 'progress' => 0, 'predecessorRefs' => $after];

    return ['result' => 'ok', 'dates' => 'relative', 'project' => ['name' => 'Office Move', 'startDate' => null, 'endDate' => null, 'days' => 14],
        'tasks' => [$task('t1', 'Pack', 0, 6), $task('t2', 'Move', 7, 13, ['t1'])], 'uncertain' => []];
}

function fakeProviders(?array $reading = null, ?array $bars = null, ?array $schedule = null): void
{
    Http::fake(function (Request $request) use ($reading, $bars, $schedule) {
        return match (true) {
            $request->url() === META.'/chat/completions' => Http::response(museAnswer($reading ?? metaReading())),
            $request->url() === META.'/responses' && $request['input'][0]['content'][0]['text'] === 'bar' => Http::response(samAnswer($bars ?? [[100, 90, 195, 110], [205, 190, 300, 210]])),
            $request->url() === META.'/responses' => Http::response(samAnswer([])),
            str_starts_with($request->url(), 'https://api.openai.com/') => Http::response(openAiSchedule($schedule ?? builtSchedule())),
        };
    });
}

beforeEach(function () {
    useRealImport();
    Sanctum::actingAs(User::factory()->create(), ['sync']);
});

function postChart()
{
    return test()->post('/v1/import/image', [
        'image' => UploadedFile::fake()->image('chart.jpg', 500, 300), 'mode' => 'new_project', 'timeZone' => 'Asia/Manila', 'today' => '2026-11-13',
    ], ['Accept' => 'application/json']);
}

it('reads with Muse Spark, segments with SAM, and builds with OpenAI from Meta\'s results only', function () {
    fakeProviders();

    postChart()->assertOk()->assertJsonPath('tasks.1', [
        'ref' => 't2', 'title' => 'Move', 'startDate' => null, 'endDate' => null, 'startDay' => 7, 'endDay' => 13,
        'startTime' => null, 'endTime' => null, 'milestone' => false, 'progress' => 0, 'predecessorRefs' => ['t1'],
    ]);

    Http::assertSentCount(4);
    // Muse Spark: the image as a data URL, the standard model, a strict schema.
    Http::assertSent(fn (Request $r) => $r->url() === META.'/chat/completions'
        && $r->hasHeader('Authorization', 'Bearer meta-test-key-not-real')
        && $r['model'] === 'muse-spark-1.3'
        && str_starts_with($r['messages'][1]['content'][1]['image_url']['url'], 'data:image/jpeg;base64,')
        && $r['messages'][1]['content'][0]['text'] === 'The image is 500×300 pixels.'
        && $r['response_format']['json_schema']['strict'] === true
        && $r['reasoning_effort'] === 'minimal');
    // SAM: one concept per request, the image as a data URL, nothing stored.
    foreach (['bar', 'diamond shape'] as $phrase) {
        Http::assertSent(fn (Request $r) => $r->url() === META.'/responses' && $r['model'] === 'sam-3.1'
            && $r['input'][0]['content'][0] === ['type' => 'input_text', 'text' => $phrase]
            && str_starts_with($r['input'][0]['content'][1]['image_url'], 'data:image/jpeg;base64,')
            && $r['metadata'] === ['mask_encoding' => 'one_bit', 'store' => 'false']);
    }
    // OpenAI: no image, nothing stored, the strict import schema, the bars measured in columns.
    Http::assertSent(function (Request $r) {
        if (! str_starts_with($r->url(), 'https://api.openai.com/')) {
            return false;
        }
        $body = json_encode($r->data());
        $data = json_decode($r['input'][1]['content'], true);

        return ! str_contains($body, 'base64') && ! str_contains($body, 'image_url')
            && $r['store'] === false
            && $r['text']['format']['schema'] === ImportSchema::jsonSchema()
            && $r['prompt_cache_key'] === 'chronova-import-rules'
            // (0.0 travels as JSON 0, so loosely.)
            && $data['chart']['rows'][0] == ['label' => '1. Pack', 'text' => '', 'bars' => [[0, 0.95]], 'milestones' => [], 'seen' => ['from' => '', 'to' => '']]
            && $data['mode'] === 'new_project' && $data['project'] === null;
    });
});

it('carries the hours written on a one-day bar through to the answer', function () {
    fakeProviders(
        reading: metaReading([
            'axis' => ['unit' => 'date', 'labels' => [['text' => 'WED 25', 'x' => 150], ['text' => 'THU 26', 'x' => 250]]],
            'rows' => [['label' => 'MENU TASTING', 'y' => 100, 'text' => '10–1', 'barStart' => 'THU 26', 'barEnd' => 'THU 26']],
        ]),
        bars: [[205, 90, 300, 110]],
        schedule: ['result' => 'ok', 'dates' => 'absolute', 'project' => ['name' => 'Cafe Opening', 'startDate' => '2026-11-26', 'endDate' => '2026-11-26', 'days' => null],
            'tasks' => [['ref' => 't1', 'title' => 'Menu Tasting', 'startDate' => '2026-11-26', 'endDate' => '2026-11-26', 'startDay' => null, 'endDay' => null,
                'startTime' => '10:00', 'endTime' => '13:00', 'milestone' => false, 'progress' => 0, 'predecessorRefs' => []]], 'uncertain' => []],
    );

    postChart()->assertOk()->assertJsonPath('tasks.0', [
        'ref' => 't1', 'title' => 'Menu Tasting', 'startDate' => '2026-11-26', 'endDate' => '2026-11-26', 'startDay' => null, 'endDay' => null,
        'startTime' => '10:00', 'endTime' => '13:00', 'milestone' => false, 'progress' => 0, 'predecessorRefs' => [],
    ]);

    // The reader is told to report what is written inside a bar; the builder gets it and has somewhere to put it.
    Http::assertSent(fn (Request $r) => $r->url() === META.'/chat/completions'
        && str_contains($r['messages'][0]['content'], 'written inside or beside its bar'));
    Http::assertSent(function (Request $r) {
        if (! str_starts_with($r->url(), 'https://api.openai.com/')) {
            return false;
        }
        $task = $r['text']['format']['schema']['properties']['tasks']['items'];

        return json_decode($r['input'][1]['content'], true)['chart']['rows'][0]['text'] === '10–1'
            && $task['properties']['startTime'] === ['type' => ['string', 'null']]
            && $task['properties']['endTime'] === ['type' => ['string', 'null']]
            && in_array('startTime', $task['required'], true) && in_array('endTime', $task['required'], true)
            && str_contains($r['input'][0]['content'][0]['text'], 'startTime and endTime');
    });
});

it('records Meta\'s and OpenAI\'s tokens together', function () {
    fakeProviders();
    postChart()->assertOk();

    expect(VoiceEvent::sole()->only(['input_tokens', 'output_tokens', 'cached_tokens']))
        ->toBe(['input_tokens' => 3500, 'output_tokens' => 1100, 'cached_tokens' => 1500]);
});

it('answers 502 without leaking a provider\'s body when any call fails', function (string $failing) {
    Log::spy();
    Http::fake(function (Request $request) use ($failing) {
        $which = str_contains($request->url(), 'openai') ? 'openai' : (str_ends_with($request->url(), '/chat/completions') ? 'read' : 'segment');

        return $which === $failing
            ? Http::response(['error' => 'secret details about chart.jpg'], 500)
            : match ($which) {
                'read' => Http::response(museAnswer(metaReading())),
                'segment' => Http::response(samAnswer([])),
                'openai' => Http::response(openAiSchedule(builtSchedule())),
            };
    });

    postChart()->assertStatus(502)->assertExactJson(['error' => 'provider_unavailable']);
    Log::shouldNotHaveReceived('error');
})->with(['read', 'segment', 'openai']);

it('treats an unfinished or unparsable reading as a failure', function () {
    Http::fake([META.'/chat/completions' => Http::response(museAnswer(metaReading(), 'length')), META.'/responses' => Http::response(samAnswer([])), '*' => Http::response(openAiSchedule(builtSchedule()))]);
    postChart()->assertStatus(502);

    Http::fake([META.'/chat/completions' => Http::response(['choices' => [['message' => ['content' => 'not json'], 'finish_reason' => 'stop']]])]);
    postChart()->assertStatus(502);
});

it('lets OpenAI say an image is not a schedule', function () {
    fakeProviders(reading: metaReading(['kind' => 'other', 'rows' => []]), schedule: [
        'result' => 'not_a_schedule', 'dates' => 'relative', 'project' => ['name' => '', 'startDate' => null, 'endDate' => null, 'days' => null], 'tasks' => [], 'uncertain' => ['A photo of a cat.'],
    ]);

    postChart()->assertOk()->assertJsonPath('result', 'not_a_schedule')->assertJsonPath('tasks', []);
});

it('treats a refusal as nothing usable', function () {
    Http::fake([
        META.'/*' => Http::sequence()->push(museAnswer(metaReading()))->push(samAnswer([]))->push(samAnswer([])),
        'https://api.openai.com/*' => Http::response(['status' => 'completed', 'output' => [['type' => 'message', 'content' => [['type' => 'refusal', 'refusal' => 'No.']]]]]),
    ]);

    postChart()->assertOk()->assertJsonPath('result', 'unreadable');
});

it('needs its keys, and never uses Meta\'s contributor models', function () {
    $analyzer = fn (array $config) => new MetaChartAnalyzer(app(Factory::class), $config + config('import.meta'));

    expect(fn () => $analyzer(['key' => null])->analyze('x', 'image/png', 1, 1))->toThrow(ProviderUnavailable::class, 'Meta is not configured.')
        ->and(fn () => $analyzer(['reader_model' => 'muse-spark-1.3-contributor'])->analyze('x', 'image/png', 1, 1))->toThrow(ProviderUnavailable::class, 'contributor')
        ->and(fn () => (new OpenAiScheduleBuilder(app(Factory::class), ['key' => null] + config('import.openai') + config('voice.openai')))->build([]))
        ->toThrow(ProviderUnavailable::class, 'OpenAI is not configured.');
    Http::assertNothingSent();
});

it('reads SAM boxes, skipping masks and anything malformed', function () {
    $line = '<0f>0<|box;x1=353;y1=382;x2=453;y2=427;w=537;h=561|><|mask;x=0;y=0;data=46,101,~a|>bc~|>,'
        .'4<|box;x1=10.6;y1=20;x2=30;y2=40;w=537;h=561;c=0.93|><|mask;x=0;y=0;c=0.93;data=1,1,!z|>,'
        .'5<|box;x1=50;y1=50;x2=40;y2=60;w=537;h=561|><|mask;x=0;y=0;data=1,1,!z|>';

    expect(SamBoxes::parse($line))->toBe([['x1' => 353, 'y1' => 382, 'x2' => 453, 'y2' => 427], ['x1' => 11, 'y1' => 20, 'x2' => 30, 'y2' => 40]])
        ->and(SamBoxes::parse(''))->toBe([])
        ->and(SamBoxes::outputText(['output_text' => 'x']))->toBe('x');
});

it('measures what SAM found against what Muse Spark read', function () {
    $analysis = new ChartAnalysis(metaReading(), [['kind' => 'bar', 'x1' => 100, 'y1' => 90, 'x2' => 195, 'y2' => 110]], 500, 300);

    expect(BarMatcher::match($analysis)['rows'][0]['bars'])->toBe([[0.0, 0.95]]);
});
