#
# Copyright (c) 2023. JetBrains s.r.o.
# Use of this source code is governed by the MIT license that can be found in the LICENSE file.
#

"""
Tests for the private helper functions in ggdeck_.py:
  - _contrast_text_color(bg_color)
  - _extract_geom_style(plot)
"""

import pytest
import lets_plot as gg

# Access module-level private helpers directly from their module
from lets_plot.plot.ggdeck_ import _contrast_text_color, _extract_geom_style


# ---------------------------------------------------------------------------
# _contrast_text_color
# ---------------------------------------------------------------------------

class TestContrastTextColor:
    def test_none_returns_white(self):
        """None background: _parse_color_rgb returns None → mid-grey (128,128,128) fallback.
        Mid-grey luminance ≈ 0.502 which is > 0.5 → returns 'black'."""
        # lum(128,128,128) = (0.2126+0.7152+0.0722)*(128/255) ≈ 0.502 → 'black'
        assert _contrast_text_color(None) == 'black'

    def test_black_returns_white(self):
        assert _contrast_text_color('black') == 'white'

    def test_white_returns_black(self):
        assert _contrast_text_color('white') == 'black'

    def test_red_returns_white(self):
        # red RGB(255,0,0) → luminance ≈ 0.21 → dark → white text
        assert _contrast_text_color('red') == 'white'

    def test_yellow_returns_black(self):
        # yellow RGB(255,255,0) → luminance ≈ 0.93 → light → black text
        assert _contrast_text_color('yellow') == 'black'

    def test_hex6_black_returns_white(self):
        assert _contrast_text_color('#000000') == 'white'

    def test_hex6_white_returns_black(self):
        assert _contrast_text_color('#ffffff') == 'black'

    def test_hex6_case_insensitive(self):
        assert _contrast_text_color('#FFFFFF') == 'black'
        assert _contrast_text_color('#000000') == 'white'

    def test_hex3_shorthand_white_returns_black(self):
        # #fff expands to #ffffff → luminance 1 → black text
        assert _contrast_text_color('#fff') == 'black'

    def test_hex3_shorthand_black_returns_white(self):
        # #000 expands to #000000 → luminance 0 → white text
        assert _contrast_text_color('#000') == 'white'

    def test_hex3_midgrey_light_returns_black(self):
        # #ccc expands to #cccccc → luminance ≈ 0.60 → light → black text
        assert _contrast_text_color('#ccc') == 'black'

    def test_unknown_color_string_returns_black(self):
        # Unknown string: _parse_color_rgb returns None → fallback mid-grey (128,128,128)
        # luminance ≈ 0.502 > 0.5 → 'black'
        result = _contrast_text_color('notacolor')
        assert result == 'black'

    def test_empty_string_returns_black(self):
        # Empty string: none of the checks match, falls back to mid-grey → lum ≈ 0.502 → 'black'
        result = _contrast_text_color('')
        assert result == 'black'

    def test_blue_returns_white(self):
        # blue RGB(0,0,255) → luminance ≈ 0.07 → dark → white
        assert _contrast_text_color('blue') == 'white'

    def test_steelblue_returns_white(self):
        # steelblue RGB(70,130,180) → luminance ≈ 0.23 → dark → white
        assert _contrast_text_color('steelblue') == 'white'


# ---------------------------------------------------------------------------
# _extract_geom_style
# ---------------------------------------------------------------------------

class TestExtractGeomStyle:
    def _make_plot_with_color(self, color):
        """Helper: create ggplot with geom_point with a fixed color."""
        return gg.ggplot() + gg.geom_point(color=color)

    def _make_plot_with_fill(self, fill):
        return gg.ggplot() + gg.geom_bar(stat='identity', fill=fill)

    def _make_plot_no_layers(self):
        return gg.ggplot()

    def test_no_layers_returns_all_none(self):
        result = _extract_geom_style(self._make_plot_no_layers())
        assert result == {'color': None, 'fill': None, 'linetype': None}

    def test_none_plot_returns_all_none(self):
        result = _extract_geom_style(None)
        assert result == {'color': None, 'fill': None, 'linetype': None}

    def test_extracts_color_from_layer(self):
        plot = self._make_plot_with_color('red')
        result = _extract_geom_style(plot)
        assert result['color'] == 'red'

    def test_extracts_fill_from_layer(self):
        plot = self._make_plot_with_fill('blue')
        result = _extract_geom_style(plot)
        assert result['fill'] == 'blue'

    def test_extracts_linetype_from_layer(self):
        plot = gg.ggplot() + gg.geom_line(linetype='dashed')
        result = _extract_geom_style(plot)
        assert result['linetype'] == 'dashed'

    def test_mapped_color_not_extracted(self):
        """A color that is mapped (in aes), not fixed, should not be extracted."""
        plot = gg.ggplot({'x': [1], 'y': [1], 'g': ['a']}, gg.aes('x', 'y', color='g')) + gg.geom_point()
        result = _extract_geom_style(plot)
        # 'color' is in mapping, not as a fixed value on the layer
        assert result['color'] is None

    def test_first_layer_color_wins(self):
        """In a multi-layer plot, the first layer's color should be returned."""
        plot = (gg.ggplot()
                + gg.geom_line(color='red')
                + gg.geom_line(color='blue'))
        result = _extract_geom_style(plot)
        assert result['color'] == 'red'

    def test_second_layer_color_used_when_first_has_no_color(self):
        """If first layer has no color but second does, second layer's color is used."""
        # geom_path with no explicit color (color is None in layer)
        plot = (gg.ggplot()
                + gg.geom_point()          # no fixed color
                + gg.geom_line(color='green'))
        result = _extract_geom_style(plot)
        assert result['color'] == 'green'

    def test_all_fields_extracted_from_single_layer(self):
        plot = gg.ggplot() + gg.geom_line(color='purple', linetype='dotted')
        result = _extract_geom_style(plot)
        assert result['color'] == 'purple'
        assert result['linetype'] == 'dotted'
