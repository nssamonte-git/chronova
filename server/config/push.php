<?php

/*
 * Push notifications through Firebase Cloud Messaging (docs/push.md). The service account's
 * private key is read from a file outside the repository (Secrets Manager in AWS); it never
 * reaches the app.
 */
return [

    // "fcm" sends through Firebase; "fake" (the default) records and sends nothing.
    'sender' => env('PUSH_SENDER', 'fake'),

    'fcm' => [
        // Path to the Firebase service-account JSON (project_id, client_email, private_key, token_uri).
        'credentials' => env('FCM_CREDENTIALS'),
        // The same JSON as a string, for containers that get secrets as environment variables.
        'credentials_json' => env('FCM_CREDENTIALS_JSON'),
        'base_url' => env('FCM_BASE_URL', 'https://fcm.googleapis.com/v1'),
        'timeout' => (int) env('FCM_TIMEOUT', 10),
    ],
];
