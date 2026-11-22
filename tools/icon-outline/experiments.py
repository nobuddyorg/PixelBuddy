"""Renders comparison sheets for the outline prototype.

    python3 experiments.py out/                       # synthetic icons
    python3 experiments.py out/ --icons ~/icons/      # plus your own PNGs (never commit brand icons)

Each row: original, sampled, sweep, single colour on the launcher gradient, the same at launcher size
(120 px, about 46 dp on a Galaxy S24) and a transparent version.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import synth
from outline import Params, extract, launcher_background, load, render

CELL = 260
LAUNCHER = 120


def sheet(icons: dict[str, np.ndarray], params: Params) -> Image.Image:
    cols = ["original", "sampled", "sweep", "single", "launcher size", "transparent"]
    out = Image.new("RGBA", (CELL * len(cols), (CELL + 24) * len(icons) + 24), (20, 24, 51, 255))
    draw = ImageDraw.Draw(out)
    for i, name in enumerate(cols):
        draw.text((i * CELL + 8, 6), name, fill=(242, 238, 255, 255))
    for row, (name, icon) in enumerate(icons.items()):
        y = 24 + row * (CELL + 24)
        draw.text((8, y + CELL + 4), name, fill=(180, 173, 207, 255))
        original = Image.fromarray((icon * 255).astype(np.uint8), "RGBA").resize((CELL - 40, CELL - 40), Image.LANCZOS)
        bg = launcher_background(CELL)
        bg.alpha_composite(original, (20, 20))
        out.alpha_composite(bg, (0, y))
        outline = extract(icon, params)
        for col, mode in enumerate(("sampled", "sweep", "single"), start=1):
            out.alpha_composite(Image.alpha_composite(launcher_background(CELL), render(outline, CELL, mode, params)), (col * CELL, y))
        small = Image.alpha_composite(launcher_background(CELL), Image.new("RGBA", (CELL, CELL)))
        icon_small = render(outline, LAUNCHER, "sampled", params)
        small.alpha_composite(icon_small, ((CELL - LAUNCHER) // 2, (CELL - LAUNCHER) // 2))
        out.alpha_composite(small, (4 * CELL, y))
        checker = Image.new("RGBA", (CELL, CELL), (60, 60, 60, 255))
        cd = ImageDraw.Draw(checker)
        for cy in range(0, CELL, 20):
            for cx in range(0, CELL, 20):
                if (cx + cy) // 20 % 2:
                    cd.rectangle([cx, cy, cx + 19, cy + 19], fill=(90, 90, 90, 255))
        checker.alpha_composite(render(outline, CELL, "sampled", params, glow=False))
        out.alpha_composite(checker, (5 * CELL, y))
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--icons", help="folder with PNG icons to compare (kept out of the repository)")
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    icons = {name: make() for name, make in synth.ALL.items()}
    if args.icons:
        for png in sorted(Path(args.icons).glob("*.png")):
            icons[png.stem] = load(png)
    sheet(icons, Params()).save(out / "outline-default.png")
    thin = Params(outer_width=0.028, inner_width=0.018, glow=((1.6, 0.28), (2.6, 0.16)))
    sheet(icons, thin).save(out / "outline-thin-subtle-glow.png")
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
