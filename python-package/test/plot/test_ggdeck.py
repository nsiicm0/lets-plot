#
# Copyright (c) 2023. JetBrains s.r.o.
# Use of this source code is governed by the MIT license that can be found in the LICENSE file.
#
import pytest

import lets_plot as gg

figures_prop = [{'data': {},
                 'data_meta': {},
                 'kind': 'plot',
                 'layers': [],
                 'mapping': {},
                 'metainfo_list': [],
                 'scales': []}
                ]

# The second figure in a 2-plot deck gets transparency + right-axis by default
second_figure_default = {
    'data': {},
    'data_meta': {},
    'kind': 'plot',
    'layers': [],
    'mapping': {},
    'metainfo_list': [],
    'scales': [{'aesthetic': 'y', 'position': 'right'}],
    'theme': {
        'legend_position': 'none',
        'panel_background': {'blank': True},
        'panel_grid': {'blank': True},
        'plot_background': {'blank': True},
    }
}

expected_defaults = {
    'kind': 'subplots',
    'figures': figures_prop + [second_figure_default],
    'layout': {'name': 'deck', 'sharex': 'all', 'sharey': 'none', 'fit': True, 'align': True}
}

expected_guides_collect = {
    'kind': 'subplots',
    'figures': figures_prop + [second_figure_default],
    'layout': {'name': 'deck', 'sharex': 'all', 'sharey': 'none', 'fit': True, 'align': True, 'guides': 'collect'}
}


@pytest.mark.parametrize('kwargs,expected', [
    ({}, expected_defaults),
    ({'guides': 'collect'}, expected_guides_collect),
])
def test_ggdeck_spec(kwargs, expected):
    p = gg.ggplot(data={})
    spec = gg.ggdeck([p, p], **kwargs)
    assert spec.as_dict() == expected


def test_ggdeck_empty_plots():
    with pytest.raises(ValueError, match="The 'plots' argument cannot be empty"):
        gg.ggdeck([])


def test_ggdeck_sides_mismatch():
    p = gg.ggplot()
    with pytest.raises(ValueError, match="The length of 'sides' .* must match the number of plots"):
        gg.ggdeck([p, p], sides=['L'])


def test_ggdeck_invalid_side():
    p = gg.ggplot()
    with pytest.raises(ValueError, match="Invalid side"):
        gg.ggdeck([p, p], sides=['L', 'X'])


def test_ggdeck_defaults():
    p1 = gg.ggplot()
    p2 = gg.ggplot()
    deck = gg.ggdeck([p1, p2])
    spec = deck.as_dict()

    assert spec['layout']['name'] == 'deck'
    assert spec['layout']['sharex'] == 'all'
    assert spec['layout']['sharey'] == 'none'
    assert spec['layout']['align'] is True

    # Check transparency on second plot
    fig2 = spec['figures'][1]
    theme_opts = fig2.get('theme', {})
    assert theme_opts.get('panel_background') == {'blank': True}
    assert theme_opts.get('plot_background') == {'blank': True}
    assert theme_opts.get('panel_grid') == {'blank': True}

    # Check axis position on second plot (default is 'R')
    scales = fig2.get('scales', [])
    found_right_axis = any(
        s.get('aesthetic') == 'y' and s.get('position') == 'right'
        for s in scales
    )
    assert found_right_axis


def test_ggdeck_explicit_sides():
    p1 = gg.ggplot()
    p2 = gg.ggplot()
    p3 = gg.ggplot()

    # L, L, R
    deck = gg.ggdeck([p1, p2, p3], sides=['L', 'L', 'R'])
    spec = deck.as_dict()

    # p2 should NOT have right axis
    fig2 = spec['figures'][1]
    scales2 = fig2.get('scales', [])
    right_axis2 = any(s.get('aesthetic') == 'y' and s.get('position') == 'right' for s in scales2)
    assert not right_axis2

    # p3 SHOULD have right axis
    fig3 = spec['figures'][2]
    scales3 = fig3.get('scales', [])
    right_axis3 = any(s.get('aesthetic') == 'y' and s.get('position') == 'right' for s in scales3)
    assert right_axis3

    # Check transparency
    # p1: no transparency
    fig1 = spec['figures'][0]
    assert 'panel_background' not in fig1.get('theme', {})

    # p2: transparency
    assert fig2.get('theme', {}).get('panel_background') == {'blank': True}

    # p3: transparency
    assert fig3.get('theme', {}).get('panel_background') == {'blank': True}


def test_ggdeck_single_plot():
    p = gg.ggplot()
    deck = gg.ggdeck([p])
    spec = deck.as_dict()

    assert spec['layout']['name'] == 'deck'
    assert len(spec['figures']) == 1

    # Single plot should NOT get transparency applied
    fig = spec['figures'][0]
    assert 'panel_background' not in fig.get('theme', {})


def test_ggdeck_none_in_plots():
    p = gg.ggplot()
    deck = gg.ggdeck([p, None, p], sides=['L', 'R', 'R'])
    spec = deck.as_dict()

    assert len(spec['figures']) == 3
    assert spec['figures'][0] is not None
    assert spec['figures'][1] is None
    assert spec['figures'][2] is not None


def test_ggdeck_guides_parameter():
    p1 = gg.ggplot()
    p2 = gg.ggplot()
    deck = gg.ggdeck([p1, p2], guides='collect')
    spec = deck.as_dict()

    assert spec['layout']['guides'] == 'collect'


def test_ggdeck_lowercase_sides():
    p1 = gg.ggplot()
    p2 = gg.ggplot()
    deck = gg.ggdeck([p1, p2], sides=['l', 'r'])
    spec = deck.as_dict()

    # Should work the same as uppercase
    fig2 = spec['figures'][1]
    scales = fig2.get('scales', [])
    found_right_axis = any(
        s.get('aesthetic') == 'y' and s.get('position') == 'right'
        for s in scales
    )
    assert found_right_axis


def test_ggdeck_auto_style_true_injects_axis_color():
    """When auto_style=True and geom has a fixed color, axis theme elements should be injected."""
    p1 = gg.ggplot()
    p2 = gg.ggplot() + gg.geom_line(color='red')
    deck = gg.ggdeck([p1, p2], auto_style=True)
    spec = deck.as_dict()

    fig2 = spec['figures'][1]
    theme = fig2.get('theme', {})

    # Axis line, ticks, text, title should all be colored red
    assert theme.get('axis_line_y', {}).get('color') == 'red'
    assert theme.get('axis_ticks_y', {}).get('color') == 'red'
    assert theme.get('axis_text_y', {}).get('color') == 'red'
    assert theme.get('axis_title_y', {}).get('color') == 'red'

    # Tooltip background should be colored red too
    assert theme.get('axis_tooltip_y', {}).get('color') == 'red'
    assert theme.get('axis_tooltip_y', {}).get('fill') == 'red'


def test_ggdeck_auto_style_false_suppresses_axis_color():
    """When auto_style=False, no axis color theme elements should be injected."""
    p1 = gg.ggplot()
    p2 = gg.ggplot() + gg.geom_line(color='red')
    deck = gg.ggdeck([p1, p2], auto_style=False)
    spec = deck.as_dict()

    fig2 = spec['figures'][1]
    theme = fig2.get('theme', {})

    # None of the color-related axis theme keys should be present
    assert 'axis_line_y' not in theme
    assert 'axis_ticks_y' not in theme
    assert 'axis_text_y' not in theme
    # axis_title_y may still be blank from transparency suppression, but not colored
    assert theme.get('axis_title_y', {}) != {'color': 'red'}


def test_ggdeck_auto_style_no_geom_color_no_injection():
    """When auto_style=True but geom has no fixed color, no axis coloring is applied."""
    p1 = gg.ggplot()
    # p2 has a geom but no fixed color (just default)
    p2 = gg.ggplot() + gg.geom_line()
    deck = gg.ggdeck([p1, p2], auto_style=True)
    spec = deck.as_dict()

    fig2 = spec['figures'][1]
    theme = fig2.get('theme', {})

    # Axis color keys should not be present
    assert 'axis_line_y' not in theme
    assert 'axis_ticks_y' not in theme
    assert 'axis_text_y' not in theme


def test_ggdeck_auto_style_first_plot_unaffected():
    """auto_style DOES color the first plot's axis (for visual consistency).
    Only transparency (panel_background) and right-axis placement skip the first plot.
    """
    p1 = gg.ggplot() + gg.geom_line(color='blue')
    p2 = gg.ggplot() + gg.geom_line(color='red')
    deck = gg.ggdeck([p1, p2], auto_style=True)
    spec = deck.as_dict()

    fig1 = spec['figures'][0]
    theme1 = fig1.get('theme', {})

    # First plot should NOT have panel_background/plot_background blanked (transparency is for overlaid plots)
    assert 'panel_background' not in theme1
    assert 'plot_background' not in theme1
    # First plot should NOT have legend suppressed
    assert 'legend_position' not in theme1

    # Second plot SHOULD have transparency applied
    fig2 = spec['figures'][1]
    theme2 = fig2.get('theme', {})
    assert theme2.get('panel_background') == {'blank': True}


def test_ggdeck_auto_style_linetype_propagated():
    """When the geom has a linetype, it should be propagated to axis_line_y."""
    p1 = gg.ggplot()
    p2 = gg.ggplot() + gg.geom_line(color='green', linetype='dashed')
    deck = gg.ggdeck([p1, p2], auto_style=True)
    spec = deck.as_dict()

    fig2 = spec['figures'][1]
    theme = fig2.get('theme', {})

    assert theme.get('axis_line_y', {}).get('color') == 'green'
    assert theme.get('axis_line_y', {}).get('linetype') == 'dashed'
