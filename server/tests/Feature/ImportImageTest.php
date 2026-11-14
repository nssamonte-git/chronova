<?php

use App\Import\BuiltSchedule;
use App\Import\ChartAnalysis;
use App\Import\ChartAnalyzer;
use App\Import\ScheduleBuilder;
use App\Models\User;
use App\Models\VoiceEvent;
use App\Voice\ProviderUnavailable;
use Illuminate\Http\UploadedFile;
use Laravel\Sanctum\Sanctum;

beforeEach(function () {
    $this->user = User::factory()->create();
    Sanctum::actingAs($this->user, ['sync']);
});

/** A real PNG of the test chart's size (the fake analyzer ignores the pixels). */
function chartImage(int $width = 1640, int $height = 1050, string $name = 'chart.png'): UploadedFile
{
    return UploadedFile::fake()->image($name, $width, $height);
}

function importImage(array $fields = [], ?UploadedFile $image = null)
{
    return test()->post('/v1/import/image', $fields + [
        'image' => $image ?? chartImage(),
        'mode' => 'new_project',
        'timeZone' => 'Asia/Manila',
        'today' => '2026-11-13',
    ], ['Accept' => 'application/json']);
}

it('reads the test chart into nine tasks over seven weeks, in relative days', function () {
    $response = importImage()->assertOk();

    expect($response->json('result'))->toBe('ok')
        ->and($response->json('dates'))->toBe('relative')
        ->and($response->json('project'))->toBe(['name' => 'Project Timeline', 'startDate' => null, 'endDate' => null, 'days' => 49])
        ->and($response->json('uncertain'))->toBe([]);

    $tasks = collect($response->json('tasks'));
    expect($tasks->map(fn ($t) => [$t['title'], $t['startDay'], $t['endDay']])->all())->toBe([
        ['Initial Client Consult', 0, 6],
        ['Project Design Inquiry', 0, 13],
        ['Initial Design Concept', 14, 20],
        ['Release Design For Bid', 21, 27],
        ['2nd Design Concept', 28, 34],
        ['Sales Presentation & Proposals', 35, 41],
        ['Purchasing of Materials', 35, 41],
        ['Install', 42, 48],
        ['Review Finished Project', 42, 48],
    ])->and($tasks->first())->toBe([
        'ref' => 't1', 'title' => 'Initial Client Consult', 'startDate' => null, 'endDate' => null, 'startDay' => 0, 'endDay' => 6,
        'startTime' => null, 'endTime' => null, 'milestone' => false, 'progress' => 0, 'predecessorRefs' => [],
    ]);
});

it('records counts and timing only', function () {
    importImage()->assertOk();

    $event = VoiceEvent::sole();
    expect($event->only(['kind', 'status', 'outcome']))->toBe(['kind' => 'import', 'status' => 200, 'outcome' => 'ok'])
        ->and($event->user_id)->toBe($this->user->id)
        ->and($event->characters)->toBeNull();
});

it('needs the project\'s name and dates to add to it', function () {
    importImage(['mode' => 'add_to_project'])->assertUnprocessable()
        ->assertJsonValidationErrors(['projectName', 'projectStart', 'projectEnd']);

    importImage(['mode' => 'add_to_project', 'projectName' => 'Jnn', 'projectStart' => '2026-11-12', 'projectEnd' => '2026-12-31'])->assertOk();
});

it('rejects what it cannot use', function (array $fields, ?Closure $image, string $error) {
    importImage($fields, $image?->__invoke())->assertUnprocessable()->assertJsonValidationErrors($error);
})->with([
    'no image' => [['image' => null], null, 'image'],
    'a PDF' => [[], fn () => UploadedFile::fake()->create('chart.pdf', 100, 'application/pdf'), 'image'],
    'over 4 MB' => [[], fn () => UploadedFile::fake()->image('chart.jpg', 100, 100)->size(4097), 'image'],
    'an unknown mode' => [['mode' => 'merge'], null, 'mode'],
    'a bad time zone' => [['timeZone' => 'Mars/Olympus'], null, 'timeZone'],
    'a bad date' => [['today' => '13/11/2026'], null, 'today'],
    'end before start' => [['mode' => 'add_to_project', 'projectName' => 'Jnn', 'projectStart' => '2026-11-12', 'projectEnd' => '2026-11-01'], null, 'projectEnd'],
]);

it('refuses a file that is not really an image, or a huge one', function () {
    importImage([], UploadedFile::fake()->create('chart.png', 10, 'image/png'))->assertUnprocessable()->assertExactJson(['error' => 'invalid_image']);
    importImage([], chartImage(5000, 100))->assertUnprocessable()->assertExactJson(['error' => 'invalid_image']);
});

it('answers 503 when switched off, without calling anyone', function () {
    config(['import.enabled' => false]);
    app()->bind(ChartAnalyzer::class, fn () => throw new LogicException('must not be called'));

    importImage()->assertStatus(503)->assertExactJson(['error' => 'import_disabled']);
    expect(VoiceEvent::sole()->outcome)->toBe('disabled');
});

it('answers 502 when a provider fails, and records it', function () {
    app()->bind(ChartAnalyzer::class, fn () => new class implements ChartAnalyzer
    {
        public function analyze(string $image, string $mime, int $width, int $height): ChartAnalysis
        {
            throw new ProviderUnavailable('Meta answered 500');
        }
    });

    importImage()->assertStatus(502)->assertExactJson(['error' => 'provider_unavailable']);
    expect(VoiceEvent::sole()->outcome)->toBe('provider_error');
});

it('answers 502 when the builder\'s schedule can\'t be trusted', function () {
    app()->bind(ScheduleBuilder::class, fn () => new class implements ScheduleBuilder
    {
        public function build(array $input): BuiltSchedule
        {
            return new BuiltSchedule(['result' => 'ok', 'tasks' => 'many']);
        }
    });

    importImage()->assertStatus(502)->assertExactJson(['error' => 'invalid_import']);
});

it('is limited per minute and per day for each user', function () {
    config(['import.limits.per_minute' => 2, 'import.limits.per_day' => 3]);
    importImage()->assertOk();
    importImage()->assertOk();
    importImage()->assertTooManyRequests();

    $this->travel(2)->minutes();
    importImage()->assertOk();
    $this->travel(2)->minutes();
    importImage()->assertTooManyRequests(); // the day's three are used
});

it('needs a signed-in user with the sync ability', function () {
    app('auth')->forgetGuards();
    $this->post('/v1/import/image', [], ['Accept' => 'application/json'])->assertUnauthorized();

    Sanctum::actingAs($this->user, ['something-else']);
    importImage()->assertForbidden();
});
