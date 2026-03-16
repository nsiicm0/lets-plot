/*
 * Copyright (c) 2023. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.dom.createElement
import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.commons.geometry.Vector
import org.jetbrains.letsPlot.commons.registration.CompositeRegistration
import org.jetbrains.letsPlot.commons.registration.Registration
import org.jetbrains.letsPlot.core.interact.event.ToolEventDispatcher
import org.jetbrains.letsPlot.core.platf.dom.DomMouseEventMapper
import org.jetbrains.letsPlot.core.plot.builder.GeomLayer
import org.jetbrains.letsPlot.core.plot.builder.PlotContainer
import org.jetbrains.letsPlot.core.plot.builder.PlotSvgRoot
import org.jetbrains.letsPlot.core.plot.builder.buildinfo.FigureBuildInfo
import org.jetbrains.letsPlot.core.plot.builder.interact.CompositeToolEventDispatcher
import org.jetbrains.letsPlot.core.plot.builder.subPlots.CompositeFigureSvgRoot
import org.jetbrains.letsPlot.core.plot.livemap.CursorServiceConfig
import org.jetbrains.letsPlot.core.plot.livemap.LiveMapProviderUtil
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgNodeContainer
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgSvgElement
import org.jetbrains.letsPlot.platf.w3c.canvas.DomCanvasControl
import org.jetbrains.letsPlot.platf.w3c.dom.css.*
import org.jetbrains.letsPlot.platf.w3c.dom.css.enumerables.CssCursor
import org.jetbrains.letsPlot.platf.w3c.dom.css.enumerables.CssPosition
import org.jetbrains.letsPlot.platf.w3c.mapping.svg.SvgRootDocumentMapper
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.Node
import org.w3c.dom.svg.SVGSVGElement

internal class FigureToHtml(
    private val buildInfo: FigureBuildInfo,
//    private val containerElement: HTMLElement,
    private val parentElement: HTMLElement,
) {

//    private val parentElement: HTMLElement = if (buildInfo.isComposite) {
//        // The `containerElement` may also contain "computation messages".
//        // Container for a composite figure must be another `div`
//        // because it is going to have "relative" positioning.
//        document.createElement("div") {
//            containerElement.appendChild(this)
//        } as HTMLElement
//    } else {
//        containerElement
//    }

    fun eval(isRoot: Boolean): Result {

        val buildInfo = buildInfo.layoutedByOuterSize()
//        containerElement.style.apply {
//            width = "${buildInfo.layoutInfo.figureSize.x}px"
//            height = "${buildInfo.layoutInfo.figureSize.y}px"
//        }

        buildInfo.injectLiveMapProvider { tiles: List<List<GeomLayer>>, spec: Map<String, Any> ->
            val cursorServiceConfig = CursorServiceConfig()
            LiveMapProviderUtil.injectLiveMapProvider(tiles, spec, cursorServiceConfig)
            cursorServiceConfig
        }

        val svgRoot = buildInfo.createSvgRoot()

        if (isRoot) {
            // Setup fixed dimensions for plot wrapper element.
            setupRootHTMLElement(
                parentElement,
                svgRoot.bounds.dimension
            )
        }

        val (toolEventDispatcher, eventsRegistration) = if (svgRoot is CompositeFigureSvgRoot) {
            processCompositeFigure(
                svgRoot,
                origin = null,      // The topmost SVG
                parentElement = parentElement,
            )
        } else {
            processPlotFigure(
                svgRoot = svgRoot as PlotSvgRoot,
                containerElement = parentElement,
                eventElement = parentElement,
//                eventArea = buildInfo.bounds
                eventArea = DoubleRectangle(DoubleVector.ZERO, buildInfo.bounds.dimension)
            )
        }

        val domCleanupRegistration = object : Registration() {
            override fun doRemove() {
                while (parentElement.firstChild != null) {
                    parentElement.removeChild(parentElement.firstChild!!)
                }
            }
        }

        return Result(
            toolEventDispatcher,
            CompositeRegistration().add(
                eventsRegistration,
                domCleanupRegistration
            )
        )
    }

    data class Result(
        val toolEventDispatcher: ToolEventDispatcher,
        val figureRegistration: Registration
    )

    companion object {
        private fun processPlotFigure(
            svgRoot: PlotSvgRoot,
            containerElement: HTMLElement,
            eventElement: HTMLElement,
            eventArea: DoubleRectangle
        ): Pair<ToolEventDispatcher, Registration> {

            val plotContainer = PlotContainer(svgRoot)
            val (rootSVG, cleanupRegistration) = buildPlotFigureSVG(plotContainer, eventElement, eventArea)
            rootSVG.style.setCursor(CssCursor.CROSSHAIR)

            // Livemap cursor pointer
            if (svgRoot.isLiveMap) {
                val cursorServiceConfig = svgRoot.liveMapCursorServiceConfig as CursorServiceConfig
                cursorServiceConfig.defaultSetter { rootSVG.style.setCursor(CssCursor.CROSSHAIR) }
                cursorServiceConfig.pointerSetter { rootSVG.style.setCursor(CssCursor.POINTER) }
            }

            containerElement.appendChild(rootSVG)
            return plotContainer.toolEventDispatcher to cleanupRegistration
        }

        private fun processCompositeFigure(
            svgRoot: CompositeFigureSvgRoot,
            origin: DoubleVector?,
            parentElement: HTMLElement,
        ): Pair<ToolEventDispatcher, Registration> {
            svgRoot.ensureContentBuilt()

            val rootSvgSvg: SvgSvgElement = svgRoot.svg
            val domSVGSVG: SVGSVGElement = mapSvgToSVG(rootSvgSvg)
            val rootNode: Node = if (origin == null) {
                domSVGSVG
            } else {
                // Not a root - put in "container" with absolute positioning.
                createContainerElement(origin).apply {
                    appendChild(domSVGSVG)
                }
            }

            parentElement.appendChild(rootNode)

            @Suppress("NAME_SHADOWING")
            val origin = origin ?: DoubleVector.ZERO

            // Sub-figures
            val elementToolEventDispatchers = ArrayList<ToolEventDispatcher>()
            val elementRegistractions = CompositeRegistration()

            val domSVGSVGs = ArrayList<SVGSVGElement>()
            val plotOrigins = ArrayList<DoubleVector>()

            for (figureSvgRoot in svgRoot.elements) {
                val elementOrigin = figureSvgRoot.bounds.origin.add(origin)
                val (toolEventDispatcher, registration) = if (figureSvgRoot is PlotSvgRoot) {
                    // Create "container" with absolute positioning.
                    val figureContainer = createContainerElement(elementOrigin)
                    figureContainer.style.setProperty("pointer-events", "none") // Let hovers pass through to parent
                    figureContainer.style.setProperty("overflow", "visible")
                    parentElement.appendChild(figureContainer)
                    val out = processPlotFigure(
                        svgRoot = figureSvgRoot,
                        containerElement = figureContainer,
                        eventElement = parentElement, // Map mouse events to the shared parent instead of the isolated container
                        eventArea = DoubleRectangle(elementOrigin, figureSvgRoot.bounds.dimension)
                    )
                    
                    val svgNode = figureContainer.firstChild as? SVGSVGElement
                    if (svgNode != null) {
                        domSVGSVGs.add(svgNode)
                        plotOrigins.add(figureSvgRoot.bounds.origin)
                    }
                    out
                } else {
                    figureSvgRoot as CompositeFigureSvgRoot
                    processCompositeFigure(figureSvgRoot, elementOrigin, parentElement)
                }

                elementToolEventDispatchers.add(toolEventDispatcher)
                elementRegistractions.add(registration)
            }

            // --- GGDECK OVERLAP FIXES ---
            
            // 1. Tooltips Z-Index Extraction
            // The tooltip HTML content lives in a `<g>` with an id from `decorationLayerId`.
            // SVG clipping and painter's algorithm traps them underneath the next subplot.
            // We create a global overlay SVG, copy the first plot's CSS into it, and hoist all decoration layers to it.
            if (domSVGSVGs.isNotEmpty()) {
                val tooltipOverlayContainer = createContainerElement(origin ?: DoubleVector.ZERO)
                tooltipOverlayContainer.style.setProperty("pointer-events", "none")
                tooltipOverlayContainer.style.setProperty("overflow", "visible")
                // Make sure this container sits strictly over all previous subplots
                tooltipOverlayContainer.style.setProperty("z-index", "9999")
                parentElement.appendChild(tooltipOverlayContainer)

                val overlaySvg = document.createElementNS("http://www.w3.org/2000/svg", "svg") as SVGSVGElement
                overlaySvg.style.setProperty("overflow", "visible")
                // Copy the first CSS style to ensure tooltip text renders correctly
                val styleNode = domSVGSVGs.first().querySelector("style")?.cloneNode(true)
                if (styleNode != null) {
                    overlaySvg.appendChild(styleNode)
                }
                
                // Extract every single decorationLayer from each subplot's SVG and append it into the single overlaySvg.
                for ((index, plotSvg) in domSVGSVGs.withIndex()) {
                    // Search for inner nodes that might be the decoration layer. The ID prefix is "d".
                    // However, we don't have direct access to the ID string here easily. Let's find any group at the very end.
                    // The easiest heuristic is to just grab the LAST <g> element in the SVG, which is where TooltipRenderer attaches.
                    val childNodes = plotSvg.childNodes
                    if (childNodes.length > 0) {
                        val lastChild = childNodes.item(childNodes.length - 1)
                        if (lastChild != null && lastChild.nodeName.lowercase() == "g") {
                            // Apply the original container offset!
                            // Because we moved the `<g>` out of its absolute container into the origin container, we must preserve its x/y shift
                            val elementOrigin = plotOrigins[index]
                            val innerGroup = document.createElementNS("http://www.w3.org/2000/svg", "g")
                            innerGroup.setAttribute("transform", "translate(${elementOrigin.x}, ${elementOrigin.y})")
                            
                            // Extract to top
                            plotSvg.removeChild(lastChild)
                            innerGroup.appendChild(lastChild)
                            overlaySvg.appendChild(innerGroup)
                        }
                    }
                }
                tooltipOverlayContainer.appendChild(overlaySvg)

                // 2. Y-Axis Lateral Spacing & Color Matching
                // Let's-Plot lacks native support for duplicating axes laterally inside the identical geometry bounds.
                // We identify left/right axes in the stacked plots and shift them visually via CSS transform.
                var leftAxisCount = 0
                var rightAxisCount = 0
                
                data class GeomStyle(
                    val stroke: String,
                    val dashArray: String,
                    val fill: String,
                    val strokeOpacity: String,
                    val fillOpacity: String,
                    val opacity: String
                )
                fun extractGeomStyle(plotSvg: org.w3c.dom.Element): GeomStyle? {
                    val paths = plotSvg.querySelectorAll("path, line, rect, circle, polygon")
                    for (i in 0 until paths.length) {
                        val el = paths.item(i) as? org.w3c.dom.Element ?: continue
                        var parent = el.parentElement
                        var isAxisOrGrid = false
                        while (parent != null && parent != plotSvg) {
                            val className = parent.getAttribute("class") ?: ""
                            if (className.contains("axis") || className.contains("grid") || className.contains("background")) {
                                isAxisOrGrid = true
                                break
                            }
                            parent = parent.parentElement
                        }
                        if (isAxisOrGrid) continue
                        
                        val stroke = el.getAttribute("stroke") ?: ""
                        val strokeTrimmed = stroke.replace(" ", "")
                        val isStrokeValid = strokeTrimmed.isNotEmpty() && strokeTrimmed != "none" && strokeTrimmed != "transparent" && strokeTrimmed != "#e9e9e9" && strokeTrimmed != "white" && !strokeTrimmed.contains("rgb(255,255,255)") && !strokeTrimmed.contains("rgb(233,233,233)") && !strokeTrimmed.contains("rgb(71,71,71)")
                        
                        val fill = el.getAttribute("fill") ?: ""
                        val fillTrimmed = fill.replace(" ", "")
                        val isFillValid = fillTrimmed.isNotEmpty() && fillTrimmed != "none" && fillTrimmed != "transparent" && fillTrimmed != "#e9e9e9" && fillTrimmed != "white" && !fillTrimmed.contains("rgb(255,255,255)") && !fillTrimmed.contains("rgb(233,233,233)") && !fillTrimmed.contains("rgb(71,71,71)")
                        
                        if (isStrokeValid || isFillValid) {
                            val dashArray = el.getAttribute("stroke-dasharray") ?: ""
                            val strokeOpacity = el.getAttribute("stroke-opacity") ?: ""
                            val fillOpacity = el.getAttribute("fill-opacity") ?: ""
                            val opacity = el.getAttribute("opacity") ?: ""
                            
                            // If it's a line/path with no fill, default to transparent faint version of stroke for nice aesthetics
                            val resolvedFill = if (isFillValid) fill else if (isStrokeValid) stroke else "none"
                            val resolvedFillOpacity = if (isFillValid) (if (fillOpacity.isNotEmpty()) fillOpacity else "0.1") else if (isStrokeValid) "0.05" else ""
                            
                            return GeomStyle(stroke, dashArray, resolvedFill, strokeOpacity, resolvedFillOpacity, opacity)
                        }
                    }
                    return null
                }
                
                // lateralOffset governs the margin distance between overlaid axes
                val lateralOffset = 70
                
                for (plotSvg in domSVGSVGs) {
                    val axisLeftGroup = plotSvg.querySelector("g.axis-left")
                    val axisRightGroup = plotSvg.querySelector("g.axis-right")
                    val axisTitleText = plotSvg.querySelector("text.axis-title-y")
                    axisTitleText?.removeAttribute("transform")
                    val axisTitleGroup = axisTitleText?.parentElement
                    val geomStyle = extractGeomStyle(plotSvg)
                    val plotColor = geomStyle?.stroke?.takeIf { it.isNotEmpty() } ?: geomStyle?.fill
                    val plotDashArray = geomStyle?.dashArray
                    
                        if (axisLeftGroup != null) {
                        if (leftAxisCount > 0) {
                            val existingTransform = axisLeftGroup.getAttribute("transform") ?: ""
                            axisLeftGroup.setAttribute("transform", "$existingTransform translate(-${leftAxisCount * lateralOffset}, 0)")
                        }
                        if (axisTitleText != null) {
                            val oldParent = axisTitleText.parentElement
                            var yMiddle = 175.0
                            var spineMinY = Double.MAX_VALUE
                            var spineMaxY = -Double.MAX_VALUE
                            val spines = axisLeftGroup.querySelectorAll("line")
                            for (i in 0 until spines.length) {
                                val line = spines.item(i) as? org.w3c.dom.Element
                                if (line?.getAttribute("x1") == "0" && line.getAttribute("x2") == "0") {
                                    val y1 = line.getAttribute("y1")?.toDoubleOrNull() ?: 0.0
                                    val y2 = line.getAttribute("y2")?.toDoubleOrNull() ?: 0.0
                                    spineMinY = kotlin.math.min(spineMinY, kotlin.math.min(y1, y2))
                                    spineMaxY = kotlin.math.max(spineMaxY, kotlin.math.max(y1, y2))
                                }
                            }
                            if (spineMinY != Double.MAX_VALUE) {
                                yMiddle = (spineMinY + spineMaxY) / 2.0
                            }
                            axisTitleText.setAttribute("transform", "translate(-45, $yMiddle) rotate(-90)")
                            axisTitleText.setAttribute("text-anchor", "middle")
                            axisLeftGroup.appendChild(axisTitleText)
                            if (oldParent != null && oldParent.childElementCount == 0) {
                                oldParent.remove()
                            }
                        }
                        
                        if (plotColor != null) {
                            var spineHeight = 0.0
                            val spines = axisLeftGroup.querySelectorAll("line")
                            for (i in 0 until spines.length) {
                                val line = spines.item(i) as? org.w3c.dom.Element
                                if (line?.getAttribute("x1") == "0" && line.getAttribute("x2") == "0") {
                                    val y1 = line.getAttribute("y1")?.toDoubleOrNull() ?: 0.0
                                    val y2 = line.getAttribute("y2")?.toDoubleOrNull() ?: 0.0
                                    spineHeight = kotlin.math.max(spineHeight, kotlin.math.abs(y2 - y1))
                                }
                            }
                            if (spineHeight == 0.0) spineHeight = 350.0 // fallback
                            
                            val rect = document.createElementNS("http://www.w3.org/2000/svg", "rect")
                            rect.setAttribute("x", "-58")
                            rect.setAttribute("y", "-10")
                            rect.setAttribute("width", "62")
                            rect.setAttribute("height", "${spineHeight + 20}")
                            if (geomStyle != null) {
                                rect.setAttribute("fill", geomStyle.fill)
                                if (geomStyle.fillOpacity.isNotEmpty()) rect.setAttribute("fill-opacity", geomStyle.fillOpacity)
                                rect.setAttribute("stroke", geomStyle.stroke)
                                if (geomStyle.strokeOpacity.isNotEmpty()) rect.setAttribute("stroke-opacity", geomStyle.strokeOpacity)
                                if (geomStyle.dashArray.isNotEmpty()) rect.setAttribute("stroke-dasharray", geomStyle.dashArray)
                                if (geomStyle.opacity.isNotEmpty()) rect.setAttribute("opacity", geomStyle.opacity)
                            } else {
                                rect.setAttribute("fill", "none")
                                rect.setAttribute("stroke", plotColor)
                                if (plotDashArray != null && plotDashArray.isNotEmpty()) {
                                    rect.setAttribute("stroke-dasharray", plotDashArray)
                                }
                            }
                            rect.setAttribute("stroke-width", "1.5")
                            rect.setAttribute("rx", "4")
                            axisLeftGroup.prepend(rect)
                            
                            axisLeftGroup.querySelectorAll("line, path").let { lines ->
                                for (i in 0 until lines.length) { 
                                    (lines.item(i) as? org.w3c.dom.Element)?.let { 
                                        val currentStyle = it.getAttribute("style") ?: ""
                                        it.setAttribute("style", "$currentStyle; stroke: $plotColor !important;")
                                    }
                                }
                            }
                            axisLeftGroup.querySelectorAll("text").let { texts ->
                                for (i in 0 until texts.length) { 
                                    (texts.item(i) as? org.w3c.dom.Element)?.let {
                                        val currentStyle = it.getAttribute("style") ?: ""
                                        it.setAttribute("style", "$currentStyle; fill: $plotColor !important;")
                                    }
                                }
                            }
                            val titleTextElements = if (axisTitleGroup?.tagName?.lowercase() == "text") {
                                listOf(axisTitleGroup)
                            } else {
                                axisTitleGroup?.querySelectorAll("text")?.let { nodeList ->
                                    (0 until nodeList.length).mapNotNull { idx -> nodeList.item(idx) as? org.w3c.dom.Element }
                                } ?: emptyList()
                            }
                            titleTextElements.forEach { textEl ->
                                val currentStyle = textEl.getAttribute("style") ?: ""
                                textEl.setAttribute("style", "$currentStyle; fill: $plotColor !important;")
                            }
                            leftAxisCount++
                        }
                    }
                    if (axisRightGroup != null) {
                        if (rightAxisCount > 0) {
                            val existingTransform = axisRightGroup.getAttribute("transform") ?: ""
                            axisRightGroup.setAttribute("transform", "$existingTransform translate(${rightAxisCount * lateralOffset}, 0)")
                        }
                        if (axisTitleText != null) {
                            val oldParent = axisTitleText.parentElement
                            var yMiddle = 175.0
                            var spineMinY = Double.MAX_VALUE
                            var spineMaxY = -Double.MAX_VALUE
                            val spines = axisRightGroup.querySelectorAll("line")
                            for (i in 0 until spines.length) {
                                val line = spines.item(i) as? org.w3c.dom.Element
                                if (line?.getAttribute("x1") == "0" && line.getAttribute("x2") == "0") {
                                    val y1 = line.getAttribute("y1")?.toDoubleOrNull() ?: 0.0
                                    val y2 = line.getAttribute("y2")?.toDoubleOrNull() ?: 0.0
                                    spineMinY = kotlin.math.min(spineMinY, kotlin.math.min(y1, y2))
                                    spineMaxY = kotlin.math.max(spineMaxY, kotlin.math.max(y1, y2))
                                }
                            }
                            if (spineMinY != Double.MAX_VALUE) {
                                yMiddle = (spineMinY + spineMaxY) / 2.0
                            }
                            axisTitleText.setAttribute("transform", "translate(45, $yMiddle) rotate(-90)")
                            axisTitleText.setAttribute("text-anchor", "middle")
                            axisRightGroup.appendChild(axisTitleText)
                            if (oldParent != null && oldParent.childElementCount == 0) {
                                oldParent.remove()
                            }
                        }
                        
                        if (plotColor != null) {
                            var spineHeight = 0.0
                            val spines = axisRightGroup.querySelectorAll("line")
                            for (i in 0 until spines.length) {
                                val line = spines.item(i) as? org.w3c.dom.Element
                                if (line?.getAttribute("x1") == "0" && line.getAttribute("x2") == "0") {
                                    val y1 = line.getAttribute("y1")?.toDoubleOrNull() ?: 0.0
                                    val y2 = line.getAttribute("y2")?.toDoubleOrNull() ?: 0.0
                                    spineHeight = kotlin.math.max(spineHeight, kotlin.math.abs(y2 - y1))
                                }
                            }
                            if (spineHeight == 0.0) spineHeight = 350.0 // fallback
                            
                            val rect = document.createElementNS("http://www.w3.org/2000/svg", "rect")
                            rect.setAttribute("x", "-4")
                            rect.setAttribute("y", "-10")
                            rect.setAttribute("width", "62")
                            rect.setAttribute("height", "${spineHeight + 20}")
                            if (geomStyle != null) {
                                rect.setAttribute("fill", geomStyle.fill)
                                if (geomStyle.fillOpacity.isNotEmpty()) rect.setAttribute("fill-opacity", geomStyle.fillOpacity)
                                rect.setAttribute("stroke", geomStyle.stroke)
                                if (geomStyle.strokeOpacity.isNotEmpty()) rect.setAttribute("stroke-opacity", geomStyle.strokeOpacity)
                                if (geomStyle.dashArray.isNotEmpty()) rect.setAttribute("stroke-dasharray", geomStyle.dashArray)
                                if (geomStyle.opacity.isNotEmpty()) rect.setAttribute("opacity", geomStyle.opacity)
                            } else {
                                rect.setAttribute("fill", "none")
                                rect.setAttribute("stroke", plotColor)
                                if (plotDashArray != null && plotDashArray.isNotEmpty()) {
                                    rect.setAttribute("stroke-dasharray", plotDashArray)
                                }
                            }
                            rect.setAttribute("stroke-width", "1.5")
                            rect.setAttribute("rx", "4")
                            axisRightGroup.prepend(rect)
                            
                            axisRightGroup.querySelectorAll("line, path").let { lines ->
                                for (i in 0 until lines.length) { 
                                    (lines.item(i) as? org.w3c.dom.Element)?.let {
                                        val currentStyle = it.getAttribute("style") ?: ""
                                        it.setAttribute("style", "$currentStyle; stroke: $plotColor !important;")
                                    }
                                }
                            }
                            axisRightGroup.querySelectorAll("text").let { texts ->
                                for (i in 0 until texts.length) { 
                                    (texts.item(i) as? org.w3c.dom.Element)?.let {
                                        val currentStyle = it.getAttribute("style") ?: ""
                                        it.setAttribute("style", "$currentStyle; fill: $plotColor !important;")
                                    }
                                }
                            }
                            val titleTextElements = if (axisTitleGroup?.tagName?.lowercase() == "text") {
                                listOf(axisTitleGroup)
                            } else {
                                axisTitleGroup?.querySelectorAll("text")?.let { nodeList ->
                                    (0 until nodeList.length).mapNotNull { idx -> nodeList.item(idx) as? org.w3c.dom.Element }
                                } ?: emptyList()
                            }
                            titleTextElements.forEach { textEl ->
                                val currentStyle = textEl.getAttribute("style") ?: ""
                                textEl.setAttribute("style", "$currentStyle; fill: $plotColor !important;")
                            }
                            rightAxisCount++
                        }
                    }
                }
                
                // Add margins to the parent container so that shifted axes are not clipped by the browser window edge
                if (leftAxisCount > 0) {
                    parentElement.style.marginLeft = "${leftAxisCount * lateralOffset}px"
                }
                if (rightAxisCount > 0) {
                    parentElement.style.marginRight = "${rightAxisCount * lateralOffset}px"
                }
            }

            return CompositeToolEventDispatcher(elementToolEventDispatchers) to elementRegistractions
        }

        fun setupRootHTMLElement(element: HTMLElement, size: DoubleVector) {
//            val style = "position: relative;"  < -- ggbunch doesn't work without setting the container's width/height.
            val style = "position: relative; width: ${size.x}px; height: ${size.y}px;"
            element.setAttribute("style", style)
        }

        fun createContainerElement(origin: DoubleVector): HTMLElement {
            return document.createElement("div") {
                setAttribute(
                    "style",
                    "position: absolute; left: ${origin.x}px; top: ${origin.y}px; overflow: visible;"
                )
            } as HTMLElement
        }

        private fun mapSvgToSVG(svg: SvgSvgElement): SVGSVGElement {
            val mapper = SvgRootDocumentMapper(svg)
            SvgNodeContainer(svg)
            mapper.attachRoot()
            return mapper.target
        }


        private fun buildPlotFigureSVG(
            plotContainer: PlotContainer,
            parentElement: Element,
            eventArea: DoubleRectangle,
        ): Pair<SVGSVGElement, Registration> {
            val svg: SVGSVGElement = mapSvgToSVG(plotContainer.svg)
            svg.style.setProperty("overflow", "visible")

            if (plotContainer.isLiveMap) {
                svg.style.run {
                    setPosition(CssPosition.RELATIVE)
                }
            }

            val plotMouseEventMapper = DomMouseEventMapper(parentElement, eventArea)

            val eventsRegistration = CompositeRegistration()
            eventsRegistration.add(Registration.from(plotMouseEventMapper))

            plotContainer.mouseEventPeer.addEventSource(plotMouseEventMapper)

            plotContainer.liveMapCanvasDrawables.forEach { liveMapCanvasDrawable ->
                val bounds = liveMapCanvasDrawable.bounds().get()
                val liveMapDiv = document.createElement("div") as HTMLElement

                liveMapDiv.style.run {
                    setLeft(bounds.origin.x.toDouble())
                    setTop(bounds.origin.y.toDouble())
                    setWidth(bounds.dimension.x)
                    setPosition(CssPosition.RELATIVE)
                }

                val canvasMouseEventMapper = DomMouseEventMapper(
                    parentElement,
                    DoubleRectangle(
                        eventArea.origin.add(bounds.origin.toDoubleVector()),
                        bounds.dimension.toDoubleVector()
                    )
                )
                eventsRegistration.add(Registration.from(canvasMouseEventMapper))

                val canvasControl = DomCanvasControl(
                    myRootElement = liveMapDiv,
                    size = Vector(bounds.dimension.x, bounds.dimension.y),
                    mouseEventSource = canvasMouseEventMapper
                )

                val liveMapReg = liveMapCanvasDrawable.mapToCanvas(canvasControl)
                parentElement.appendChild(liveMapDiv)

                liveMapDiv.onDisconnect(liveMapReg::dispose)
            }

            return svg to eventsRegistration
        }

        private fun Node.onDisconnect(onDisconnected: () -> Unit): Int {
            fun checkConnection() {
                if (!isConnected) {
                    onDisconnected()
                } else {
                    window.requestAnimationFrame { checkConnection() }
                }
            }
            return window.requestAnimationFrame { checkConnection() }
        }
    }
}