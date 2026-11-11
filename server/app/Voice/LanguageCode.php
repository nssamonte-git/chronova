<?php

namespace App\Voice;

/**
 * Scribe reports languages as ISO 639-3 ("eng"), while text to speech takes
 * ISO 639-1 ("en") and refuses anything else. Converts the languages the
 * speech model supports; others are left as they are.
 */
final class LanguageCode
{
    private const TWO_LETTER = [
        'ara' => 'ar', 'bul' => 'bg', 'ces' => 'cs', 'cmn' => 'zh', 'dan' => 'da', 'deu' => 'de', 'ell' => 'el',
        'eng' => 'en', 'fin' => 'fi', 'fra' => 'fr', 'hin' => 'hi', 'hrv' => 'hr', 'hun' => 'hu', 'ind' => 'id',
        'ita' => 'it', 'jpn' => 'ja', 'kor' => 'ko', 'msa' => 'ms', 'nld' => 'nl', 'nob' => 'no', 'nor' => 'no',
        'pol' => 'pl', 'por' => 'pt', 'ron' => 'ro', 'rus' => 'ru', 'slk' => 'sk', 'spa' => 'es', 'swe' => 'sv',
        'tam' => 'ta', 'tgl' => 'tl', 'tur' => 'tr', 'ukr' => 'uk', 'vie' => 'vi', 'zho' => 'zh', 'zsm' => 'ms',
    ];

    public static function normalize(?string $code): ?string
    {
        if ($code === null || $code === '') {
            return null;
        }
        $code = strtolower($code);

        return self::TWO_LETTER[$code] ?? $code;
    }

    /** The ISO 639-1 code, or null when there isn't one we know. */
    public static function twoLetter(?string $code): ?string
    {
        $code = self::normalize($code);

        return $code !== null && strlen($code) === 2 ? $code : null;
    }
}
