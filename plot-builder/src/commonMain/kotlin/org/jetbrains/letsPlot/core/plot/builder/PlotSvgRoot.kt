/*
 * Copyright (c) 2020. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder

import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.canvas.CanvasDrawable
import org.jetbrains.letsPlot.core.plot.base.render.svg.StrokeDashArraySupport
import org.jetbrains.letsPlot.core.plot.base.render.svg.SvgUID
import org.jetbrains.letsPlot.core.plot.base.theme.AxisTheme
import org.jetbrains.letsPlot.core.plot.builder.layout.figure.composite.CompositeFigureDeckLayout
import org.jetbrains.letsPlot.core.plot.builder.presentation.Style
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgCssResource
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgGElement
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNode
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgRectElement

/**
 *  This class only handles static SVG. (no interactions)
 */
class PlotSvgRoot constructor(
    val plot: PlotSvgComponent,
    val liveMapCursorServiceConfig: Any?,
    origin: DoubleVector,
    private val axisLeftShift: Double = 0.0,
    private val axisRightShift: Double = 0.0,
    private val isDeckPlot: Boolean = false
) : FigureSvgRoot(DoubleRectangle(origin, plot.figureSize)) {

    val liveMapCanvasDrawables: List<CanvasDrawable>
        get() = plot.liveMapCanvasDrawables

    val isLiveMap: Boolean
        get() = plot.liveMapCanvasDrawables.isNotEmpty()

    private val decorationLayerId = SvgUID.get(DECORATION_LAYER_ID_PREFIX)
    val decorationLayer = SvgGElement().apply {
        id().set(decorationLayerId)
    }

    protected override fun buildFigureContent() {
        val id = SvgUID.get(PLOT_ID_PREFIX)

        svg.setStyle(object : SvgCssResource {
            override fun css(): String {
                return Style.generateCSS(plot.styleSheet, id, decorationLayerId)
            }
        })

        // Set axis tooltip shift before accessing rootGroup, which triggers the build.
        // During the build, PlotSvgComponent registers tiles with the interactor using
        // this shift to position y-axis tooltips at the shifted axis location.
        if (isDeckPlot) {
            // For left-axis plots, shift tooltip left; for right-axis, shift right.
            val layoutInfo = plot.figureLayoutInfo.plotLayoutInfo
            val shift = if (layoutInfo.hasLeftAxis) -axisLeftShift else axisRightShift
            plot.axisTooltipXShift = shift
        }

        plot.rootGroup.id().set(id)

        // Apply axis shifting for ggdeck overlaid plots.
        // This is done here (after the plot SVG tree is built) rather than in
        // PlotFigureBuildInfo.createSvgRoot() to avoid triggering ensureBuilt()
        // before PlotContainer has a chance to set the interactor.
        if (axisLeftShift != 0.0) {
            shiftAxis(plot.rootGroup, "axis-left", -axisLeftShift)
            // Shift the axis title's PARENT group. The class "axis-title-y" is on the
            // SvgTextElement (child), but the rotation transform is on its parent SvgGElement.
            // Shifting the text element directly would apply in the rotated space (vertical).
            shiftParentOfClass(plot.rootGroup, "axis-title-y", -axisLeftShift)
        }
        if (axisRightShift != 0.0) {
            shiftAxis(plot.rootGroup, "axis-right", axisRightShift)
            shiftParentOfClass(plot.rootGroup, "axis-title-y", axisRightShift)
        }

        svg.children().add(plot.rootGroup)

        // Draw bounding boxes around axes for ggdeck visual differentiation.
        // Added after the plot root group so the boxes render on top of the
        // plot background but before the decoration (interaction) layer.
        if (isDeckPlot) {
            drawAxisBoundingBoxes()
        }

        if (plot.interactionsEnabled) {
            svg.children().add(decorationLayer)
        }
    }

    /**
     * Draws styled bounding box rectangles around each vertical axis.
     * The styling comes from the axis tooltip theme (set by ggdeck in Python via
     * axis_tooltip_y = element_rect(color=..., fill=..., linetype=...)).
     *
     * Boxes use the full geometry area height for consistent vertical alignment
     * across all deck plots. Width is dynamic, based on the actual axis label
     * width (outer bounds minus geom bounds) plus a small padding.
     */
    private fun drawAxisBoundingBoxes() {
        val layoutInfo = plot.figureLayoutInfo.plotLayoutInfo
        val plotAreaOrigin = plot.figureLayoutInfo.plotAreaOrigin
        val tileLayoutInfo = layoutInfo.tiles.firstOrNull() ?: return

        val vAxisTheme = plot.theme.verticalAxis(plot.flippedAxis)

        val absOffset = plotAreaOrigin.add(tileLayoutInfo.offset)

        // Use geomContentBounds for consistent vertical alignment across all deck plots.
        val geomContentAbsolute = tileLayoutInfo.geomContentBounds.add(absOffset)

        // Use geomOuterBounds for horizontal positioning (where the axis ticks start).
        val geomOuterAbsolute = tileLayoutInfo.geomOuterBounds.add(absOffset)

        // Padding approach: box starts flush with the axis edge (no gap between ticks
        // and box). Gap is between adjacent boxes' outer edges, created by making the
        // box narrower than the allocated AXIS_LATERAL_OFFSET.
        val boxWidth = CompositeFigureDeckLayout.AXIS_LATERAL_OFFSET - CompositeFigureDeckLayout.AXIS_BOX_GAP

        // Draw bounding box for left axis
        if (layoutInfo.hasLeftAxis) {
            // Box right edge is flush with geomOuter.left (at the axis ticks/line)
            val boxBounds = DoubleRectangle(
                geomOuterAbsolute.left - boxWidth - axisLeftShift,
                geomContentAbsolute.top,
                boxWidth,
                geomContentAbsolute.height
            )
            drawAxisBoundingBox(boxBounds, vAxisTheme)
        }

        // Draw bounding box for right axis
        if (layoutInfo.hasRightAxis) {
            // Box left edge is flush with geomOuter.right (at the axis ticks/line)
            val boxBounds = DoubleRectangle(
                geomOuterAbsolute.right + axisRightShift,
                geomContentAbsolute.top,
                boxWidth,
                geomContentAbsolute.height
            )
            drawAxisBoundingBox(boxBounds, vAxisTheme)
        }
    }

    private fun drawAxisBoundingBox(bounds: DoubleRectangle, axisTheme: AxisTheme) {
        if (!axisTheme.showTooltip()) return

        val borderColor = axisTheme.tooltipColor()
        val fillColor = axisTheme.tooltipFill()
        val strokeWidth = axisTheme.tooltipStrokeWidth()
        val lineType = axisTheme.tooltipLineType()

        val rect = SvgRectElement(bounds).apply {
            fillColor().set(fillColor)
            fillOpacity().set(0.08) // Very subtle fill to not obscure data
            strokeColor().set(borderColor)
            strokeWidth().set(strokeWidth)
            StrokeDashArraySupport.apply(this, strokeWidth, lineType)
        }

        // Add the rect directly to the SVG root so it renders on top of the plot background
        svg.children().add(rect)
    }

    protected override fun clearFigureContent() {
        decorationLayer.children().clear()
        plot.clear()
    }

    internal companion object {
        const val PLOT_ID_PREFIX = "p"
        const val DECORATION_LAYER_ID_PREFIX = "d"

        /**
         * Recursively walks the SVG tree to find elements with the given CSS class
         * and applies a horizontal translate transform to shift axes outward.
         */
        fun shiftAxis(container: SvgNode, className: String, shift: Double) {
            if (container is SvgElement) {
                val elemClass = container.getAttribute("class").get()?.toString() ?: ""
                if (elemClass.contains(className)) {
                    val existing = container.getAttribute("transform").get()?.toString() ?: ""
                    // Prepend translate so it applies in global (post-rotation) space.
                    // SVG transforms apply right-to-left: "translate(x,0) rotate(-90,...)"
                    // means rotate first, then shift horizontally. Appending would shift
                    // in the local pre-rotation coordinate system (vertical instead of horizontal).
                    val newTransform = if (existing.isEmpty()) "translate($shift, 0)" else "translate($shift, 0) $existing"
                    container.setAttribute("transform", newTransform)
                    return
                }
            }
            for (child in container.children()) {
                shiftAxis(child, className, shift)
            }
        }

        /**
         * Finds a descendant element with the given CSS class and applies a horizontal
         * translate to its PARENT element. This is needed for axis titles where the class
         * is on the SvgTextElement (child) but the rotation transform is on the parent
         * SvgGElement. Shifting the text element directly applies in the rotated local
         * space (causing vertical movement). Shifting the parent applies the translate
         * outside the rotation, producing correct horizontal movement.
         */
        fun shiftParentOfClass(container: SvgNode, className: String, shift: Double): Boolean {
            for (child in container.children()) {
                if (child is SvgElement) {
                    val elemClass = child.getAttribute("class").get()?.toString() ?: ""
                    if (elemClass.contains(className)) {
                        // Found the child with the target class — shift the parent (container)
                        if (container is SvgElement) {
                            val existing = container.getAttribute("transform").get()?.toString() ?: ""
                            val newTransform = if (existing.isEmpty()) "translate($shift, 0)" else "translate($shift, 0) $existing"
                            container.setAttribute("transform", newTransform)
                        }
                        return true
                    }
                }
                if (shiftParentOfClass(child, className, shift)) return true
            }
            return false
        }
    }
}
