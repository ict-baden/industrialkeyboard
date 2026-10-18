"""Renders the launcher icon (PNG per density) and the README logo.

Usage: python tools/icon/make_icon.py <repo>
PNG icons only (no adaptive XML), because some MDM consoles cannot display XML icons.
"""
import pathlib
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

repo = pathlib.Path(sys.argv[1])
res = repo / "app" / "src" / "main" / "res"
S = 1024


def gradient(size, start, end):
    """Diagonal gradient from top-left (start) to bottom-right (end)."""
    w, h = size
    base = Image.new("RGB", size, start)
    top = Image.new("RGB", size, end)
    mask = Image.linear_gradient("L").rotate(-45, expand=True).resize((w * 2, h * 2))
    mask = mask.crop((w // 2, h // 2, w // 2 + w, h // 2 + h))
    return Image.composite(top, base, mask)


def artwork():
    img = gradient((S, S), (26, 35, 126), (0, 151, 167)).convert("RGBA")
    # Soft light from the top left.
    glow = Image.new("L", (S, S), 0)
    ImageDraw.Draw(glow).ellipse((-300, -360, 620, 520), fill=120)
    glow = glow.filter(ImageFilter.GaussianBlur(160))
    img = Image.composite(Image.new("RGBA", (S, S), (255, 255, 255, 255)), img, glow.point(lambda v: v // 3))

    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    # Keyboard body with key cut-outs.
    body = (176, 336, 848, 760)
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((body[0], body[1] + 26, body[2], body[3] + 26), 72, fill=(0, 20, 60, 110))
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(26)))
    d.rounded_rectangle(body, 72, fill=(255, 255, 255, 255))
    key = (46, 70, 140, 255)
    cols, gap, left, top = 7, 18, body[0] + 52, body[1] + 54
    kw = (body[2] - body[0] - 104 - gap * (cols - 1)) / cols
    kh = 74
    for row in range(3):
        offset = (kw + gap) / 2 if row == 1 else 0
        count = cols - (1 if row == 1 else 0)
        for c in range(count):
            x = left + offset + c * (kw + gap)
            y = top + row * (kh + gap)
            d.rounded_rectangle((x, y, x + kw, y + kh), 18, fill=key)
    d.rounded_rectangle((body[0] + 52 + kw + gap, top + 3 * (kh + gap), body[2] - 52 - kw - gap, top + 3 * (kh + gap) + kh * 0.8), 18, fill=key)
    img.alpha_composite(layer)

    # Amber scan beam with glow across the keyboard.
    beam = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bd = ImageDraw.Draw(beam)
    y = 512
    bd.rounded_rectangle((112, y - 16, 912, y + 16), 16, fill=(255, 196, 0, 255))
    halo = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(halo).rounded_rectangle((90, y - 46, 934, y + 46), 46, fill=(255, 171, 0, 170))
    img.alpha_composite(halo.filter(ImageFilter.GaussianBlur(30)))
    img.alpha_composite(beam)
    ImageDraw.Draw(img).rounded_rectangle((140, y - 5, 884, y + 5), 5, fill=(255, 248, 225, 255))
    return img


def masked(img, round_icon):
    mask = Image.new("L", (S, S), 0)
    if round_icon:
        ImageDraw.Draw(mask).ellipse((0, 0, S - 1, S - 1), fill=255)
    else:
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), int(S * 0.22), fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(img, (0, 0), ImageChops.multiply(mask, img.getchannel("A")))
    return out


art = artwork()
densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, size in densities.items():
    folder = res / f"mipmap-{name}"
    folder.mkdir(parents=True, exist_ok=True)
    # Legacy launcher icons leave a small margin around the shape.
    for round_icon, filename in ((False, "ic_launcher.png"), (True, "ic_launcher_round.png")):
        icon = masked(art, round_icon).resize((int(size * 0.92),) * 2, Image.LANCZOS)
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        canvas.alpha_composite(icon, ((size - icon.width) // 2, (size - icon.height) // 2))
        canvas.save(folder / filename, optimize=True)
nodpi = res / "drawable-nodpi"
nodpi.mkdir(parents=True, exist_ok=True)
masked(art, False).resize((256, 256), Image.LANCZOS).save(nodpi / "logo.png", optimize=True)
docs = repo / "docs" / "images"
docs.mkdir(parents=True, exist_ok=True)
masked(art, False).resize((256, 256), Image.LANCZOS).save(docs / "logo.png", optimize=True)
play = repo / "fastlane" / "metadata" / "android" / "en-US" / "images"
play.mkdir(parents=True, exist_ok=True)
# Google Play hi-res icon: full square, Play rounds the corners itself
art.convert("RGB").resize((512, 512), Image.LANCZOS).save(play / "icon.png", optimize=True)
print("icons written")
