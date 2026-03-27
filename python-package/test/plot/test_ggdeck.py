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
        'axis_title_y': {'blank': True},
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
