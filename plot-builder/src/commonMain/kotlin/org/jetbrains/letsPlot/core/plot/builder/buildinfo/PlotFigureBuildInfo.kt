/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder.buildinfo

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.builder.GeomLayer
import org.jetbrains.letsPlot.core.plot.builder.PlotSvgRoot
import org.jetbrains.letsPlot.core.plot.builder.assemble.PlotAssembler
import org.jetbrains.letsPlot.core.plot.builder.layout.figure.plot.PlotFigureLayoutInfo

class PlotFigureBuildInfo constructor(
    private val plotAssembler: PlotAssembler,
    private val processedPlotSpec: Map<String, Any>,
    override val bounds: DoubleRectangle,
    override val computationMessages: List<String>,
    private val axisLeftShift: Double = 0.0,
    private val axisRightShift: Double = 0.0,
) : FigureBuildInfo {

    override val isComposite: Boolean = false

    override val containsLiveMap: Boolean = plotAssembler.containsLiveMap

    override val layoutInfo: PlotFigureLayoutInfo
        get() = _layoutInfo

    private lateinit var _layoutInfo: PlotFigureLayoutInfo
    private var liveMapCursorServiceConfig: Any? = null

    /**
     * This method should be called before 'createSvgRoot()'
     */
    override fun injectLiveMapProvider(f: (tiles: List<List<GeomLayer>>, spec: Map<String, Any>) -> Any) {
        if (containsLiveMap) {
            val listOfTiles = plotAssembler.geomTiles.coreLayersByTile()
            liveMapCursorServiceConfig = f(listOfTiles, processedPlotSpec)
        }
    }

    override fun createSvgRoot(): PlotSvgRoot {
        check(this::_layoutInfo.isInitialized) { "Plot figure is not layouted." }
        val plotSvgComponent = plotAssembler.createPlot(_layoutInfo)
        
        fun shiftAxis(container: org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode, className: String, shift: Double) {
            if (container is org.jetbrains.letsPlot.datamodel.svg.dom.SvgElement) {
                val elemClass = container.getAttribute("class")?.toString() ?: ""
                if (elemClass.contains(className)) {
                    val existing = container.getAttribute("transform")?.toString() ?: ""
                    val newTransform = if (existing.isEmpty()) "translate($shift, 0)" else "$existing translate($shift, 0)"
                    container.setAttribute("transform", newTransform)
                    return
                }
            }
            for (child in container.children()) {
                shiftAxis(child, className, shift)
            }
        }
        
        if (axisLeftShift != 0.0) {
            shiftAxis(plotSvgComponent.rootGroup, "axis-left", -axisLeftShift)
        }
        if (axisRightShift != 0.0) {
            shiftAxis(plotSvgComponent.rootGroup, "axis-right", axisRightShift)
        }

        return PlotSvgRoot(
            plotSvgComponent,
            liveMapCursorServiceConfig = if (containsLiveMap) liveMapCursorServiceConfig else null,
            bounds.origin
        )
    }

    override fun withAxisShift(leftShift: Double, rightShift: Double): FigureBuildInfo {
        return makeCopy(bounds, leftShift, rightShift).apply {
            if (this@PlotFigureBuildInfo::_layoutInfo.isInitialized) {
                this._layoutInfo = this@PlotFigureBuildInfo._layoutInfo
            }
        }
    }

    override fun withBounds(bounds: DoubleRectangle): PlotFigureBuildInfo {
        return if (bounds == this.bounds) {
            this
        } else {
            // this drops 'layout info' if initialized.
            makeCopy(bounds)
        }
    }

    override fun layoutedByOuterSize(): PlotFigureBuildInfo {
        val outerSize = bounds.dimension
        val layoutInfo = plotAssembler.layoutByOuterSize(outerSize)
        return makeCopy().apply {
            this._layoutInfo = layoutInfo
        }
    }

    override fun layoutedByGeomBounds(geomBounds: DoubleRectangle): PlotFigureBuildInfo {
        val layoutInfo = plotAssembler.layoutByGeomSize(geomBounds.dimension)
        val oldCenter = geomBounds.center
        val newCenter = layoutInfo.geomAreaBounds.center
        val delta = newCenter.subtract(oldCenter)
        val newOrigin = this.bounds.origin.subtract(delta)
        val newSize = layoutInfo.figureLayoutedBounds.dimension
        val newBounds = DoubleRectangle(newOrigin, newSize)

        return makeCopy(newBounds).apply {
            this._layoutInfo = layoutInfo
        }
    }

    private fun makeCopy(
        newBounds: DoubleRectangle? = null, 
        newAxisLeftShift: Double = this.axisLeftShift, 
        newAxisRightShift: Double = this.axisRightShift
    ): PlotFigureBuildInfo {
        val newBuildInfo = PlotFigureBuildInfo(
            plotAssembler,
            processedPlotSpec,
            newBounds ?: this.bounds,
            computationMessages,
            newAxisLeftShift,
            newAxisRightShift
        )

        if (this.liveMapCursorServiceConfig != null) {
            newBuildInfo.liveMapCursorServiceConfig = liveMapCursorServiceConfig
        }

        return newBuildInfo
    }

    override fun withPreferredSize(size: DoubleVector): FigureBuildInfo {
        return PlotFigureBuildInfo(
            plotAssembler,
            processedPlotSpec,
            DoubleRectangle(DoubleVector.Companion.ZERO, size),
            computationMessages
        )
    }
}