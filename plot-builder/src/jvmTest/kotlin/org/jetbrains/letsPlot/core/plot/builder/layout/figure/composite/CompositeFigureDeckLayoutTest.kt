
package org.jetbrains.letsPlot.core.plot.builder.layout.figure.composite

import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.builder.FigureSvgRoot
import org.jetbrains.letsPlot.core.plot.builder.GeomLayer
import org.jetbrains.letsPlot.core.plot.builder.buildinfo.FigureBuildInfo
import org.jetbrains.letsPlot.core.plot.builder.layout.figure.FigureLayoutInfo
import org.jetbrains.letsPlot.core.plot.builder.layout.figure.plot.PlotFigureLayoutInfo
import io.mockk.mockk
import io.mockk.every

class CompositeFigureDeckLayoutTest {

    @Test
    fun testLayoutWithoutAlignment() {
        val layout = CompositeFigureDeckLayout(
            scaleShareX = ScaleSharePolicy.NONE,
            scaleShareY = ScaleSharePolicy.NONE,
            fitCellAspectRatio = true,
            elementsDefaultSizes = emptyList(),
            innerAlignment = false
        )

        val bounds = DoubleRectangle(0.0, 0.0, 100.0, 100.0)
        val elements = listOf(
            FakeFigureBuildInfo(),
            FakeFigureBuildInfo()
        )

        val result = layout.doLayout(bounds, elements)

        assertEquals(2, result.size)
        assertEquals(bounds, result[0]!!.bounds)
        assertEquals(bounds, result[1]!!.bounds)
    }

    @Test
    fun testLayoutWithAlignment() {
        val layout = CompositeFigureDeckLayout(
            scaleShareX = ScaleSharePolicy.NONE,
            scaleShareY = ScaleSharePolicy.NONE,
            fitCellAspectRatio = true,
            elementsDefaultSizes = emptyList(),
            innerAlignment = true
        )

        val bounds = DoubleRectangle(0.0, 0.0, 100.0, 100.0)
        
        // Mock PlotFigureLayoutInfo
        val layoutInfo1 = mockk<PlotFigureLayoutInfo>(relaxed = true)
        every { layoutInfo1.geomAreaBounds } returns DoubleRectangle(10.0, 10.0, 80.0, 80.0)
        
        val layoutInfo2 = mockk<PlotFigureLayoutInfo>(relaxed = true)
        every { layoutInfo2.geomAreaBounds } returns DoubleRectangle(20.0, 20.0, 60.0, 60.0)

        val info1 = FakeFigureBuildInfo(layoutInfo = layoutInfo1)
        val info2 = FakeFigureBuildInfo(layoutInfo = layoutInfo2)

        val elements = listOf(info1, info2)

        val result = layout.doLayout(bounds, elements)

        // Expected intersection of geom areas:
        // Rect1: x[10, 90], y[10, 90]
        // Rect2: x[20, 80], y[20, 80]
        // Intersection: x[20, 80], y[20, 80] -> Rect(20, 20, 60, 60)
        
        val expectedGeomBounds = DoubleRectangle(20.0, 20.0, 60.0, 60.0)

        val res1 = result[0] as FakeFigureBuildInfo
        val res2 = result[1] as FakeFigureBuildInfo

        assertEquals(expectedGeomBounds, res1.layoutedGeomBounds)
        assertEquals(expectedGeomBounds, res2.layoutedGeomBounds)
    }

    class FakeFigureBuildInfo(
        override val isComposite: Boolean = false,
        override val bounds: DoubleRectangle = DoubleRectangle(0.0, 0.0, 0.0, 0.0),
        override val layoutInfo: FigureLayoutInfo = mockk<FigureLayoutInfo>()
    ) : FigureBuildInfo {
        var layoutedGeomBounds: DoubleRectangle? = null

        override val computationMessages: List<String> get() = emptyList()
        override val containsLiveMap: Boolean get() = false

        override fun createSvgRoot(): FigureSvgRoot {
            throw NotImplementedError()
        }

        override fun injectLiveMapProvider(f: (tiles: List<List<GeomLayer>>, spec: Map<String, Any>) -> Any) {
        }

        override fun withBounds(bounds: DoubleRectangle): FigureBuildInfo {
            return FakeFigureBuildInfo(isComposite, bounds, layoutInfo)
        }

        override fun layoutedByOuterSize(): FigureBuildInfo {
            return this
        }

        override fun layoutedByGeomBounds(geomBounds: DoubleRectangle): FigureBuildInfo {
            val newInfo = FakeFigureBuildInfo(isComposite, bounds, layoutInfo)
            newInfo.layoutedGeomBounds = geomBounds
            return newInfo
        }

        override fun withAxisShift(leftShift: Double, rightShift: Double): FigureBuildInfo {
            val newInfo = FakeFigureBuildInfo(isComposite, bounds, layoutInfo)
            newInfo.layoutedGeomBounds = this.layoutedGeomBounds
            return newInfo
        }

        override fun withPreferredSize(size: DoubleVector): FigureBuildInfo {
            return this
        }
    }
}
