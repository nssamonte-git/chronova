<?php

namespace App\Import;

/**
 * Puts the shapes SAM found on the rows Muse Spark read, and measures each bar in the chart's own
 * columns ("Week 1" = column 0 to 1), so the builder gets numbers rather than pixels:
 * - Muse Spark's pixel positions drift by tens of pixels, so the geometry comes from SAM's boxes when
 *   it can: boxes grouped into horizontal rows give the rows' centres when the groups match the rows
 *   read (in order), and full-width tracks give the axis' ends, split into as many columns as it has
 *   labels; otherwise (a photo at an angle, a chart without tracks) Muse Spark's positions are used;
 * - a shape belongs to the row whose band (halfway to the rows above and below) holds its centre;
 * - a row keeps the bars nearly as strongly coloured as its strongest (SAM also finds the pale
 *   track behind the bar, often in pieces either side of it); without colours, or when nothing in
 *   the row is coloured, a full-width track is dropped when a shorter bar shares the row;
 * - boxes that cover the same span twice count once;
 * - what Muse Spark saw of each row's bar ("WEEK 1" to "WEEK 2") is passed on beside the measurements;
 * - columns come from the time-axis labels: their spacing is a column, the first label's centre
 *   is the middle of column 0.
 */
final class BarMatcher
{
    /** A bar this much of the axis wide, beside a shorter one, is the row's background track. */
    private const TRACK_SHARE = 0.9;

    /** A bar keeps its place when its colour is at least this share of the row's strongest… */
    private const COLOUR_SHARE = 0.5;

    /** …once that strongest is a colour at all, not grey or white. */
    private const COLOURED = 0.25;

    /**
     * @return array{axis: array{unit: string, labels: list<string>, columns: int}, rows: list<array{label: string, text: string, bars: list<array{0: float, 1: float}>, milestones: list<float>, seen: array{from: string, to: string}}>, links: list<array{from: string, to: string}>, title: string, kind: string, notes: list<string>}
     */
    public static function match(ChartAnalysis $analysis): array
    {
        $reading = $analysis->reading;
        $labels = self::axisLabels($reading);
        [$origin, $column] = self::trackColumns($analysis->shapes, count($labels)) ?? self::columns($labels);

        $rows = array_values(array_filter($reading['rows'] ?? [], fn ($row) => is_array($row) && is_string($row['label'] ?? null) && is_numeric($row['y'] ?? null)));
        usort($rows, fn ($a, $b) => $a['y'] <=> $b['y']);
        $centres = self::shapeRows($analysis->shapes, count($rows)) ?? array_map(fn ($row) => (float) $row['y'], $rows);
        $bands = self::bands($centres);

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
            $bars = self::strongest($found[$i]['bars']);
            $short = array_filter($bars, fn ($bar) => ($bar['x2'] - $bar['x1']) < self::TRACK_SHARE * $axisWidth);
            if ($short !== []) {
                $bars = $short;
            }
            usort($bars, fn ($a, $b) => $a['x1'] <=> $b['x1']);
            $bars = self::distinct($bars);
            $milestones = array_map(fn ($m) => $toColumn(($m['x1'] + $m['x2']) / 2), $found[$i]['milestones']);
            sort($milestones);
            $matched[] = [
                'label' => trim($row['label']),
                'text' => is_string($row['text'] ?? null) ? trim($row['text']) : '',
                'bars' => array_values(array_map(fn ($bar) => [$toColumn($bar['x1']), $toColumn($bar['x2'])], $bars)),
                'milestones' => $milestones,
                'seen' => ['from' => self::text($row['barStart'] ?? null), 'to' => self::text($row['barEnd'] ?? null)],
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

    /**
     * The axis' left edge and column width from full-width tracks: at least two bar boxes that both
     * start at the leftmost box's edge and end at the rightmost's (within 1% of the span). Null without them.
     *
     * @return array{0: float, 1: float}|null
     */
    private static function trackColumns(array $shapes, int $labels): ?array
    {
        $bars = array_values(array_filter($shapes, fn ($shape) => $shape['kind'] === 'bar'));
        if ($labels < 1 || count($bars) < 2) {
            return null;
        }
        $left = min(array_column($bars, 'x1'));
        $right = max(array_column($bars, 'x2'));
        $slack = 0.01 * ($right - $left);
        $tracks = array_filter($bars, fn ($bar) => $slack >= $bar['x1'] - $left && $right - $bar['x2'] <= $slack);

        return count($tracks) >= 2 && $right > $left ? [(float) $left, ($right - $left) / $labels] : null;
    }

    /**
     * The rows' vertical centres from the shapes, top to bottom: boxes whose centres lie within half a
     * box's height of each other form one row. Null unless there are exactly [count] such rows.
     *
     * @return list<float>|null
     */
    private static function shapeRows(array $shapes, int $count): ?array
    {
        if ($count === 0 || $shapes === []) {
            return null;
        }
        $centres = array_map(fn ($shape) => ($shape['y1'] + $shape['y2']) / 2, $shapes);
        sort($centres);
        $heights = array_map(fn ($shape) => $shape['y2'] - $shape['y1'], $shapes);
        sort($heights);
        $tolerance = max(2, $heights[intdiv(count($heights), 2)] / 2);

        $groups = [[$centres[0]]];
        foreach (array_slice($centres, 1) as $centre) {
            $group = &$groups[count($groups) - 1];
            if ($centre - (array_sum($group) / count($group)) <= $tolerance) {
                $group[] = $centre;
            } else {
                $groups[] = [$centre];
            }
            unset($group);
        }

        return count($groups) === $count ? array_map(fn ($group) => array_sum($group) / count($group), $groups) : null;
    }

    /** The bars coloured nearly as strongly as the row's strongest; all of them when colours are unknown or faint. */
    private static function strongest(array $bars): array
    {
        $known = array_filter(array_column($bars, 'saturation'), fn ($s) => $s !== null);
        if ($known === [] || max($known) < self::COLOURED) {
            return $bars;
        }
        $floor = self::COLOUR_SHARE * max($known);

        return array_values(array_filter($bars, fn ($bar) => ($bar['saturation'] ?? 0) >= $floor));
    }

    /** Drops a box that covers (nearly) the same span as one already kept; [bars] are sorted by x1. */
    private static function distinct(array $bars): array
    {
        $kept = [];
        foreach ($bars as $bar) {
            $last = end($kept);
            if ($last !== false) {
                $overlap = min($last['x2'], $bar['x2']) - max($last['x1'], $bar['x1']);
                $shorter = min($last['x2'] - $last['x1'], $bar['x2'] - $bar['x1']);
                if ($shorter > 0 && $overlap >= 0.9 * $shorter) {
                    continue;
                }
            }
            $kept[] = $bar;
        }

        return $kept;
    }

    private static function text(mixed $value): string
    {
        return is_string($value) ? trim($value) : '';
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
