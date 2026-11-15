<?php

namespace App\Voice;

/**
 * ElevenLabs audio tags: bracketed directions such as "[excited, cheerful]" that set how a
 * line is said. Only the Eleven v3 and v4 models act on them; others would read the brackets
 * aloud, so they get the words alone. A tag's mood carries on through the rest of the text,
 * so one leading set is enough for a sentence or two.
 */
final class AudioTags
{
    public static function supported(string $model): bool
    {
        return str_starts_with($model, 'eleven_v3') || str_starts_with($model, 'eleven_v4');
    }

    /**
     * The text to send to text to speech. Square brackets are taken out of the words first,
     * so a task title like "[Draft] spec" is spoken as words, never taken as a direction;
     * then the style tags lead, when the model supports them and some are set.
     */
    public static function forSpeech(string $words, string $model, ?string $tags): string
    {
        $words = trim(preg_replace('/\s{2,}/u', ' ', str_replace(['[', ']'], '', $words)));
        $tags = trim(str_replace(['[', ']'], '', (string) $tags));

        return $tags !== '' && self::supported($model) ? "[{$tags}] {$words}" : $words;
    }
}
