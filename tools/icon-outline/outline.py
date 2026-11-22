"""Automatic neon outline icons (#29), Python prototype.

Turns an app icon into smooth, consistently thick outline strokes with a soft tinted glow:

1. source: alpha mask, or background estimated from the border for opaque icons; "white glyph on a plate" icons
   use the glyph and take the colour from the plate
2. segmentation: fine colour clusters in Lab, then neighbouring regions whose shared border is weak are merged;
   tiny and thin regions are folded into a neighbour (no fragments, no near-parallel duplicate strokes)
3. contours: every region border is traced exactly once (crack edges between pixels), so a border is one stroke
4. smoothing: Gaussian along the arc length; chain ends stay pinned, so T-junctions join without gaps
5. colour, separate from geometry: sampled gradient, sweep gradient or one dominant colour
6. rendering, separate again: anti-aliased strokes with round joins and caps, a bright core and tinted glow layers

Only primitives that are easy to port to Kotlin are used (see primitives.py).
"""

from __future__ import annotations

import argparse
import colorsys
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from primitives import connected_components, distance_transform, gaussian_blur, kmeans, sobel, srgb_to_lab


@dataclass(frozen=True)
class Params:
    """All sizes are relative to the icon size, so results look the same at any resolution."""

    work_size: int = 192
    clusters: int = 10
    min_region: float = 0.004  # of the foreground area
    weak_border: float = 22.0  # mean Lab gradient along a border below which two regions are merged
    thin: float = 0.016  # regions thinner than this (of the size) are merged into a neighbour
    min_contour: float = 0.08  # internal contours shorter than this (of the size) are dropped
    smooth: float = 0.012  # Gaussian sigma along the arc length (of the size)
    duplicate: float = 0.035  # internal contours this close to a longer one are dropped
    outer_width: float = 0.036
    inner_width: float = 0.024
    glow: tuple[tuple[float, float], ...] = ((1.8, 0.34), (3.2, 0.2))  # (radius x stroke width, opacity)
    core: float = 0.42  # bright core width relative to the stroke
    core_whiten: float = 0.25
    saturation_boost: float = 1.25
    min_value: float = 0.82  # strokes stay bright on the dark launcher background
    padding: float = 0.1


@dataclass
class Contour:
    points: np.ndarray  # (n, 2) x, y in work pixels
    regions: tuple[int, int]
    closed: bool

    @property
    def outer(self) -> bool:
        return 0 in self.regions

    @property
    def length(self) -> float:
        p = np.vstack([self.points, self.points[:1]]) if self.closed else self.points
        return float(np.hypot(*np.diff(p, axis=0).T).sum())


@dataclass
class Outline:
    contours: list[Contour]
    rgb: np.ndarray  # work-size RGB used for colour sampling
    mask: np.ndarray
    plate_colour: np.ndarray | None = None
    debug: dict = field(default_factory=dict)


# ----------------------------------------------------------------------------------------------------------------
# 1. Source


def load(path: str | Path) -> np.ndarray:
    return np.asarray(Image.open(path).convert("RGBA"), dtype=float) / 255.0


def prepare(rgba: np.ndarray, size: int) -> tuple[np.ndarray, np.ndarray]:
    """Pads to a square (transparent) and resizes to the work size."""
    h, w = rgba.shape[:2]
    side = max(h, w)
    square = np.zeros((side, side, 4))
    square[(side - h) // 2 : (side - h) // 2 + h, (side - w) // 2 : (side - w) // 2 + w] = rgba
    image = Image.fromarray((np.clip(square, 0, 1) * 255).astype(np.uint8), "RGBA")
    image = image.convert("RGBa").resize((size, size), Image.LANCZOS).convert("RGBA")
    a = np.asarray(image, dtype=float) / 255.0
    return a[..., :3], a[..., 3]


def foreground(rgb: np.ndarray, alpha: np.ndarray) -> np.ndarray:
    """Alpha mask; fully opaque icons get their background estimated from the border instead."""
    border = np.concatenate([alpha[0], alpha[-1], alpha[:, 0], alpha[:, -1]])
    if border.mean() < 0.9:
        return alpha > 0.5
    lab = srgb_to_lab(rgb)
    edge = np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]])
    background = np.median(edge, axis=0)
    return np.linalg.norm(lab - background, axis=-1) > 12


def plate_glyph(rgb: np.ndarray, mask: np.ndarray):
    """For "white glyph on a uniform plate" icons, returns (glyph mask, plate colour), else None."""
    if mask.sum() == 0:
        return None
    lab = srgb_to_lab(rgb)
    plate = np.median(lab[mask], axis=0)
    near_plate = (np.linalg.norm(lab - plate, axis=-1) < 12) & mask
    chroma = np.hypot(lab[..., 1], lab[..., 2])
    glyph = mask & (lab[..., 0] > 82) & (chroma < 14) & ~near_plate
    share = glyph.sum() / mask.sum()
    if near_plate.sum() / mask.sum() > 0.5 and 0.03 < share < 0.45 and np.hypot(plate[1], plate[2]) > 10:
        rgb_plate = np.median(rgb[near_plate], axis=0)
        return glyph, rgb_plate
    return None


# ----------------------------------------------------------------------------------------------------------------
# 2. Segmentation


def _relabel(labels: np.ndarray, parent: dict[int, int]) -> np.ndarray:
    def find(a):
        while parent.get(a, a) != a:
            a = parent[a]
        return a

    ids = np.unique(labels)
    mapping = {i: find(i) for i in ids}
    out = np.vectorize(mapping.get)(labels)
    # compact ids, background stays 0
    unique = [u for u in np.unique(out) if u != 0]
    compact = {u: i + 1 for i, u in enumerate(unique)}
    compact[0] = 0
    return np.vectorize(compact.get)(out).astype(np.int32)


def _near_junction(labels: np.ndarray, radius: int = 2) -> np.ndarray:
    """Pixels with at least three different labels (background included) within `radius`."""
    padded = np.pad(labels, radius, mode="edge")
    h, w = labels.shape
    windows = np.stack(
        [padded[dy : dy + h, dx : dx + w] for dy in range(2 * radius + 1) for dx in range(2 * radius + 1)], axis=-1
    )
    windows.sort(axis=-1)
    distinct = 1 + (np.diff(windows, axis=-1) != 0).sum(-1)
    return distinct >= 3


def _borders(labels: np.ndarray, grad: np.ndarray, strong: float):
    """Per pair of neighbouring regions (background included): [border length, strong-edge length, judged length].
    Border pixels near a junction with a third region are not judged: other edges meet there."""
    stats: dict[tuple[int, int], list[float]] = {}
    junction = _near_junction(labels)
    for a, b, g, j in (
        (labels[:, :-1], labels[:, 1:], np.maximum(grad[:, :-1], grad[:, 1:]), junction[:, :-1] | junction[:, 1:]),
        (labels[:-1], labels[1:], np.maximum(grad[:-1], grad[1:]), junction[:-1] | junction[1:]),
    ):
        diff = a != b
        for x, y, v, near in zip(a[diff].tolist(), b[diff].tolist(), g[diff].tolist(), j[diff].tolist()):
            s = stats.setdefault((min(x, y), max(x, y)), [0.0, 0.0, 0.0])
            s[0] += 1
            if not near:
                s[2] += 1
                s[1] += v > strong
    return stats


def _strong_share(s: list[float]) -> float:
    return s[1] / s[2] if s[2] >= 3 else 1.0


def _merge_weak(labels: np.ndarray, grad: np.ndarray, p: Params) -> np.ndarray:
    """Agglomerative: repeatedly merge the pair whose whole shared border has the smallest share of strong edge,
    while that share is below 30 %. Re-evaluating merged regions avoids chains through smooth gradients."""
    stats = _borders(labels, grad, p.weak_border)
    parent: dict[int, int] = {}
    while True:
        candidates = [(_strong_share(s), k) for k, s in stats.items() if k[0] and _strong_share(s) < 0.3]
        if not candidates:
            break
        _, (a, b) = min(candidates)
        parent[b] = a
        merged: dict[tuple[int, int], list[float]] = {}
        for (x, y), s in stats.items():
            if (x, y) == (a, b):
                continue
            x2, y2 = (a if x == b else x), (a if y == b else y)
            key = (min(x2, y2), max(x2, y2))
            m = merged.setdefault(key, [0.0, 0.0, 0.0])
            m[0] += s[0]
            m[1] += s[1]
            m[2] += s[2]
        stats = merged
    return _relabel(labels, parent) if parent else labels


def _fold_specks(labels: np.ndarray, lab: np.ndarray, mask: np.ndarray, p: Params) -> np.ndarray:
    """Tiny regions (mostly anti-aliased edge pixels) join their most similar neighbour by colour. Done before
    comparing borders, so specks don't hide how strong the edge between two big regions is."""
    min_area = max(4, p.min_region * mask.sum())
    for _ in range(8):
        n = labels.max() + 1
        areas = np.bincount(labels.ravel(), minlength=n)
        sums = np.stack([np.bincount(labels.ravel(), weights=lab[..., c].ravel(), minlength=n) for c in range(3)], 1)
        means = sums / np.maximum(areas, 1)[:, None]
        pairs = set()
        for a, b in ((labels[:, :-1], labels[:, 1:]), (labels[:-1], labels[1:])):
            diff = (a != b) & (a != 0) & (b != 0)
            pairs.update(zip(a[diff].tolist(), b[diff].tolist()))
        neighbours: dict[int, set[int]] = {}
        for a, b in pairs:
            neighbours.setdefault(a, set()).add(b)
            neighbours.setdefault(b, set()).add(a)
        parent: dict[int, int] = {}
        for region in sorted(neighbours, key=lambda r: areas[r]):
            if areas[region] >= min_area or region in parent:
                continue
            options = [o for o in neighbours[region] if o not in parent and areas[o] >= areas[region]]
            if options:
                parent[region] = min(options, key=lambda o: float(((means[o] - means[region]) ** 2).sum()))
        if not parent:
            break
        labels = _relabel(labels, parent)
    return labels


def _fold_small_and_thin(labels: np.ndarray, grad: np.ndarray, mask: np.ndarray, p: Params) -> np.ndarray:
    """Tiny regions and thin slivers join the neighbour they share the longest border with."""
    size = mask.shape[0]
    min_area = max(4, p.min_region * mask.sum())
    thin_px = p.thin * size
    for _ in range(6):
        stats = _borders(labels, grad, p.weak_border)
        areas = np.bincount(labels.ravel())
        boundary = np.zeros(mask.shape, dtype=bool)
        boundary[:, :-1] |= labels[:, :-1] != labels[:, 1:]
        boundary[:, 1:] |= labels[:, :-1] != labels[:, 1:]
        boundary[:-1] |= labels[:-1] != labels[1:]
        boundary[1:] |= labels[:-1] != labels[1:]
        depth = distance_transform(~boundary)
        thickness = np.zeros(len(areas))
        np.maximum.at(thickness, labels.ravel(), depth.ravel())
        parent: dict[int, int] = {}
        for region in sorted(range(1, len(areas)), key=lambda r: areas[r]):
            if areas[region] == 0 or region in parent:
                continue
            if areas[region] < min_area or 2 * thickness[region] + 1 < thin_px:
                neighbours = [(s[0], b if a == region else a) for (a, b), s in stats.items() if region in (a, b)]
                neighbours = [(n, o) for n, o in neighbours if o != 0 and o not in parent]
                if neighbours:
                    parent[region] = max(neighbours)[1]
        if not parent:
            break
        labels = _relabel(labels, parent)
    return labels


def segment(rgb: np.ndarray, mask: np.ndarray, p: Params) -> np.ndarray:
    lab = srgb_to_lab(gaussian_blur(rgb, 0.8))
    clusters = np.zeros(mask.shape, dtype=np.int32)
    if mask.sum() == 0:
        return clusters
    _, assign = kmeans(lab[mask], p.clusters)
    clusters[mask] = assign + 1
    labels = connected_components(clusters)
    grad = np.sqrt(sum(sobel(lab[..., c]) ** 2 for c in range(3))) / 8
    labels = _fold_specks(labels, lab, mask, p)
    labels = _merge_weak(labels, grad, p)
    labels = _fold_small_and_thin(labels, grad, mask, p)
    return _merge_weak(labels, grad, p)


# ----------------------------------------------------------------------------------------------------------------
# 3. Contours: crack edges between differently labelled pixels, each border traced once


def trace(labels: np.ndarray) -> list[Contour]:
    lp = np.pad(labels, 1)
    h, w = lp.shape
    edges: dict[tuple, tuple[int, int]] = {}  # ((y0, x0), (y1, x1)) -> region pair
    for y in range(1, h):
        for x in range(w):
            a, b = lp[y - 1, x], lp[y, x]
            if a != b:
                edges[((y, x), (y, x + 1))] = (min(a, b), max(a, b))
    for y in range(h):
        for x in range(1, w):
            a, b = lp[y, x - 1], lp[y, x]
            if a != b:
                edges[((y, x), (y + 1, x))] = (min(a, b), max(a, b))
    incident: dict[tuple, list] = {}
    for e in edges:
        incident.setdefault(e[0], []).append(e)
        incident.setdefault(e[1], []).append(e)

    def is_node(v):
        es = incident[v]
        return len(es) != 2 or edges[es[0]] != edges[es[1]]

    seen: set = set()
    contours: list[Contour] = []

    def walk(start, edge):
        pts = [start]
        v = start
        while True:
            seen.add(edge)
            u = edge[1] if edge[0] == v else edge[0]
            pts.append(u)
            if is_node(u) or u == start:
                return pts, u == start
            nxt = [e for e in incident[u] if e != edge and e not in seen]
            if not nxt:
                return pts, False
            v, edge = u, nxt[0]

    for v in incident:
        if is_node(v):
            for e in incident[v]:
                if e not in seen:
                    pts, _ = walk(v, e)
                    contours.append(_contour(pts, edges[e], closed=False))
    for e in edges:
        if e not in seen:
            pts, closed = walk(e[0], e)
            if closed:
                pts = pts[:-1]
            contours.append(_contour(pts, edges[e], closed=closed))
    return contours


def _contour(pts, regions, closed):
    arr = np.array([(x - 1, y - 1) for y, x in pts], dtype=float)
    return Contour(arr, (int(regions[0]), int(regions[1])), closed)


# ----------------------------------------------------------------------------------------------------------------
# 4. Smoothing and cleanup


def resample(points: np.ndarray, closed: bool, step: float = 1.0) -> np.ndarray:
    p = np.vstack([points, points[:1]]) if closed else points
    seg = np.hypot(*np.diff(p, axis=0).T)
    dist = np.concatenate([[0], np.cumsum(seg)])
    total = dist[-1]
    if total == 0:
        return points[:1]
    n = max(2, int(round(total / step)))
    t = np.linspace(0, total, n, endpoint=not closed)
    return np.stack([np.interp(t, dist, p[:, 0]), np.interp(t, dist, p[:, 1])], axis=1)


def smooth(c: Contour, sigma: float) -> Contour:
    pts = resample(c.points, c.closed)
    if len(pts) < 3 or sigma <= 0:
        return Contour(pts, c.regions, c.closed)
    k = np.exp(-(np.arange(-int(3 * sigma), int(3 * sigma) + 1) ** 2) / (2 * sigma * sigma))
    k /= k.sum()
    r = len(k) // 2
    if c.closed:
        padded = np.vstack([pts[-r:], pts, pts[:r]]) if len(pts) > r else np.vstack([pts] * 3)
        mode_pts = padded
    else:
        mode_pts = np.vstack([np.repeat(pts[:1], r, 0), pts, np.repeat(pts[-1:], r, 0)])
    out = np.stack([np.convolve(mode_pts[:, i], k, mode="valid") for i in (0, 1)], axis=1)
    out = out[: len(pts)]
    if not c.closed:
        out[0], out[-1] = pts[0], pts[-1]  # pinned ends: junctions stay joined
    return Contour(out, c.regions, c.closed)


def cleanup(contours: list[Contour], size: int, p: Params) -> list[Contour]:
    min_len = p.min_contour * size
    kept = [c for c in contours if c.length >= (min_len * 0.5 if c.outer else min_len)]
    # near-parallel duplicates: drop internal contours that mostly run along a longer one
    kept.sort(key=lambda c: -c.length)
    result: list[Contour] = []
    limit = p.duplicate * size
    for c in kept:
        if not c.outer and result:
            others = np.vstack([o.points for o in result])
            d = np.sqrt(((c.points[:, None, :] - others[None]) ** 2).sum(-1)).min(1)
            if (d < limit).mean() > 0.7:
                continue
        result.append(c)
    return result


def extract(rgba: np.ndarray, p: Params = Params()) -> Outline:
    rgb, alpha = prepare(rgba, p.work_size)
    mask = foreground(rgb, alpha)
    plate = plate_glyph(rgb, mask)
    plate_colour = None
    if plate is not None:
        # outline the glyph, colour from the plate
        mask, plate_colour = plate[0], plate[1]
        labels = mask.astype(np.int32)
    else:
        labels = segment(rgb, mask, p)
    contours = [smooth(c, p.smooth * p.work_size) for c in trace(labels)]
    contours = cleanup(contours, p.work_size, p)
    return Outline(contours, rgb, mask, plate_colour, {"labels": labels})


# ----------------------------------------------------------------------------------------------------------------
# 5. Colour


def _boost(rgb: np.ndarray, p: Params) -> np.ndarray:
    out = []
    for c in np.atleast_2d(rgb):
        h, s, v = colorsys.rgb_to_hsv(*np.clip(c, 0, 1))
        out.append(colorsys.hsv_to_rgb(h, min(1.0, s * p.saturation_boost), max(v, p.min_value)))
    return np.array(out)


def dominant_colour(rgb: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """Hue histogram weighted by saturation, brightness and coverage; mean colour of the winning hue bin."""
    px = rgb[mask]
    if len(px) == 0:
        return np.array([1.0, 1.0, 1.0])
    hsv = np.array([colorsys.rgb_to_hsv(*c) for c in px])
    weight = hsv[:, 1] * hsv[:, 2]
    if weight.sum() < 1e-6:
        return px.mean(0)
    bins = (hsv[:, 0] * 36).astype(int) % 36
    hist = np.bincount(bins, weights=weight, minlength=36)
    hist = hist + 0.5 * (np.roll(hist, 1) + np.roll(hist, -1))  # neighbouring hues help
    best = int(np.argmax(hist))
    sel = bins == best
    return np.average(px[sel], axis=0, weights=weight[sel] + 1e-9)


def colours(outline: Outline, mode: str, p: Params) -> list[np.ndarray]:
    """One colour per contour point. Modes: sampled, sweep, single."""
    rgb, mask = outline.rgb, outline.mask
    if outline.plate_colour is not None:
        # glyph on a plate: the plate is the brand colour, whatever the mode
        c = _boost(outline.plate_colour, p)[0]
        return [np.repeat(c[None], len(k.points), 0) for k in outline.contours]
    if mode == "single":
        c = _boost(dominant_colour(rgb, mask if outline.plate_colour is None else mask), p)[0]
        return [np.repeat(c[None], len(k.points), 0) for k in outline.contours]
    if mode == "sampled":
        # Outer contours take the icon's colour near them. A border between two regions takes the colour of the
        # smaller region (usually the detail, e.g. a disc on a plate), so the stroke never washes out by blending.
        labels = outline.debug["labels"]
        areas = np.bincount(labels.ravel())
        radius = 0.025 * p.work_size
        cache: dict[int, np.ndarray] = {}

        def local_average(region: int) -> np.ndarray:
            if region not in cache:
                m = (mask if region == 0 else labels == region).astype(float)
                cache[region] = gaussian_blur(rgb * m[..., None], radius) / np.maximum(gaussian_blur(m, radius), 1e-6)[..., None]
            return cache[region]

        out = []
        for k in outline.contours:
            a, b = k.regions
            source = 0 if a == 0 else (a if areas[a] <= areas[b] else b)
            xs = np.clip(k.points[:, 0].round().astype(int), 0, p.work_size - 1)
            ys = np.clip(k.points[:, 1].round().astype(int), 0, p.work_size - 1)
            out.append(_boost(local_average(source)[ys, xs], p))
        return out
    if mode == "sweep":
        # interior pixels only: anti-aliased edges mix in the background
        src = mask & (distance_transform(mask) > 2) if mask.sum() > 50 else mask
        px = rgb[src]
        lab = srgb_to_lab(px)
        chroma = np.hypot(lab[:, 1], lab[:, 2])
        w = (chroma + 3) ** 2
        centres, assign = kmeans(lab, 3, weights=w)
        share = np.array([w[assign == i].sum() for i in range(len(centres))]) / w.sum()
        keep = [i for i in range(len(centres)) if share[i] >= 0.08] or [int(np.argmax(share))]
        ys, xs = np.nonzero(src)
        pos = np.array([[xs[assign == i].mean(), ys[assign == i].mean()] for i in keep])
        cols = np.array([px[assign == i].mean(0) for i in keep])
        weights = np.array([(assign == i).sum() for i in keep], float)
        mean = np.average(pos, axis=0, weights=weights)
        cov = np.cov((pos - mean).T, aweights=weights) if len(pos) > 1 else np.eye(2)
        direction = np.linalg.eigh(cov)[1][:, -1] if np.ptp(pos, 0).max() > 2 else np.array([1.0, 0.0])
        if direction[0] < 0:
            direction = -direction
        proj = (pos - mean) @ direction
        order = np.argsort(proj)
        cols, proj = _boost(cols[order], p), proj[order]
        out = []
        for k in outline.contours:
            t = (k.points - mean) @ direction
            out.append(np.stack([np.interp(t, proj, cols[:, i]) for i in range(3)], axis=1))
        return out
    raise ValueError(f"unknown colour mode {mode}")


# ----------------------------------------------------------------------------------------------------------------
# 6. Rendering


def render(outline: Outline, size: int, mode: str = "sampled", p: Params = Params(), glow: bool = True) -> Image.Image:
    """Transparent RGBA image of the outline icon at `size` px."""
    ss = 4
    big = size * ss
    scale = big * (1 - 2 * p.padding) / p.work_size
    offset = big * p.padding
    cols = colours(outline, mode, p)

    def layer(width_factor: float, whiten: float) -> Image.Image:
        img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        for contour, colour in zip(outline.contours, cols):
            base = p.outer_width if contour.outer else p.inner_width
            w = max(1.0, base * big * width_factor)
            pts = contour.points * scale + offset
            if contour.closed:
                pts = np.vstack([pts, pts[:1]])
                colour = np.vstack([colour, colour[:1]])
            for i in range(len(pts)):
                c = colour[i] * (1 - whiten) + whiten
                fill = tuple(int(v * 255) for v in c) + (255,)
                if i + 1 < len(pts):
                    draw.line([tuple(pts[i]), tuple(pts[i + 1])], fill=fill, width=int(round(w)))
                r = w / 2
                draw.ellipse([pts[i][0] - r, pts[i][1] - r, pts[i][0] + r, pts[i][1] + r], fill=fill)
        return img.convert("RGBa").resize((size, size), Image.LANCZOS).convert("RGBA")

    stroke = layer(1.0, 0.0)
    core = layer(p.core, p.core_whiten)
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    if glow:
        s = np.asarray(stroke, dtype=float) / 255.0
        premult = s[..., :3] * s[..., 3:4]
        for radius, opacity in p.glow:
            sigma = p.outer_width * size * radius / 2
            a = gaussian_blur(s[..., 3], sigma)
            c = gaussian_blur(premult, sigma) / np.maximum(a, 1e-6)[..., None]
            ga = np.clip(a * 1.8, 0, 1) * opacity
            glow_img = Image.fromarray((np.dstack([np.clip(c, 0, 1), ga]) * 255).astype(np.uint8), "RGBA")
            result = Image.alpha_composite(result, glow_img)
    result = Image.alpha_composite(result, stroke)
    return Image.alpha_composite(result, core)


def launcher_background(size: int) -> Image.Image:
    """The launcher's navy -> purple -> orange sky, for previews."""
    stops = np.array([[0x14, 0x18, 0x33], [0x3D, 0x2A, 0x6B], [0xB4, 0x50, 0x6A], [0xFF, 0x8A, 0x3D]], float)
    t = np.linspace(0, 1, size)[:, None] * (len(stops) - 1)
    i = np.minimum(t.astype(int), len(stops) - 2)
    f = t - i
    rows = stops[i[:, 0]] * (1 - f) + stops[i[:, 0] + 1] * f
    img = np.repeat(rows[:, None, :], size, axis=1)
    return Image.fromarray(np.dstack([img, np.full((size, size), 255.0)]).astype(np.uint8), "RGBA")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("icon")
    ap.add_argument("out")
    ap.add_argument("--mode", choices=["sampled", "sweep", "single"], default="sampled")
    ap.add_argument("--size", type=int, default=500)
    ap.add_argument("--background", action="store_true", help="draw on the launcher gradient")
    args = ap.parse_args()
    outline = extract(load(args.icon))
    img = render(outline, args.size, args.mode)
    if args.background:
        img = Image.alpha_composite(launcher_background(args.size), img)
    img.save(args.out)


if __name__ == "__main__":
    main()
