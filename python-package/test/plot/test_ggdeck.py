import pytest
from lets_plot.plot.core import PlotSpec
from lets_plot.plot.geom import geom_point, geom_line, geom_bar
from lets_plot.plot.plot import ggplot
from lets_plot.plot.ggdeck_ import ggdeck

def test_ggdeck_empty_plots():
    with pytest.raises(ValueError, match="The 'plots' argument cannot be empty"):
        ggdeck([])

def test_ggdeck_sides_mismatch():
    p = ggplot() + geom_point()
    with pytest.raises(ValueError, match="The length of 'sides' .* must match the number of plots"):
        ggdeck([p, p], sides=['L'])

def test_ggdeck_invalid_side():
    p = ggplot() + geom_point()
    with pytest.raises(ValueError, match="Invalid side"):
        ggdeck([p, p], sides=['L', 'X'])

def test_ggdeck_defaults():
    p1 = ggplot() + geom_point()
    p2 = ggplot() + geom_line()
    deck = ggdeck([p1, p2])
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
    found_right_axis = False
    for scale in scales:
        if scale.get('aesthetic') == 'y' and scale.get('position') == 'right':
            found_right_axis = True
            break
    assert found_right_axis

def test_ggdeck_explicit_sides():
    p1 = ggplot() + geom_point()
    p2 = ggplot() + geom_line()
    p3 = ggplot() + geom_bar()
    
    # L, L, R
    deck = ggdeck([p1, p2, p3], sides=['L', 'L', 'R'])
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
