<?php

namespace App\Providers;

use App\Import\ChartAnalyzer;
use App\Import\FakeChartAnalyzer;
use App\Import\FakeScheduleBuilder;
use App\Import\ScheduleBuilder;
use App\Push\FakePushSender;
use App\Push\FcmPushSender;
use App\Push\PushSender;
use App\Voice\ElevenLabsSpeaker;
use App\Voice\ElevenLabsTranscriber;
use App\Voice\Explainer;
use App\Voice\FakeExplainer;
use App\Voice\FakeInterpreter;
use App\Voice\FakeSpeaker;
use App\Voice\FakeTranscriber;
use App\Voice\Interpreter;
use App\Voice\OpenAiExplainer;
use App\Voice\OpenAiInterpreter;
use App\Voice\Speaker;
use App\Voice\Transcriber;
use Filament\Support\Assets\Css;
use Filament\Support\Facades\FilamentAsset;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Client\Factory as Http;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Facades\URL;
use Illuminate\Support\ServiceProvider;
use Illuminate\Support\Str;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register any application services.
     */
    public function register(): void
    {
        // Voice providers: real ones only when configured; the fake needs no network or key.
        // Schedules from images (docs/import-api.md): fakes until IMPORT_ANALYZER / IMPORT_BUILDER say otherwise.
        $this->app->bind(ChartAnalyzer::class, fn () => match (config('import.analyzer')) {
            default => new FakeChartAnalyzer,
        });
        $this->app->bind(ScheduleBuilder::class, fn () => match (config('import.builder')) {
            default => new FakeScheduleBuilder,
        });
        $this->app->bind(Interpreter::class, fn ($app) => match (config('voice.interpreter')) {
            'openai' => new OpenAiInterpreter($app->make(Http::class), config('voice.openai')),
            default => new FakeInterpreter,
        });
        $this->app->bind(Transcriber::class, fn ($app) => match (config('voice.transcriber')) {
            'elevenlabs' => new ElevenLabsTranscriber($app->make(Http::class), config('voice.elevenlabs')),
            default => new FakeTranscriber(config('voice.fake_transcript'), $app->make('cache.store'), $app->make('request')->user()?->getKey()),
        });
        $this->app->bind(Explainer::class, fn ($app) => match (config('voice.explainer')) {
            'openai' => new OpenAiExplainer($app->make(Http::class), config('voice.openai')),
            default => new FakeExplainer,
        });
        $this->app->bind(Speaker::class, fn ($app) => match (config('voice.speech')) {
            'elevenlabs' => new ElevenLabsSpeaker($app->make(Http::class), config('voice.elevenlabs')),
            default => new FakeSpeaker,
        });
        // Push notifications: Firebase only when configured; the fake records what would be sent.
        $this->app->singleton(PushSender::class, fn ($app) => match (config('push.sender')) {
            'fcm' => new FcmPushSender($app->make(Http::class), $app->make('cache.store'), config('push.fcm')),
            default => new FakePushSender,
        });
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        // The admin panel's Chronova look; `php artisan filament:assets` publishes it to public/css/app.
        FilamentAsset::register([Css::make('chronova-admin', resource_path('css/admin-theme.css'))]);

        if (config('app.behind_cloudfront')) {
            URL::forceScheme('https');
        }

        // Sign-up, sign-in and code endpoints: per email+IP, and per IP overall.
        RateLimiter::for('auth', fn (Request $request) => [
            Limit::perMinute(5)->by('auth-email:'.mb_strtolower((string) $request->input('email')).'|'.$request->ip()),
            Limit::perMinute(30)->by('auth-ip:'.$request->ip()),
        ]);

        // A device syncs on demand, but a full pull of a big account takes a few pages.
        RateLimiter::for('sync', fn (Request $request) => Limit::perMinute(60)->by('sync:'.$request->user()?->getKey()));

        // Every voice request costs provider money; on for everyone, so capped per user and
        // endpoint (a spoken command is one transcribe and one interpret).
        RateLimiter::for('voice', fn (Request $request) => Limit::perMinute(config('voice.limits.requests_per_minute'))
            ->by('voice:'.Str::afterLast($request->path(), '/').':'.$request->user()?->getKey()));

        // An import is two Meta calls and one OpenAI call: a few a minute and a daily cap per user.
        RateLimiter::for('import', fn (Request $request) => [
            Limit::perMinute(config('import.limits.per_minute'))->by('import-minute:'.$request->user()?->getKey()),
            Limit::perDay(config('import.limits.per_day'))->by('import-day:'.$request->user()?->getKey()),
        ]);
    }
}
