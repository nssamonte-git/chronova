<?php

/*
 * Building a schedule from an image of a chart (POST /v1/import/image, docs/import-api.md).
 * Meta reads and segments the image; OpenAI turns Meta's results (never the image) into the
 * schedule. Provider keys come only from the environment and never reach the app.
 */
return [

    // Kill switch: false answers every import with 503.
    'enabled' => (bool) env('IMPORT_ENABLED', true),

    // "meta" in production (Muse Spark reads the chart, SAM 3.1 finds its bars); "fake" answers with a
    // fixed reading of the test chart (a third-party template, not in the repository), no network.
    'analyzer' => env('IMPORT_ANALYZER', 'fake'),

    // "openai" in production; "fake" turns the matched bars into tasks by rule.
    'builder' => env('IMPORT_BUILDER', 'fake'),

    // Seconds an import may run. PHP stops a request after 30 (max_execution_time), counting the
    // time spent waiting for the providers; CloudFront gives up on the server at 60
    // (server/infra/lib/chronova-stack.ts). The two provider timeouts below must add up to less.
    'time_limit' => (int) env('IMPORT_TIME_LIMIT', 55),

    'meta' => [
        'key' => env('META_API_KEY'),
        'base_url' => env('META_BASE_URL', 'https://api.meta.ai/v1'),
        // Standard models (Meta doesn't train on them); never the "-contributor" variants.
        'reader_model' => env('IMPORT_META_READER_MODEL', 'muse-spark-1.3'),
        'segmenter_model' => env('IMPORT_META_SEGMENTER_MODEL', 'sam-3.1'),
        // SAM finds one concept per request, named by a short noun phrase.
        'segment_phrases' => [
            // Measured on real charts: "gantt chart bar" finds nothing, "bar" finds bars and the tracks behind them.
            'bar' => env('IMPORT_META_BAR_PHRASE', 'bar'),
            'milestone' => env('IMPORT_META_MILESTONE_PHRASE', 'diamond shape'),
        ],
        // Muse Spark reasons before answering: measured on real charts, "minimal" read as well as the
        // default and took ~14 s instead of ~30 s.
        'reasoning_effort' => env('IMPORT_META_REASONING_EFFORT', 'minimal'),
        // Three calls at once; a read takes ~14 s (measured on real charts).
        'timeout' => (int) env('IMPORT_META_TIMEOUT', 30),
    ],

    // The OpenAI key and model are the voice assistant's (config/voice.php); its own timeout.
    'openai' => [
        'timeout' => (int) env('IMPORT_OPENAI_TIMEOUT', 20),
        'reasoning_effort' => env('IMPORT_OPENAI_REASONING_EFFORT', 'low'),
    ],

    'limits' => [
        // Each import costs provider money: per user, per minute and per day.
        'per_minute' => (int) env('IMPORT_REQUESTS_PER_MINUTE', 5),
        'per_day' => (int) env('IMPORT_REQUESTS_PER_DAY', 50),
        // The app sends a JPEG at most 2048 px on its long side (typically 0.3–1.5 MB).
        'image_kb' => 4096,
        'image_pixels' => 4096,
        'tasks' => 300,
        'days' => 366,
        'title_chars' => 200,
        'uncertain' => 20,
    ],
];
