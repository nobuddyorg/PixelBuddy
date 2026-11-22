"""Tests for the outline prototype. Run: python3 -m unittest -v (from tools/icon-outline)."""

from __future__ import annotations

import unittest

import numpy as np

import synth
from outline import Params, dominant_colour, extract, prepare, render


def internal(outline):
    return [c for c in outline.contours if not c.outer]


def outer(outline):
    return [c for c in outline.contours if c.outer]


class OutlineTest(unittest.TestCase):
    def test_deterministic(self):
        icon = synth.logo()
        a = np.asarray(render(extract(icon), 160, "sampled"))
        b = np.asarray(render(extract(icon), 160, "sampled"))
        self.assertTrue(np.array_equal(a, b))

    def test_transparent_input_gives_transparent_output(self):
        outline = extract(synth.transparent())
        self.assertEqual([], outline.contours)
        self.assertEqual(0, np.asarray(render(outline, 120))[..., 3].max())

    def test_alpha_only_where_strokes_and_glow_are(self):
        img = np.asarray(render(extract(synth.circle_in_square()), 200, "single"))
        # corners are far from every stroke
        self.assertEqual(0, img[:6, :6, 3].max())
        self.assertGreater(img[..., 3].max(), 200)

    def test_circle_in_square_has_outer_and_one_internal_contour(self):
        outline = extract(synth.circle_in_square())
        self.assertEqual(1, len(outer(outline)))
        self.assertEqual(1, len(internal(outline)))
        self.assertTrue(internal(outline)[0].closed)

    def test_smooth_gradient_has_no_internal_contour(self):
        outline = extract(synth.gradient_disc())
        self.assertEqual(1, len(outer(outline)))
        self.assertEqual([], internal(outline))

    def test_two_nearby_parallel_edges_become_one_stroke(self):
        outline = extract(synth.parallel_edges())
        self.assertEqual(1, len(internal(outline)))

    def test_white_glyph_on_plate_outlines_the_glyph_in_the_plate_colour(self):
        outline = extract(synth.plate_with_glyph())
        self.assertIsNotNone(outline.plate_colour)
        self.assertGreater(outline.plate_colour[2], outline.plate_colour[0])  # blue plate
        self.assertGreaterEqual(len(outline.contours), 1)

    def test_opaque_icons_use_the_border_as_background(self):
        outline = extract(synth.opaque_icon())
        self.assertEqual(1, len(outer(outline)))

    def test_non_square_small_and_large_inputs(self):
        wide = synth.circle_in_square(200)[:, :, :][:, 20:180]  # 200 x 160
        for icon in (wide, synth.circle_in_square(48), synth.circle_in_square(1024)):
            outline = extract(icon)
            self.assertEqual(1, len(outer(outline)))
        rgb, alpha = prepare(wide, 64)
        self.assertEqual((64, 64), alpha.shape)

    def test_outer_stroke_is_thicker_than_internal(self):
        p = Params()
        self.assertGreaterEqual(p.outer_width, p.inner_width)

    def test_dominant_colour(self):
        img = synth.circle_in_square()
        rgb, alpha = prepare(img, 96)
        colour = dominant_colour(rgb, alpha > 0.5)
        # the orange plate covers more than the blue disc
        self.assertGreater(colour[0], 0.8)
        self.assertLess(colour[2], 0.4)

    def test_all_colour_modes_render(self):
        outline = extract(synth.logo())
        for mode in ("sampled", "sweep", "single"):
            img = np.asarray(render(outline, 120, mode))
            self.assertEqual((120, 120, 4), img.shape)
            self.assertGreater(img[..., 3].max(), 200)


if __name__ == "__main__":
    unittest.main()
