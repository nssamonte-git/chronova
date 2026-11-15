<?php

use App\Voice\AudioTags;

it('leads with the style tags on models that use audio tags', function (string $model) {
    expect(AudioTags::forSpeech('Build moves to noon.', $model, 'excited, cheerful'))
        ->toBe('[excited, cheerful] Build moves to noon.');
})->with(['eleven_v4_turbo', 'eleven_v4', 'eleven_v3']);

it('sends the words alone to models without audio tags, or with no tags set', function (string $model, ?string $tags) {
    expect(AudioTags::forSpeech('Build moves to noon.', $model, $tags))->toBe('Build moves to noon.');
})->with([
    'Flash v2.5' => ['eleven_flash_v2_5', 'excited'],
    'Multilingual v2' => ['eleven_multilingual_v2', 'excited'],
    'empty' => ['eleven_v4_turbo', ''],
    'unset' => ['eleven_v4_turbo', null],
]);

it('takes brackets out of the words so a title never acts as a tag', function () {
    expect(AudioTags::forSpeech('Move [Draft] spec to [whispers] Friday.', 'eleven_v4_turbo', 'excited'))
        ->toBe('[excited] Move Draft spec to whispers Friday.')
        ->and(AudioTags::forSpeech('Move [Draft] spec.', 'eleven_flash_v2_5', 'excited'))->toBe('Move Draft spec.');
});

it('accepts tags written with their own brackets', function () {
    expect(AudioTags::forSpeech('Done.', 'eleven_v4_turbo', '[excited]'))->toBe('[excited] Done.');
});
