"""
Builds every Chronova icon and logo file from the supplied logo image
(docs/brand/chronova-logo.webp), as is — no redraw. Re-run after changing
the source:  python3 tools/brand/generate.py   (needs Pillow)

Outputs
  app/src/main/res/drawable-nodpi/ic_launcher_art.png      adaptive icon layer (108 dp, logo art scaled into the safe zone)
  app/src/main/res/drawable-nodpi/ic_launcher_mono.png     themed-icon mask (Android 13+)
  app/src/main/res/drawable-nodpi/ic_splash_logo.png       launch-screen icon
  app/src/main/res/mipmap-*/ic_launcher(_round).webp       legacy launcher icons
  server/public/images/brand/*.png, server/public/favicon.ico   admin logo, favicons
"""
import math
import os
import statistics

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SOURCE = os.path.join(ROOT, "docs", "brand", "chronova-logo.webp")
RES = os.path.join(ROOT, "app", "src", "main", "res")
PUBLIC = os.path.join(ROOT, "server", "public")


def rounded(src: Image.Image) -> Image.Image:
    """The logo with its white surround made transparent, edges blended out of the white."""
    rgb = src.convert("RGB")
    w, h = rgb.size
    px = rgb.load()
    alpha = Image.new("L", (w, h), 0)
    ap = alpha.load()
    out = Image.new("RGBA", (w, h))
    op = out.load()

    def is_white(p):
        return min(p) >= 250

    def blend_alpha(edge, inner):
        # edge = a * inner + (1 - a) * white, per channel; take the most reliable channel.
        best = max(range(3), key=lambda c: 255 - inner[c])
        span = 255 - inner[best]
        return 1.0 if span < 40 else max(0.0, min(1.0, (255 - edge[best]) / span))

    def scan(line_len, get, put):
        coords = list(range(line_len))
        for order in (coords, coords[::-1]):
            hits = [i for i in order if not is_white(get(i))]
            if not hits:
                return False
        # interior: from the first to the last non-white pixel
        first = next(i for i in coords if not is_white(get(i)))
        last = next(i for i in coords[::-1] if not is_white(get(i)))
        for i in range(first, last + 1):
            put(i, 1.0)
        for edge, inner in ((first, min(first + 3, last)), (last, max(last - 3, first))):
            put(edge, blend_alpha(get(edge), get(inner)))
        return True

    rows = [[0.0] * w for _ in range(h)]
    cols = [[0.0] * h for _ in range(w)]
    for y in range(h):
        scan(w, lambda i, y=y: px[i, y], lambda i, a, y=y: rows[y].__setitem__(i, a))
    for x in range(w):
        scan(h, lambda i, x=x: px[x, i], lambda i, a, x=x: cols[x].__setitem__(i, a))
    for y in range(h):
        for x in range(w):
            a = min(rows[y][x], cols[x][y])
            ap[x, y] = round(a * 255)
            p = px[x, y]
            if 0 < a < 1:  # un-mix the white so the edge isn't light
                p = tuple(max(0, min(255, round((p[c] - (1 - a) * 255) / a))) for c in range(3))
            op[x, y] = (*p, round(a * 255))
    # The outermost pixels still carry a little of the white: paint a 3 px rim with the
    # background navy (the art never reaches the edge) so no light outline shows on dark.
    rim = alpha.filter(ImageFilter.MinFilter(7))
    navy = navy_of(rgb.crop((w // 4, h // 4, 3 * w // 4, 3 * h // 4)))
    rp = rim.load()
    for y in range(h):
        for x in range(w):
            if ap[x, y] and rp[x, y] < 255:
                op[x, y] = (*navy, ap[x, y])
    bbox = alpha.getbbox()
    return out.crop(bbox)


def edge_navy(art: Image.Image):
    """The art's own edge colour (median of its outer ring), so the fill around it shows no step."""
    small = art.convert("RGB").resize((100, 100))
    ring = [small.getpixel((x, y)) for y in range(100) for x in range(100) if min(x, y, 99 - x, 99 - y) < 4]
    return tuple(int(statistics.median(p[c] for p in ring)) for c in range(3))


def navy_of(art: Image.Image):
    """The background colour: the median of the dark pixels."""
    small = art.convert("RGB").resize((128, 128))
    dark = [p for p in small.get_flattened_data() if sum(p) < 110]
    return tuple(int(statistics.median(p[c] for p in dark)) for c in range(3))


def full_bleed(logo: Image.Image) -> Image.Image:
    """The largest square inside the rounded corners (all art, no transparency)."""
    w, h = logo.size
    r = 0.275 * w  # corner radius, measured from the source (≈ 340 of 1235 px)
    inset = math.ceil(r * (1 - 1 / math.sqrt(2))) + 4
    return logo.crop((inset, inset, w - inset, h - inset)).convert("RGB")


def composed(art: Image.Image, navy, size: int, art_fraction: float, feather: float) -> Image.Image:
    """[art] centred on a navy square of [size], scaled to [art_fraction] of it, edges feathered into the navy."""
    canvas = Image.new("RGB", (size, size), navy)
    side = round(size * art_fraction)
    scaled = art.resize((side, side), Image.LANCZOS)
    mask = Image.new("L", (side, side), 0)
    f = max(1, round(side * feather))
    ImageDraw.Draw(mask).rectangle((f, f, side - 1 - f, side - 1 - f), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(f / 2))
    offset = ((size - side) // 2, (size - side) // 2)
    canvas.paste(scaled, offset, mask)
    return canvas


def monochrome(art: Image.Image, size: int, art_fraction: float) -> Image.Image:
    """White glyph on transparent: the bright hourglass, bars and dot (by brightness)."""
    side = round(size * art_fraction)
    scaled = art.resize((side, side), Image.LANCZOS).convert("RGB")
    a = Image.new("L", scaled.size)
    # The glyph is near full brightness; the glow arcs peak lower, so they drop out.
    a.putdata([max(0, min(255, round((max(p) - 185) * 255 / 50))) for p in scaled.get_flattened_data()])
    # Opening (erode, then dilate) removes what's left of the thin arcs; the outline, bars and dot are far thicker.
    a = a.filter(ImageFilter.MinFilter(5)).filter(ImageFilter.MaxFilter(5))
    a = without_specks(a)
    out = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    white = Image.new("RGBA", scaled.size, (255, 255, 255, 255))
    off = (size - side) // 2
    out.paste(white, (off, off), a)
    return out


def without_specks(mask: Image.Image, keep_fraction: float = 0.01) -> Image.Image:
    """Drops connected blobs smaller than [keep_fraction] of the mask's total area."""
    w, h = mask.size
    px = mask.load()
    seen = bytearray(w * h)
    total = sum(1 for v in mask.get_flattened_data() if v)
    out = mask.copy()
    op = out.load()
    for start in range(w * h):
        if seen[start] or not px[start % w, start // w]:
            continue
        blob, stack = [], [start]
        seen[start] = 1
        while stack:
            i = stack.pop()
            blob.append(i)
            x, y = i % w, i // w
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                j = ny * w + nx
                if 0 <= nx < w and 0 <= ny < h and not seen[j] and px[nx, ny]:
                    seen[j] = 1
                    stack.append(j)
        if len(blob) < total * keep_fraction:
            for i in blob:
                op[i % w, i // w] = 0
    return out


def circle(img: Image.Image) -> Image.Image:
    s = img.size[0]
    big = Image.new("L", (s * 4, s * 4), 0)
    ImageDraw.Draw(big).ellipse((0, 0, s * 4 - 1, s * 4 - 1), fill=255)
    out = img.convert("RGBA")
    out.putalpha(big.resize((s, s), Image.LANCZOS))
    return out


def save(img: Image.Image, *parts, **kwargs):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, **kwargs)
    print("wrote", os.path.relpath(path, ROOT), img.size)


def main():
    logo = rounded(Image.open(SOURCE))
    art = full_bleed(logo)
    navy = navy_of(art)
    print("navy #%02x%02x%02x" % navy)

    # Adaptive icon: 108 dp layer, 432 px (xxxhdpi). The art covers 62% of it, so the
    # hourglass (its rounded corners included) stays inside the 66 dp safe circle.
    ART_FRACTION = 0.62
    fill = edge_navy(art)
    print("edge fill #%02x%02x%02x" % fill)
    adaptive = composed(art, fill, 432, ART_FRACTION, feather=0.05)
    save(adaptive, RES, "drawable-nodpi", "ic_launcher_art.png", optimize=True)
    save(monochrome(art, 432, ART_FRACTION), RES, "drawable-nodpi", "ic_launcher_mono.png", optimize=True)

    # Launch screen: icon area 288 dp, content inside a 192 dp circle (xxhdpi, 864 px).
    save(composed(art, fill, 864, ART_FRACTION, feather=0.05), RES, "drawable-nodpi", "ic_splash_logo.png", optimize=True)

    # Legacy launcher icons (unused with adaptive icons on API 26+, kept consistent).
    visible = adaptive.crop((72, 72, 360, 360))  # the 72 dp a launcher shows
    for density, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        square = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        inner = round(px * 0.92)
        square.alpha_composite(logo.resize((inner, inner), Image.LANCZOS), ((px - inner) // 2, (px - inner) // 2))
        save(square, RES, f"mipmap-{density}", "ic_launcher.webp", lossless=True)
        save(circle(visible.resize((px, px), Image.LANCZOS)), RES, f"mipmap-{density}", "ic_launcher_round.webp", lossless=True)

    # Admin panel and browser icons.
    save(logo.resize((256, 256), Image.LANCZOS), PUBLIC, "images", "brand", "chronova-logo.png", optimize=True)
    save(logo.resize((32, 32), Image.LANCZOS), PUBLIC, "images", "brand", "favicon-32.png", optimize=True)
    save(art.resize((180, 180), Image.LANCZOS), PUBLIC, "images", "brand", "apple-touch-icon.png", optimize=True)  # iOS rounds it
    save(logo.resize((48, 48), Image.LANCZOS), PUBLIC, "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])


if __name__ == "__main__":
    main()
