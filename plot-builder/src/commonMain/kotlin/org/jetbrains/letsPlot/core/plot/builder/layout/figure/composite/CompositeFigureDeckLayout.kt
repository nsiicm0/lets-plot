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

        val commonGeomBounds = geomBounds.reduce { acc, rect ->
            acc.intersect(rect) ?: DoubleRectangle.ZERO
        }

        return elementsLayoutedByBounds.map { buildInfo ->
            if (buildInfo == null) {
                null
            } else if (buildInfo.isComposite) {
                buildInfo
            } else {
                buildInfo.layoutedByGeomBounds(commonGeomBounds)
            }
        }
    }
}
