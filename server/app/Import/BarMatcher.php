<?php

namespace App\Import;

/**
 * Puts the shapes SAM found on the rows Muse Spark read, and measures each bar in the chart's own
 * columns ("Week 1" = column 0 to 1), so the builder gets numbers rather than pixels:
 * - a shape belongs to the row whose band (halfway to the rows above and below) holds its centre;
 * - a row's pale full-width track is dropped when a shorter bar shares the row;
 * - columns come from the time-axis labels: their spacing is a column, the first label's centre
 *   is the middle of column 0.
 */
final class BarMatcher
{
    /** A bar this much of the axis wide, beside a shorter one, is the row's background track. */
    private const TRACK_SHARE = 0.9;

    /**
     * @return array{axis: array{unit: string, labels: list<string>, columns: int}, rows: list<array{label: string, text: string, bars: list<array{0: float, 1: float}>, milestones: list<float>}>, links: list<array{from: string, to: string}>, title: string, kind: string, notes: list<string>}
     */
    public static function match(ChartAnalysis $analysis): array
    {
        $reading = $analysis->reading;
        $labels = self::axisLabels($reading);
        [$origin, $column] = self::columns($labels);

        $rows = array_values(array_filter($reading['rows'] ?? [], fn ($row) => is_array($row) && is_string($row['label'] ?? null) && is_numeric($row['y'] ?? null)));
        usort($rows, fn ($a, $b) => $a['y'] <=> $b['y']);
        $bands = self::bands(array_map(fn ($row) => (float) $row['y'], $rows));

        $found = array_fill(0, count($rows), ['bars' => [], 'milestones' => []]);
        foreach ($analysis->shapes as $shape) {
            $centre = ($shape['y1'] + $shape['y2']) / 2;
            foreach ($bands as $i => [$top, $bottom]) {
                if ($centre >= $top && $centre < $bottom) {
                    $found[$i][$shape['kind'] === 'milestone' ? 'milestones' : 'bars'][] = $shape;
                    break;
                }
            }
        }

        $axisWidth = $column !== null ? $column * max(count($labels), 1) : $analysis->width;
        $toColumn = fn (float $x): float => $column !== null ? round(($x - $origin) / $column, 2) : round($x / max($analysis->width, 1), 3);

        $matched = [];
        foreach ($rows as $i => $row) {
            $bars = $found[$i]['bars'];
            $short = array_filter($bars, fn ($bar) => ($bar['x2'] - $bar['x1']) < self::TRACK_SHARE * $axisWidth);
            if ($short !== []) {
                $bars = $short;
            }
            usort($bars, fn ($a, $b) => $a['x1'] <=> $b['x1']);
            $milestones = array_map(fn ($m) => $toColumn(($m['x1'] + $m['x2']) / 2), $found[$i]['milestones']);
            sort($milestones);
            $matched[] = [
                'label' => trim($row['label']),
                'text' => is_string($row['text'] ?? null) ? trim($row['text']) : '',
                'bars' => array_values(array_map(fn ($bar) => [$toColumn($bar['x1']), $toColumn($bar['x2'])], $bars)),
                'milestones' => $milestones,
            ];
        }

        return [
            'kind' => is_string($reading['kind'] ?? null) ? $reading['kind'] : 'other',
            'title' => is_string($reading['title'] ?? null) ? trim($reading['title']) : '',
            'axis' => [
                // Without labels there are no columns: positions are then shares of the image's width.
                'unit' => $column !== null && is_string($reading['axis']['unit'] ?? null) ? $reading['axis']['unit'] : 'image_width',
                'labels' => array_map(fn ($label) => $label['text'], $labels),
                'columns' => count($labels),
            ],
            'rows' => $matched,
            'links' => array_values(array_filter($reading['links'] ?? [], fn ($link) => is_string($link['from'] ?? null) && is_string($link['to'] ?? null))),
            'notes' => array_values(array_filter($reading['notes'] ?? [], 'is_string')),
        ];
    }

    /** @return list<array{text: string, x: float}> the time-axis labels, left to right */
    private static function axisLabels(array $reading): array
    {
        $labels = [];
        foreach ($reading['axis']['labels'] ?? [] as $label) {
            if (is_string($label['text'] ?? null) && is_numeric($label['x'] ?? null)) {
                $labels[] = ['text' => trim($label['text']), 'x' => (float) $label['x']];
            }
        }
        usort($labels, fn ($a, $b) => $a['x'] <=> $b['x']);

        return $labels;
    }

    /** @return array{0: float, 1: ?float} column 0's left edge and a column's width, in pixels */
    private static function columns(array $labels): array
    {
        if (count($labels) < 2) {
            return [0.0, null];
        }
        $gaps = [];
        for ($i = 1; $i < count($labels); $i++) {
            $gaps[] = $labels[$i]['x'] - $labels[$i - 1]['x'];
        }
        sort($gaps);
        $column = $gaps[intdiv(count($gaps), 2)];
        if ($column <= 0) {
            return [0.0, null];
        }

        return [$labels[0]['x'] - $column / 2, $column];
    }

    /**
     * @param  list<float>  $centres  rows' vertical centres, top to bottom
     * @return list<array{0: float, 1: float}> each row's band, top (inclusive) to bottom (exclusive)
     */
    private static function bands(array $centres): array
    {
        $n = count($centres);
        if ($n === 0) {
            return [];
        }
        $gaps = [];
        for ($i = 1; $i < $n; $i++) {
            $gaps[] = $centres[$i] - $centres[$i - 1];
        }
        sort($gaps);
        $half = $gaps === [] ? 50.0 : $gaps[intdiv(count($gaps), 2)] / 2;

        $bands = [];
        for ($i = 0; $i < $n; $i++) {
            $top = $i === 0 ? $centres[0] - $half : ($centres[$i - 1] + $centres[$i]) / 2;
            $bottom = $i === $n - 1 ? $centres[$i] + $half : ($centres[$i] + $centres[$i + 1]) / 2;
            $bands[] = [$top, $bottom];
        }

        return $bands;
    }
}
