<?php

use App\Import\BarMatcher;
use App\Import\ChartAnalysis;
use App\Import\FakeChartAnalyzer;

function chartWith(array $rows, array $shapes, array $labels = [['text' => 'W1', 'x' => 150], ['text' => 'W2', 'x' => 250], ['text' => 'W3', 'x' => 350]]): ChartAnalysis
{
    return new ChartAnalysis(['kind' => 'gantt', 'title' => 'Plan', 'axis' => ['unit' => 'week', 'labels' => $labels], 'rows' => $rows, 'links' => [], 'notes' => []], $shapes, 500, 300);
}

function bar(int $x1, int $x2, int $y, string $kind = 'bar'): array
{
    return ['kind' => $kind, 'x1' => $x1, 'y1' => $y - 10, 'x2' => $x2, 'y2' => $y + 10];
}

it('measures each row\'s bar in the axis\' columns, dropping the full-width track', function () {
    $chart = BarMatcher::match((new FakeChartAnalyzer)->analyze('', 'image/png', 1640, 1050));

    expect($chart['axis'])->toBe(['unit' => 'week', 'labels' => ['WEEK 1', 'WEEK 2', 'WEEK 3', 'WEEK 4', 'WEEK 5', 'WEEK 6', 'WEEK 7'], 'columns' => 7])
        // Columns are 188 px (the labels' median spacing); column 0 starts at 366 - 94 = 272.
        ->and($chart['rows'][0])->toMatchArray(['label' => 'STEP 1: Initial Client Consult', 'bars' => [[0.01, 0.99]]])
        ->and($chart['rows'][1]['bars'])->toBe([[0.01, 1.99]])
        ->and($chart['rows'][8]['bars'])->toBe([[6.0, 6.98]]);
});

it('puts a shape on the row whose band holds its centre, in reading order', function () {
    $chart = BarMatcher::match(chartWith(
        [['label' => 'Second', 'y' => 200], ['label' => 'First', 'y' => 100]],
        [bar(100, 200, 140), bar(200, 300, 160), bar(150, 150, 210, 'milestone')],
    ));

    expect(array_column($chart['rows'], 'label'))->toBe(['First', 'Second'])
        ->and($chart['rows'][0]['bars'])->toBe([[0.0, 1.0]])            // 140 is above the 150 midline
        ->and($chart['rows'][1]['bars'])->toBe([[1.0, 2.0]])
        ->and($chart['rows'][1]['milestones'])->toBe([0.5]);
});

it('keeps a split row\'s pieces in order, and a lone full-width bar', function () {
    $chart = BarMatcher::match(chartWith(
        [['label' => 'Split', 'y' => 100], ['label' => 'All along', 'y' => 200]],
        [bar(250, 300, 100), bar(100, 150, 100), bar(100, 400, 200)],
    ));

    expect($chart['rows'][0]['bars'])->toBe([[0.0, 0.5], [1.5, 2.0]])
        ->and($chart['rows'][1]['bars'])->toBe([[0.0, 3.0]]);
});

it('falls back to shares of the image width without an axis', function () {
    $chart = BarMatcher::match(chartWith([['label' => 'Only', 'y' => 100]], [bar(100, 350, 100)], labels: []));

    expect($chart['axis'])->toBe(['unit' => 'image_width', 'labels' => [], 'columns' => 0])
        ->and($chart['rows'][0]['bars'])->toBe([[0.2, 0.7]]);
});

it('ignores rows and links it cannot read', function () {
    $analysis = new ChartAnalysis(['rows' => [['label' => 'Fine', 'y' => 10], ['label' => 3, 'y' => 20], ['y' => 30]], 'links' => [['from' => 'a'], ['from' => 'a', 'to' => 'b']]], [], 100, 100);

    expect(BarMatcher::match($analysis))->toMatchArray([
        'kind' => 'other', 'title' => '',
        'rows' => [['label' => 'Fine', 'text' => '', 'bars' => [], 'milestones' => []]],
        'links' => [['from' => 'a', 'to' => 'b']],
    ]);
});
