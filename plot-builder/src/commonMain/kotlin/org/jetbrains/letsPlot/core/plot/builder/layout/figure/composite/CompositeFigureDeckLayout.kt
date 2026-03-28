/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.layout.figure.composite

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.builder.buildinfo.FigureBuildInfo
import org.jetbrains.letsPlot.core.plot.builder.layout.figure.CompositeFigureLayout
import org.jetbrains.letsPlot.core.plot.builder.presentation.Defaults.DEF_PLOT_SIZE
import org.jetbrains.letsPlot.core.plot.builder.layout.PlotLayoutInfo
import org.jetbrains.letsPlot.core.plot.builder.layout.figure.plot.PlotFigureLayoutInfo

class CompositeFigureDeckLayout(
    private val scaleShareX: ScaleSharePolicy,
    private val scaleShareY: ScaleSharePolicy,
    private val fitCellAspectRatio: Boolean,
    private val elementsDefaultSizes: List<DoubleVector?>,
    private val innerAlignment: Boolean,
) : CompositeFigureLayout {

    companion object {
        /**
         * Horizontal pixel offset applied between stacked axes on the same side.
         * When multiple plots share the same side (e.g. two left-axis plots),
         * each subsequent axis is shifted outward by this amount.
         */
        const val AXIS_LATERAL_OFFSET = 65.0

        /** Horizontal gap between adjacent axis bounding boxes. */
        const val AXIS_BOX_GAP = 6.0
    }

    override fun defaultSize(): DoubleVector {
        return DEF_PLOT_SIZE
    }

    override fun doLayout(bounds: DoubleRectangle, elements: List<FigureBuildInfo?>): List<FigureBuildInfo?> {
        val elementsWithBounds = elements.mapIndexed { index, buildInfo ->
            buildInfo?.let {
                if (fitCellAspectRatio) {
                    it.withBounds(bounds)
                } else {
                    val defaultSize = elementsDefaultSizes[index]!!
                    val elementBounds = DoubleRectangle(
                        bounds.origin,
                        defaultSize
                    )
                    it.withBounds(elementBounds)
                }
            }
        }

        if (!innerAlignment) {
            return elementsWithBounds
        }

        val elementsLayoutedByBounds = elementsWithBounds.map {
            it?.layoutedByOuterSize()
        }

        // Compute common "inner" size
        val geomBounds = elementsLayoutedByBounds
            .filterNotNull()
            .filter { !it.isComposite } // Do not align composite figures by geom bounds
            .mapNotNull { 
                (it.layoutInfo as? PlotFigureLayoutInfo)?.geomAreaBounds 
            }
        
        if (geomBounds.isEmpty()) {
            return elementsLayoutedByBounds
        }

        // We want all plots to have the same geometry bounds, but also ensure that 
        // no plot's outer elements (axes, labels, legends) are clipped by `bounds`.
        // To do this, we find the maximum margin required on each side across all plots.
        var maxLeftMargin = 0.0
        var maxRightMargin = 0.0
        var maxTopMargin = 0.0
        var maxBottomMargin = 0.0

        // Pre-count left and right axes so we can reserve space for shifted axes
        // within the figure bounds (ensuring the theme background covers them).
        var totalLeftAxes = 0
        var totalRightAxes = 0

        // Dynamic per-axis stride: computed from the actual rendered axis width.
        // Falls back to AXIS_LATERAL_OFFSET when layout info is unavailable.
        var maxLeftAxisWidth = AXIS_LATERAL_OFFSET
        var maxRightAxisWidth = AXIS_LATERAL_OFFSET

        for (el in elementsLayoutedByBounds) {
            if (el != null && !el.isComposite) {
                val info = el.layoutInfo as? PlotFigureLayoutInfo
                if (info != null) {
                    val gb = info.geomAreaBounds
                    val ob = el.bounds
                    
                    val leftMargin = gb.left - ob.left
                    val rightMargin = ob.right - gb.right
                    val topMargin = gb.top - ob.top
                    val bottomMargin = ob.bottom - gb.bottom

                    if (leftMargin > maxLeftMargin) maxLeftMargin = leftMargin
                    if (rightMargin > maxRightMargin) maxRightMargin = rightMargin
                    if (topMargin > maxTopMargin) maxTopMargin = topMargin
                    if (bottomMargin > maxBottomMargin) maxBottomMargin = bottomMargin

                    val plotLayout: PlotLayoutInfo = info.plotLayoutInfo
                    if (plotLayout.hasLeftAxis) {
                        totalLeftAxes++
                        // Actual left-axis width = distance from geomWithAxisBounds.left to geomOuterBounds.left
                        val tile = plotLayout.tiles.firstOrNull()
                        if (tile != null) {
                            val axisWidth = tile.geomOuterBounds.left - tile.geomWithAxisBounds.left
                            if (axisWidth > maxLeftAxisWidth) maxLeftAxisWidth = axisWidth
                        }
                    }
                    if (plotLayout.hasRightAxis) {
                        totalRightAxes++
                        val tile = plotLayout.tiles.firstOrNull()
                        if (tile != null) {
                            val axisWidth = tile.geomWithAxisBounds.right - tile.geomOuterBounds.right
                            if (axisWidth > maxRightAxisWidth) maxRightAxisWidth = axisWidth
                        }
                    }
                }
            }
        }

        // Reserve horizontal space for all axis bounding boxes.
        // Use the dynamically-computed per-axis stride (falling back to AXIS_LATERAL_OFFSET)
        // to ensure both axis labels and bounding boxes fit within the SVG viewport.
        val totalLeftSpace = if (totalLeftAxes > 0) {
            maxOf(maxLeftMargin + (totalLeftAxes - 1) * maxLeftAxisWidth,
                  totalLeftAxes * maxLeftAxisWidth)
        } else maxLeftMargin

        val totalRightSpace = if (totalRightAxes > 0) {
            maxOf(maxRightMargin + (totalRightAxes - 1) * maxRightAxisWidth,
                  totalRightAxes * maxRightAxisWidth)
        } else maxRightMargin

        // The common geometry bounds should sit within the provided `bounds`, 
        // inset by the required margins and space for axes.
        val commonGeomBounds = DoubleRectangle(
            bounds.left + totalLeftSpace,
            bounds.top + maxTopMargin,
            bounds.width - totalLeftSpace - totalRightSpace,
            bounds.height - maxTopMargin - maxBottomMargin
        )

        var leftAxisCount = 0
        var rightAxisCount = 0

        return elementsLayoutedByBounds.map { buildInfo ->
            if (buildInfo == null) {
                null
            } else if (buildInfo.isComposite) {
                buildInfo
            } else {
                var shiftedLeft = 0.0
                var shiftedRight = 0.0
                val info = buildInfo.layoutInfo as? PlotFigureLayoutInfo
                if (info != null) {
                    if (info.plotLayoutInfo.hasLeftAxis) {
                        shiftedLeft = leftAxisCount * maxLeftAxisWidth
                        leftAxisCount++
                    }
                    if (info.plotLayoutInfo.hasRightAxis) {
                        shiftedRight = rightAxisCount * maxRightAxisWidth
                        rightAxisCount++
                    }
                }
                buildInfo.layoutedByGeomBounds(commonGeomBounds).withAxisShift(shiftedLeft, shiftedRight)
            }
        }
    }
}
