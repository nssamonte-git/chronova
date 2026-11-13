<?php

namespace App\Import;

/**
 * Development and tests: always "sees" the test chart (tests/Fixtures/import/project-timeline.png,
 * 1640×1050: nine steps over seven weeks), as Meta would report it, including the pale full-width
 * track behind each bar. No network; the image is ignored.
 */
final class FakeChartAnalyzer implements ChartAnalyzer
{
    /** Bars by row: [x1, x2]; rows' centres are 231 + 55.4 × step. */
    private const BARS = [[274, 459], [274, 646], [649, 833], [835, 1020], [1022, 1208], [1211, 1397], [1211, 1397], [1400, 1584], [1400, 1584]];

    private const STEPS = [
        'Initial Client Consult', 'Project Design Inquiry', 'Initial Design Concept', 'Release Design For Bid',
        '2nd Design Concept', 'Sales Presentation & Proposals', 'Purchasing of Materials', 'Install', 'Review Finished Project',
    ];

    public function analyze(string $image, string $mime, int $width, int $height): ChartAnalysis
    {
        $rows = [];
        $shapes = [];
        foreach (self::STEPS as $i => $title) {
            $y = (int) round(231 + 55.4 * $i);
            $rows[] = ['label' => 'STEP '.($i + 1).': '.$title, 'y' => $y, 'text' => ''];
            [$x1, $x2] = self::BARS[$i];
            $shapes[] = ['kind' => 'bar', 'x1' => 274, 'y1' => $y - 15, 'x2' => 1583, 'y2' => $y + 15]; // the track
            $shapes[] = ['kind' => 'bar', 'x1' => $x1, 'y1' => $y - 15, 'x2' => $x2, 'y2' => $y + 15];
        }
        $weeks = [366, 553, 740, 930, 1120, 1308, 1490];

        return new ChartAnalysis(
            reading: [
                'kind' => 'gantt',
                'title' => 'PROJECT TIMELINE',
                'axis' => ['unit' => 'week', 'labels' => array_map(fn ($x, $i) => ['text' => 'WEEK '.($i + 1), 'x' => $x], $weeks, array_keys($weeks))],
                'rows' => $rows,
                'links' => [],
                'notes' => ['Each week lists its activities below the chart, e.g. "Review terms and sign contract with client."'],
            ],
            shapes: $shapes,
            width: 1640,
            height: 1050,
            inputTokens: 0,
            outputTokens: 0,
        );
    }
}
