<?php

namespace App\Import;

/**
 * Reads the boxes from SAM 3.1's image output: one line, "<0f>" then comma-separated records, each
 * an object id, a box token "<|box;x1=…;y1=…;x2=…;y2=…;w=…;h=…|>" and a mask token. Masks are
 * skipped (their base85 payload never contains "|"), and so is any malformed box.
 */
final class SamBoxes
{
    /** @return list<array{x1: int, y1: int, x2: int, y2: int}> */
    public static function parse(string $output): array
    {
        preg_match_all('/<\|box;([^|]*)\|>/', $output, $matches);
        $boxes = [];
        foreach ($matches[1] as $fields) {
            $box = [];
            foreach (explode(';', $fields) as $field) {
                [$key, $value] = array_pad(explode('=', $field, 2), 2, null);
                if (in_array($key, ['x1', 'y1', 'x2', 'y2'], true) && is_numeric($value)) {
                    $box[$key] = (int) round((float) $value);
                }
            }
            if (count($box) === 4 && $box['x2'] > $box['x1'] && $box['y2'] > $box['y1']) {
                $boxes[] = ['x1' => $box['x1'], 'y1' => $box['y1'], 'x2' => $box['x2'], 'y2' => $box['y2']];
            }
        }

        return $boxes;
    }

    /** The text of a Responses API answer: "output_text", or the message items' output_text parts. */
    public static function outputText(mixed $json): ?string
    {
        if (! is_array($json)) {
            return null;
        }
        if (is_string($json['output_text'] ?? null)) {
            return $json['output_text'];
        }
        $text = null;
        foreach ($json['output'] ?? [] as $item) {
            foreach (is_array($item['content'] ?? null) ? $item['content'] : [] as $content) {
                if (($content['type'] ?? null) === 'output_text' && is_string($content['text'] ?? null)) {
                    $text = ($text ?? '').$content['text'];
                }
            }
        }

        return $text;
    }
}
