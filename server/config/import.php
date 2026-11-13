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
    // fixed reading of the test chart (tests/Fixtures/import/project-timeline.png), no network.
    'analyzer' => env('IMPORT_ANALYZER', 'fake'),

    // "openai" in production; "fake" turns the matched bars into tasks by rule.
    'builder' => env('IMPORT_BUILDER', 'fake'),

    'meta' => [
        'key' => env('META_API_KEY'),
        'base_url' => env('META_BASE_URL', 'https://api.meta.ai/v1'),
        // Standard models (Meta doesn't train on them); never the "-contributor" variants.
        'reader_model' => env('IMPORT_META_READER_MODEL', 'muse-spark-1.3'),
        'segmenter_model' => env('IMPORT_META_SEGMENTER_MODEL', 'sam-3.1'),
        // SAM finds one concept per request, named by a short noun phrase.
        'segment_phrases' => [
            'bar' => env('IMPORT_META_BAR_PHRASE', 'gantt chart bar'),
            'milestone' => env('IMPORT_META_MILESTONE_PHRASE', 'diamond shape'),
        ],
        'timeout' => (int) env('IMPORT_META_TIMEOUT', 30),
    ],

    // The OpenAI key and model are the voice assistant's (config/voice.php); its own timeout.
    'openai' => [
        'timeout' => (int) env('IMPORT_OPENAI_TIMEOUT', 30),
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
