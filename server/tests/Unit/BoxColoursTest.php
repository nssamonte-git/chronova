<?php

use App\Import\BoxColours;

/** A white PNG with a strong teal bar and a pale mint track, as on the test chart. */
function barsImage(): string
{
    $gd = imagecreatetruecolor(400, 100);
    imagefill($gd, 0, 0, imagecolorallocate($gd, 255, 255, 255));
    imagefilledrectangle($gd, 20, 20, 180, 45, imagecolorallocate($gd, 14, 235, 190));
    imagefilledrectangle($gd, 20, 60, 380, 85, imagecolorallocate($gd, 210, 248, 236));
    ob_start();
    imagepng($gd);

    return (string) ob_get_clean();
}

it('tells a strongly coloured bar from a pale track and white paper', function () {
    [$bar, $track, $paper, $off] = BoxColours::saturations(barsImage(), [
        ['x1' => 20, 'y1' => 20, 'x2' => 180, 'y2' => 45],
        ['x1' => 20, 'y1' => 60, 'x2' => 380, 'y2' => 85],
        ['x1' => 200, 'y1' => 5, 'x2' => 390, 'y2' => 15],
        ['x1' => 500, 'y1' => 500, 'x2' => 600, 'y2' => 600], // outside the image
    ]);

    expect($bar)->toBeGreaterThan(0.9)
        ->and($track)->toBeLessThan(0.2)
        ->and($paper)->toBe(0.0)
        ->and($off)->toBeNull();
});

it('answers null for every box when the image can\'t be read', function () {
    expect(BoxColours::saturations('not an image', [['x1' => 0, 'y1' => 0, 'x2' => 1, 'y2' => 1]]))->toBe([null])
        ->and(BoxColours::saturations('not an image', []))->toBe([]);
});
