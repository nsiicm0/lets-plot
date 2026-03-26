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
import org.jetbrains.letsPlot.core.plot.builder.layout.figure.plot.PlotFigureLayoutInfo

class CompositeFigureDeckLayout(
    private val scaleShareX: ScaleSharePolicy,
    private val scaleShareY: ScaleSharePolicy,
    private val fitCellAspectRatio: Boolean,
    private val elementsDefaultSizes: List<DoubleVector?>,
    private val innerAlignment: Boolean,
) : CompositeFigureLayout {

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
                }
            }
        }

        // The common geometry bounds should sit within the provided `bounds`, 
        // inset by the maximum required margins.
        val commonGeomBounds = DoubleRectangle(
            bounds.left + maxLeftMargin,
            bounds.top + maxTopMargin,
            bounds.width - maxLeftMargin - maxRightMargin,
            bounds.height - maxTopMargin - maxBottomMargin
        )

        var leftAxisCount = 0
        var rightAxisCount = 0
        val lateralOffset = 65.0

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
                        shiftedLeft = leftAxisCount * lateralOffset
                        leftAxisCount++
                    }
                    if (info.plotLayoutInfo.hasRightAxis) {
                        shiftedRight = rightAxisCount * lateralOffset
                        rightAxisCount++
                    }
                }
                buildInfo.layoutedByGeomBounds(commonGeomBounds).withAxisShift(shiftedLeft, shiftedRight)
            }
        }
    }
}
