"""Synthetic test icons (no brand icons in the repository)."""

from __future__ import annotations

import numpy as np


def _grid(size):
    y, x = np.mgrid[0:size, 0:size].astype(float)
    return x / (size - 1), y / (size - 1)


def _rgba(size):
    return np.zeros((size, size, 4))


def _put(img, mask, colour):
    img[mask, :3] = colour
    img[mask, 3] = 1.0


def circle_in_square(size=256):
    """Orange rounded plate with a blue disc: one outer contour plus one internal contour."""
    x, y = _grid(size)
    img = _rgba(size)
    _put(img, (np.abs(x - 0.5) < 0.4) & (np.abs(y - 0.5) < 0.4), (1.0, 0.54, 0.24))
    _put(img, (x - 0.5) ** 2 + (y - 0.5) ** 2 < 0.2**2, (0.2, 0.45, 0.9))
    return img


def gradient_disc(size=256):
    """Disc with a smooth orange -> purple gradient: no internal contour."""
    x, y = _grid(size)
    img = _rgba(size)
    disc = (x - 0.5) ** 2 + (y - 0.5) ** 2 < 0.42**2
    top, bottom = np.array([1.0, 0.54, 0.24]), np.array([0.42, 0.2, 0.62])
    for c in range(3):
        img[..., c] = top[c] * (1 - y) + bottom[c] * y
    img[..., 3] = disc
    img[~disc, :3] = 0
    return img


def parallel_edges(size=256):
    """A plate split by a thin band: two borders 1 % apart should become one stroke."""
    x, y = _grid(size)
    img = _rgba(size)
    plate = (np.abs(x - 0.5) < 0.4) & (np.abs(y - 0.5) < 0.4)
    _put(img, plate & (x < 0.5), (0.25, 0.65, 0.42))
    _put(img, plate & (x >= 0.5), (0.95, 0.85, 0.3))
    _put(img, plate & (np.abs(x - 0.5) < 0.008), (0.1, 0.1, 0.2))
    return img


def plate_with_glyph(size=256):
    """White speech bubble on a blue rounded plate."""
    x, y = _grid(size)
    img = _rgba(size)
    r = 0.12
    dx = np.maximum(np.abs(x - 0.5) - (0.42 - r), 0)
    dy = np.maximum(np.abs(y - 0.5) - (0.42 - r), 0)
    _put(img, dx**2 + dy**2 < r**2, (0.24, 0.45, 0.85))
    bubble = ((x - 0.5) / 0.26) ** 2 + ((y - 0.46) / 0.2) ** 2 < 1
    tail = (y > 0.6) & (y < 0.74) & (x > 0.34) & (x < 0.34 + (0.74 - y) * 0.8)
    _put(img, bubble | tail, (1.0, 1.0, 1.0))
    return img


def opaque_icon(size=256):
    """No transparency: a purple star-ish shape on a flat white square."""
    x, y = _grid(size)
    img = np.ones((size, size, 4))
    a = np.arctan2(y - 0.5, x - 0.5)
    r = np.hypot(x - 0.5, y - 0.5)
    _put(img, r < 0.25 + 0.08 * np.cos(5 * a), (0.45, 0.2, 0.7))
    return img


def logo(size=256):
    """A multi-part logo in the spirit of the concepts: gradient ring, inner head with two ears, a swirl, a flame."""
    x, y = _grid(size)
    img = _rgba(size)
    r = np.hypot(x - 0.5, y - 0.52)
    a = np.arctan2(y - 0.52, x - 0.5)
    ring = (r < 0.42)
    t = np.clip((x - 0.1) / 0.8, 0, 1)
    ring_colour = np.stack([1.0 - 0.45 * t, 0.55 - 0.3 * t, 0.25 + 0.45 * t], -1)
    img[ring, :3] = ring_colour[ring]
    img[ring, 3] = 1
    globe = r < 0.28
    _put(img, globe, (0.42, 0.25, 0.75))
    head = ((x - 0.58) / 0.16) ** 2 + ((y - 0.5) / 0.12) ** 2 < 1
    ear1 = (y > 0.28) & (y < 0.42) & (np.abs(x - 0.52) < (y - 0.28) * 0.5)
    ear2 = (y > 0.28) & (y < 0.42) & (np.abs(x - 0.66) < (y - 0.28) * 0.5)
    _put(img, head | ear1 | ear2, (1.0, 0.6, 0.2))
    swirl = (np.abs(r - 0.35) < 0.035) & (a > 0.6) & (a < 2.6)
    _put(img, swirl, (1.0, 0.85, 0.3))
    flame = ((x - 0.5) / 0.08) ** 2 + ((y - 0.11) / 0.06) ** 2 < 1
    _put(img, flame, (1.0, 0.85, 0.2))
    return img


def transparent(size=128):
    return _rgba(size)


ALL = {
    "circle_in_square": circle_in_square,
    "gradient_disc": gradient_disc,
    "parallel_edges": parallel_edges,
    "plate_with_glyph": plate_with_glyph,
    "opaque_icon": opaque_icon,
    "logo": logo,
}
