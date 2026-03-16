import numpy as np
import os
import time

custom_js_url = f"file://{os.path.abspath('js-package/build/kotlin-webpack/js/productionExecutable/lets-plot.js')}?t={int(time.time())}"
# Set our custom local JS bundle that has our kotlin `ggdeck` overlap and tooltip fixes
os.environ['LETS_PLOT_DEV_JS_URL_MANUAL'] = custom_js_url
from lets_plot import *

# Initialize lets-plot
LetsPlot.setup_html()

np.random.seed(42)
L = 100
x = np.arange(L)

N = 4
colors = [None, 'blue', 'green', 'orange', 'red']

sides = []
plots = []
for i in range(1, N+1):
    y = np.random.normal(i * 10, i * 5, L)
    c = np.repeat(i, L)
    if i % 2 == 0:
        p = ggplot({'x': x, 'y': y}, aes(x='x', y='y')) + geom_line(color=colors[i], linetype='dashed') 
    else:
        p = ggplot({'x': x, 'y': y}, aes(x='x', y='y')) + geom_point(color=colors[i], tooltips=layer_tooltips().line('xy|@x,@y')) 
    plots.append(p)
    sides.append("L" if i % 2 == 0 else "R")
    

# Create a deck of the two plots!
deck = ggdeck(plots, sides=sides) + ggtb() + theme_bw()

# Export to HTML file
out_path = os.path.join(os.getcwd(), 'demo_ggdeck.html')
ggsave(deck, out_path, iframe=False)
print(f"Deck exported successfully to: {out_path}")
