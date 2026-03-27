#
# Copyright (c) 2023. JetBrains s.r.o.
# Use of this source code is governed by the MIT license that can be found in the LICENSE file.
#

from typing import Optional

from ._global_theme import _get_global_theme
from .subplots import SupPlotsLayoutSpec
from .subplots import SupPlotsSpec
from .subplots_util import _strip_theme_if_global
from .theme_ import theme, element_blank, element_line, element_text, element_rect
from .scale_position import scale_y_continuous

__all__ = ['ggdeck']

# Common CSS named colors → (R, G, B)
_NAMED_COLORS = {
    'black': (0, 0, 0), 'white': (255, 255, 255), 'red': (255, 0, 0),
    'green': (0, 128, 0), 'blue': (0, 0, 255), 'yellow': (255, 255, 0),
    'cyan': (0, 255, 255), 'magenta': (255, 0, 255), 'orange': (255, 165, 0),
    'purple': (128, 0, 128), 'pink': (255, 192, 203), 'brown': (165, 42, 42),
    'gray': (128, 128, 128), 'grey': (128, 128, 128),
    'darkblue': (0, 0, 139), 'darkgreen': (0, 100, 0), 'darkred': (139, 0, 0),
    'lightblue': (173, 216, 230), 'lightgreen': (144, 238, 144),
    'coral': (255, 127, 80), 'salmon': (250, 128, 114),
    'navy': (0, 0, 128), 'teal': (0, 128, 128), 'olive': (128, 128, 0),
    'maroon': (128, 0, 0), 'aqua': (0, 255, 255), 'lime': (0, 255, 0),
    'gold': (255, 215, 0), 'silver': (192, 192, 192),
    'steelblue': (70, 130, 180), 'tomato': (255, 99, 71),
    'dodgerblue': (30, 144, 255), 'firebrick': (178, 34, 34),
}


def _contrast_text_color(bg_color):
    """Return 'black' or 'white' for best contrast against *bg_color*."""
    r, g, b = 128, 128, 128  # fallback mid-grey
    if bg_color is None:
        return 'white'

    c = str(bg_color).strip().lower()
    if c in _NAMED_COLORS:
        r, g, b = _NAMED_COLORS[c]
    elif c.startswith('#'):
        h = c[1:]
        if len(h) == 3:
            h = ''.join(ch * 2 for ch in h)
        if len(h) == 6:
            r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)

    # Relative luminance (simplified sRGB)
    lum = 0.2126 * (r / 255.0) + 0.7152 * (g / 255.0) + 0.0722 * (b / 255.0)
    return 'black' if lum > 0.5 else 'white'


def _extract_geom_style(plot):
    """
    Extract the dominant visual style from a plot's geometry layers.

    Returns a dict with keys 'color', 'fill', 'linetype' (any may be None).
    """
    result = {'color': None, 'fill': None, 'linetype': None}
    if plot is None:
        return result

    spec = plot.as_dict() if hasattr(plot, 'as_dict') else {}
    layers = spec.get('layers', [])
    for layer in layers:
        # Color
        if result['color'] is None:
            color = layer.get('color')
            if color is not None:
                result['color'] = color
            else:
                mapping = layer.get('mapping', {})
                if 'color' in mapping:
                    pass  # Mapped — can't extract a single value

        # Fill
        if result['fill'] is None:
            fill = layer.get('fill')
            if fill is not None:
                result['fill'] = fill

        # Linetype
        if result['linetype'] is None:
            lt = layer.get('linetype')
            if lt is not None:
                result['linetype'] = lt

    return result


def ggdeck(plots: list, sides: Optional[list] = None, *,
           guides: Optional[str] = None,
           auto_style: bool = True
           ) -> SupPlotsSpec:
    """
    Combine several plots on one figure, organized in a deck (overlaying each other).

    Parameters
    ----------
    plots : list
        A list where each element is a plot specification, a subplot specification, or None.
        Use None to fill in empty cells in the grid.
    sides : list, optional
        List of side specifiers for each plot ('L' for left, 'R' for right).
        If not provided, defaults to ['L'] + ['R'] for subsequent plots.
        When providing multiple left or right sides (e.g., 'L', 'L'), the axes will be
        automatically shifted outwards to prevent overlaps. 
        
        Notes:
        1. Subsequent plots in the deck will have a transparent background
           and their legends will be suppressed to prevent overlapping.
        2. Axis ticks, lines, and labels are automatically colored to match
           each plot's geometry color for visual differentiation.
        3. Bounding boxes around each y-axis show the color and line style
           of the corresponding geometry.
        4. Y-axis titles on overlaid plots are suppressed to prevent overlap
           with the base plot's title.
    guides : {'auto', 'collect', 'keep'}, default='auto'
        Controls the placement of guides.
    auto_style : bool, default=True
        When True, automatically extracts the color, fill, and line style from
        each plot's geometry layers and applies them to the corresponding y-axis
        (ticks, labels, bounding box). When False, axes use default theme styling.

    Returns
    -------
    ``SupPlotsSpec``
        The composite plot specification.

    Examples
    --------
    .. jupyter-execute::
        :linenos:
        :emphasize-lines: 10

        import numpy as np
        from lets_plot import *
        LetsPlot.setup_html()
        np.random.seed(42)
        x = np.arange(100)
        y1 = np.random.normal(0, 1, 100)
        y2 = np.random.normal(10, 5, 100)
        p1 = ggplot({'x': x, 'y': y1}, aes(x='x', y='y')) + geom_line(color='blue')
        p2 = ggplot({'x': x, 'y': y2}, aes(x='x', y='y')) + geom_line(color='red')
        ggdeck([p1, p2])

    """
    if not plots:
        raise ValueError("The 'plots' argument cannot be empty.")

    # Validate and normalize 'sides'
    if sides is None:
        sides = ['L'] + ['R'] * (len(plots) - 1)

    if len(sides) != len(plots):
        raise ValueError(f"The length of 'sides' ({len(sides)}) must match the number of plots ({len(plots)}).")

    # Apply transparency and axis positioning
    processed_plots = []
    for i, (plot, side) in enumerate(zip(plots, sides)):
        if plot is None:
            processed_plots.append(None)
            continue
            
        # Apply transparency to all plots except the first one
        if i > 0:
            plot += theme(
                panel_background=element_blank(),
                plot_background=element_blank(),
                panel_grid=element_blank(),
                legend_position='none',
            )
        
        # Apply axis positioning
        if side.upper() == 'R':
            plot += scale_y_continuous(position='right')
        elif side.upper() == 'L':
            pass
        else:
            raise ValueError(f"Invalid side '{side}'. Use 'L' or 'R'.")

        # Auto-style the y-axis to match the geometry for visual differentiation
        if auto_style:
            geom_style = _extract_geom_style(plot)
            geom_color = geom_style['color']
            geom_linetype = geom_style.get('linetype')

            if geom_color is not None:
                axis_theme = dict(
                    axis_line_y=element_line(color=geom_color, linetype=geom_linetype),
                    axis_ticks_y=element_line(color=geom_color),
                    axis_text_y=element_text(color=geom_color),
                )
                # Color the axis title to match the geometry
                axis_theme['axis_title_y'] = element_text(color=geom_color)

                # Bounding box around the y-axis tooltip area:
                # uses the geometry color as border, with matching linetype
                axis_theme['axis_tooltip_y'] = element_rect(
                    color=geom_color,
                    fill=geom_color,
                    size=2,
                    linetype=geom_linetype,
                )
                axis_theme['axis_tooltip_text_y'] = element_text(
                    color=_contrast_text_color(geom_color)
                )

                plot += theme(**axis_theme)

        processed_plots.append(plot)

    layout = SupPlotsLayoutSpec(
        name="deck",
        sharex='all',
        sharey='none',
        fit=True,
        align=True,
        guides=guides
    )

    figures = [_strip_theme_if_global(fig) for fig in processed_plots]

    figure_spec = SupPlotsSpec(figures=figures, layout=layout)

    # Apply global theme if defined
    global_theme_options = _get_global_theme()
    if global_theme_options is not None:
        figure_spec += global_theme_options

    return figure_spec

