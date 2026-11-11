<?php

namespace App\Voice;

/**
 * Words to listen for (the project's task titles), cleaned to what Scribe's
 * `keyterms` accepts: at most 50 characters and 5 words each, none of
 * < > { } [ ] \. Titles that can't be made to fit are left out rather than
 * cut, so a half title is never suggested.
 */
final class KeyTerms
{
    public const MAX_CHARS = 50;

    public const MAX_WORDS = 5;

    /** @return list<string> */
    public static function from(array $titles, int $limit): array
    {
        $terms = [];
        foreach ($titles as $title) {
            if (! is_string($title)) {
                continue;
            }
            $term = trim(preg_replace('/\s+/u', ' ', str_replace(['<', '>', '{', '}', '[', ']', '\\'], ' ', $title)));
            if ($term === '' || mb_strlen($term) > self::MAX_CHARS || count(explode(' ', $term)) > self::MAX_WORDS) {
                continue;
            }
            $terms[mb_strtolower($term)] ??= $term;
            if (count($terms) === $limit) {
                break;
            }
        }

        return array_values($terms);
    }
}
