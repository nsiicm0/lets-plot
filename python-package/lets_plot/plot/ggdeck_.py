#
# Copyright (c) 2023. JetBrains s.r.o.
# Use of this source code is governed by the MIT license that can be found in the LICENSE file.
#

from ._global_theme import _get_global_theme
from .subplots import SupPlotsLayoutSpec
from .subplots import SupPlotsSpec
from .subplots_util import _strip_theme_if_global
from .theme_ import theme, element_blank, element_text
from .scale_position import scale_y_continuous

__all__ = ['ggdeck']


from typing import Optional

def ggdeck(plots: list, sides: Optional[list] = None, *,
           guides: Optional[str] = None
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
        1. Subsequent plots in the deck will have a transparent background.
        2. By default, axes are drawn with standard colors. If you want to differentiate
           them, you can apply grammar constraints to individual plots (e.g. 
           `theme(axis_line_y=element_line(color="red"), axis_text_y=element_text(color="red"))`).
    guides : {'auto', 'collect', 'keep'}, default='auto'
        Controls the placement of guides.

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

    left_count = 0
    right_count = 0

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
                panel_grid=element_blank(),  # Optional: maybe user wants grid from top plots? 
                                            # Usually only base plot grid is desired.
                legend_position='none'       # Prevent overlapping legends
            )
        
        # Apply axis positioning
        # 'position="right"' moves the y-axis labels and ticks to the right side.
        # This creates the visual effect of a secondary axis when overlaid on a left-axis plot.
        if side.upper() == 'R':
            plot += scale_y_continuous(position='right')
            right_count += 1
        elif side.upper() == 'L':
            left_count += 1
        else:
             raise ValueError(f"Invalid side '{side}'. Use 'L' or 'R'.")

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
