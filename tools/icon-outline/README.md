# Outline icon prototype (#29)

Python prototype of the automatic neon outline renderer: it turns an app icon into smooth, consistently thick
outline strokes with a soft, tinted glow. The prototype settles the algorithm and its defaults. The app uses the
Kotlin port in `:core:pixel-engine` (package `outline`, `IconOutline.trace` and `IconOutline.render`), tested on the
same synthetic icons. Differences in the port:

- Rendering is pure Kotlin too: analytic anti-aliased capsules at the target size (no supersampling) and the wide
  glow as three box blurs, so outputs are deterministic and checked against golden images.
- k-means fits its centres on at most 4096 evenly spaced pixels (Kotlin's random numbers differ from NumPy's anyway).
- `sweep` spreads the colour stops evenly over the icon along the sweep direction, so colours whose centres sit
  close together (a disc on a plate) still blend gradually.

In the app the outlines are the "Neon lines" and "Neon, one colour" presets, and "Drawn as: Neon lines" in the
icon editor (colours, glow, pixelated). Adaptive icons are traced from their monochrome layer if they have one,
otherwise from their foreground layer (the background layer is the plate, so it doesn't have to be guessed); the
colours, and the plate colour of a light glyph, come from the full icon. Smooth outlines are drawn at the size on
screen (48 dp); pixelated ones at the grid size without glow. Imported pictures are traced like icons; album art
stays pixel art.

Render times on the phone itself (release build, from drawing the app's icon to the finished bitmap) are shown under
Settings → About: median and 90th percentile per icon, for neon and pixel icons. On the CI emulators,
`NeonIconsOnDeviceTest` renders every installed app's real icon in the neon styles and logs the times (debug build,
only a loose regression limit).

Only NumPy and Pillow are used, and only for primitives that are easy to write in Kotlin without OpenCV
(`primitives.py`): Lab conversion, Gaussian blur, Sobel, connected components, distance transform, k-means.

## Pipeline

1. **Source:** alpha mask; opaque icons get their background estimated from the border. "White glyph on a uniform
   plate" icons outline the glyph and take the colour from the plate.
2. **Segmentation:** fine colour clusters in Lab. Specks (anti-aliased edge pixels) join their most similar
   neighbour first; then neighbouring regions are merged agglomeratively while less than 30 % of their shared
   border is a strong edge (judged away from junctions). Thin slivers fold into a neighbour, so two nearby parallel
   edges become one stroke.
3. **Contours:** every region border is traced once along the crack edges between pixels; one border, one stroke.
4. **Smoothing and cleanup:** Gaussian smoothing along the arc length with pinned chain ends, so T-junctions stay
   joined; short internal contours and near-parallel duplicates are dropped.
5. **Colour** (separate from geometry):
   - `sampled`: colours from the icon along the contour; a border between two regions takes the smaller region's
     colour (the detail), so strokes never wash out by blending.
   - `sweep`: a hue sweep through the icon's main colours along the direction in which they sit.
   - `single`: the dominant brand colour (hue histogram weighted by saturation, brightness and coverage).
6. **Rendering** (separate again): 4× supersampled strokes with round joins and caps, outer strokes thicker than
   inner ones, a bright core and two tinted glow layers (never white).

All sizes in `Params` are relative to the icon size.

## Use

```sh
cd tools/icon-outline
python3 -m pip install -r requirements.txt
python3 -m unittest -v                                  # tests on synthetic shapes
python3 outline.py icon.png out.png --mode sweep --background
python3 experiments.py out/                             # comparison sheets of the synthetic icons
python3 experiments.py out/ --icons ~/my-icons/         # plus your own PNGs
```

**Never commit brand icon files** (trademarks). Validation with real app icons happens locally; comparisons are
shared in the issue or pull request only. The tests and committed fixtures are synthetic (`synth.py`). `out/` is
ignored. Screenshots of PixelBuddy showing installed apps, like the README's (`docs/screenshots/`), are fine.

## Known limitations

- Photographic, 3D or gradient-heavy icons (many games) give weak or arbitrary internal contours; the fallback is
  the outer silhouette only.
- Wordmarks and text lose legibility at launcher size.
- A letter on a plate outlines the plate unless the plate detection triggers.
- A border between two regions has one colour (the smaller region's); a blended two-sided stroke was tried and
  washes out.
