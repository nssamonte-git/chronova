<?php

namespace App\Import;

use GdImage;

/**
 * How strongly coloured each box is, from the image itself: a Gantt chart's bars are saturated,
 * the pale tracks behind them are not, and SAM finds both. Saturation is (max − min) / max of
 * the RGB channels, averaged over a grid inside the box (its edges left out), 0 to 1.
 */
final class BoxColours
{
    /**
     * @param  list<array{x1: int, y1: int, x2: int, y2: int}>  $boxes
     * @return list<?float> each box's saturation, or null when the image can't be read
     */
    public static function saturations(string $image, array $boxes): array
    {
        $gd = $boxes === [] ? false : @imagecreatefromstring($image);
        if (! $gd instanceof GdImage) {
            return array_fill(0, count($boxes), null);
        }
        $width = imagesx($gd);
        $height = imagesy($gd);
        $result = [];
        foreach ($boxes as $box) {
            $result[] = self::saturation($gd, $box, $width, $height);
        }

        return $result;
    }

    private static function saturation(GdImage $gd, array $box, int $width, int $height): ?float
    {
        $insetX = ($box['x2'] - $box['x1']) * 0.2;
        $insetY = ($box['y2'] - $box['y1']) * 0.25;
        $left = max(0, (int) ($box['x1'] + $insetX));
        $right = min($width - 1, (int) ($box['x2'] - $insetX));
        $top = max(0, (int) ($box['y1'] + $insetY));
        $bottom = min($height - 1, (int) ($box['y2'] - $insetY));
        if ($right < $left || $bottom < $top) {
            return null;
        }
        $sum = 0.0;
        $count = 0;
        foreach (self::steps($left, $right, 12) as $x) {
            foreach (self::steps($top, $bottom, 4) as $y) {
                $rgb = imagecolorat($gd, $x, $y);
                $colour = imagecolorsforindex($gd, $rgb);
                $max = max($colour['red'], $colour['green'], $colour['blue']);
                $min = min($colour['red'], $colour['green'], $colour['blue']);
                $sum += $max === 0 ? 0.0 : ($max - $min) / $max;
                $count++;
            }
        }

        return round($sum / $count, 3);
    }

    /** @return list<int> [count] evenly spread points from $from to $to */
    private static function steps(int $from, int $to, int $count): array
    {
        if ($to <= $from) {
            return [$from];
        }

        return array_values(array_unique(array_map(fn ($i) => (int) round($from + ($to - $from) * $i / ($count - 1)), range(0, $count - 1))));
    }
}
