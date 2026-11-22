"""Image primitives for the outline prototype, in plain NumPy.

Everything here is deliberately simple so it can be ported to Kotlin without OpenCV (#29):
Lab conversion, Gaussian blur, Sobel, connected components, distance transform, k-means.
"""

from __future__ import annotations

from collections import deque

import numpy as np


def srgb_to_lab(rgb: np.ndarray) -> np.ndarray:
    """sRGB in 0..1, shape (..., 3) -> CIE Lab (D65)."""
    c = np.where(rgb <= 0.04045, rgb / 12.92, ((rgb + 0.055) / 1.055) ** 2.4)
    m = np.array([[0.4124, 0.3576, 0.1805], [0.2126, 0.7152, 0.0722], [0.0193, 0.1192, 0.9505]])
    xyz = c @ m.T / np.array([0.95047, 1.0, 1.08883])
    f = np.where(xyz > 0.008856, np.cbrt(xyz), 7.787 * xyz + 16 / 116)
    lab = np.empty_like(f)
    lab[..., 0] = 116 * f[..., 1] - 16
    lab[..., 1] = 500 * (f[..., 0] - f[..., 1])
    lab[..., 2] = 200 * (f[..., 1] - f[..., 2])
    return lab


def gaussian_kernel(sigma: float) -> np.ndarray:
    radius = max(1, int(round(3 * sigma)))
    x = np.arange(-radius, radius + 1, dtype=float)
    k = np.exp(-(x * x) / (2 * sigma * sigma))
    return k / k.sum()


def gaussian_blur(img: np.ndarray, sigma: float) -> np.ndarray:
    """Separable blur over the first two axes, edges clamped."""
    if sigma <= 0:
        return img.astype(float)
    k = gaussian_kernel(sigma)
    r = len(k) // 2
    out = img.astype(float)
    for axis in (0, 1):
        pad = [(0, 0)] * out.ndim
        pad[axis] = (r, r)
        padded = np.pad(out, pad, mode="edge")
        acc = np.zeros_like(out)
        for i, w in enumerate(k):
            acc += w * np.take(padded, np.arange(i, i + out.shape[axis]), axis=axis)
        out = acc
    return out


def sobel(gray: np.ndarray) -> np.ndarray:
    """Gradient magnitude of a 2-D array."""
    p = np.pad(gray.astype(float), 1, mode="edge")
    gx = (p[:-2, 2:] + 2 * p[1:-1, 2:] + p[2:, 2:]) - (p[:-2, :-2] + 2 * p[1:-1, :-2] + p[2:, :-2])
    gy = (p[2:, :-2] + 2 * p[2:, 1:-1] + p[2:, 2:]) - (p[:-2, :-2] + 2 * p[:-2, 1:-1] + p[:-2, 2:])
    return np.hypot(gx, gy)


def connected_components(labels: np.ndarray) -> np.ndarray:
    """4-connected components of equal values; value 0 stays 0 (background). Returns ids from 1."""
    h, w = labels.shape
    out = np.zeros((h, w), dtype=np.int32)
    next_id = 1
    for y in range(h):
        for x in range(w):
            if labels[y, x] == 0 or out[y, x]:
                continue
            value = labels[y, x]
            out[y, x] = next_id
            queue = deque([(y, x)])
            while queue:
                cy, cx = queue.popleft()
                for ny, nx in ((cy - 1, cx), (cy + 1, cx), (cy, cx - 1), (cy, cx + 1)):
                    if 0 <= ny < h and 0 <= nx < w and not out[ny, nx] and labels[ny, nx] == value:
                        out[ny, nx] = next_id
                        queue.append((ny, nx))
            next_id += 1
    return out


def distance_transform(mask: np.ndarray) -> np.ndarray:
    """Chamfer (3-4) distance in pixels from each True pixel to the nearest False pixel or the border."""
    h, w = mask.shape
    big = 10**9
    d = np.where(mask, big, 0).astype(np.int64)
    d = np.pad(d, 1, constant_values=0)
    for y in range(1, h + 1):
        for x in range(1, w + 1):
            if d[y, x]:
                d[y, x] = min(d[y, x], d[y - 1, x] + 3, d[y, x - 1] + 3, d[y - 1, x - 1] + 4, d[y - 1, x + 1] + 4)
    for y in range(h, 0, -1):
        for x in range(w, 0, -1):
            if d[y, x]:
                d[y, x] = min(d[y, x], d[y + 1, x] + 3, d[y, x + 1] + 3, d[y + 1, x + 1] + 4, d[y + 1, x - 1] + 4)
    return d[1:-1, 1:-1] / 3.0


def kmeans(points: np.ndarray, k: int, weights: np.ndarray | None = None, iterations: int = 12, seed: int = 7):
    """Deterministic weighted k-means (k-means++ init with a fixed seed). Returns (centres, assignment)."""
    n = len(points)
    k = min(k, n)
    w = np.ones(n) if weights is None else weights.astype(float)
    rng = np.random.default_rng(seed)
    centres = [points[int(np.argmax(w))]]
    for _ in range(1, k):
        d = np.min([((points - c) ** 2).sum(1) for c in centres], axis=0) * w
        if d.sum() == 0:
            break
        centres.append(points[rng.choice(n, p=d / d.sum())])
    centres = np.array(centres, dtype=float)
    for _ in range(iterations):
        assign = np.argmin(((points[:, None, :] - centres[None]) ** 2).sum(2), axis=1)
        for i in range(len(centres)):
            sel = assign == i
            if sel.any():
                centres[i] = np.average(points[sel], axis=0, weights=w[sel])
    assign = np.argmin(((points[:, None, :] - centres[None]) ** 2).sum(2), axis=1)
    return centres, assign
