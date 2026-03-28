
package org.jetbrains.letsPlot.core.plot.builder.layout.figure.composite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.letsPlot.commons.geometry.DoubleRectangle
import org.jetbrains.letsPlot.commons.geometry.DoubleVector
import org.jetbrains.letsPlot.core.plot.builder.FigureSvgRoot
import org.jetbrains.letsPlot.core.plot.builder.GeomLayer
import org.jetbrains.letsPlot.core.plot.builder.buildinfo.FigureBuildInfo
import org.jetbrains.letsPlot.core.plot.builder.layout.figure.FigureLayoutInfo
import org.jetbrains.letsPlot.core.plot.builder.layout.figure.plot.PlotFigureLayoutInfo
import io.mockk.mockk
import io.mockk.every
import org.jetbrains.letsPlot.core.plot.builder.layout.PlotLayoutInfo
import org.jetbrains.letsPlot.core.plot.builder.layout.TileLayoutInfo
import org.jetbrains.letsPlot.core.plot.builder.layout.AxisLayoutInfoQuad

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

    @Test
    fun testLayoutReservesSpaceForTwoLeftAxes() {
        // Two plots both with left axes: the common geom bounds must be pushed right
        // by at least 2 * AXIS_LATERAL_OFFSET.
        val layout = CompositeFigureDeckLayout(
            scaleShareX = ScaleSharePolicy.NONE,
            scaleShareY = ScaleSharePolicy.NONE,
            fitCellAspectRatio = true,
            elementsDefaultSizes = emptyList(),
            innerAlignment = true
        )

        val bounds = DoubleRectangle(0.0, 0.0, 500.0, 300.0)
        val off = CompositeFigureDeckLayout.AXIS_LATERAL_OFFSET

        // Both plots have left axis. We inject PlotFigureLayoutInfo mocks with hasLeftAxis=true.
        val tileInfoMock = mockk<TileLayoutInfo>(relaxed = true).also {
            every { it.geomWithAxisBounds } returns DoubleRectangle(0.0, 0.0, 200.0, 200.0)
            every { it.geomOuterBounds }    returns DoubleRectangle(off, 0.0, 200.0, 200.0)
        }
        val plotLayoutMock = mockk<PlotLayoutInfo>(relaxed = true).also {
            every { it.hasLeftAxis }  returns true
            every { it.hasRightAxis } returns false
            every { it.tiles }        returns listOf(tileInfoMock)
        }
        val figLayoutMock = mockk<PlotFigureLayoutInfo>(relaxed = true).also {
            every { it.geomAreaBounds } returns DoubleRectangle(off, 10.0, 400.0, 280.0)
            every { it.plotLayoutInfo } returns plotLayoutMock
        }

        val elem1 = FakeFigureBuildInfo(layoutInfo = figLayoutMock)
        val elem2 = FakeFigureBuildInfo(layoutInfo = figLayoutMock)

        val result = layout.doLayout(bounds, listOf(elem1, elem2))
        assertEquals(2, result.size)

        // Both plots should be laid out into the same common geom bounds,
        // and that bounds.left should be at least 2 * off from the figure origin.
        val res1 = result[0] as FakeFigureBuildInfo
        val res2 = result[1] as FakeFigureBuildInfo
        val commonLeft = res1.layoutedGeomBounds!!.left
        assertTrue(commonLeft >= 2 * off,
            "Expected commonGeomBounds.left >= ${2 * off} but was $commonLeft")
        assertEquals(res1.layoutedGeomBounds, res2.layoutedGeomBounds,
            "Both plots must share the same geom bounds")
    }

    @Test
    fun testLayoutReservesSpaceForMixedAxes() {
        // 1 left-axis plot + 1 right-axis plot: space must be reserved on both sides.
        val layout = CompositeFigureDeckLayout(
            scaleShareX = ScaleSharePolicy.NONE,
            scaleShareY = ScaleSharePolicy.NONE,
            fitCellAspectRatio = true,
            elementsDefaultSizes = emptyList(),
            innerAlignment = true
        )

        val bounds = DoubleRectangle(0.0, 0.0, 500.0, 300.0)
        val off = CompositeFigureDeckLayout.AXIS_LATERAL_OFFSET

        fun makeFigMock(leftAxis: Boolean, rightAxis: Boolean): PlotFigureLayoutInfo {
            val tile = mockk<TileLayoutInfo>(relaxed = true).also {
                every { it.geomWithAxisBounds } returns DoubleRectangle(0.0, 0.0, 200.0, 200.0)
                every { it.geomOuterBounds }    returns DoubleRectangle(off, 0.0, 200.0, 200.0)
            }
            val pl = mockk<PlotLayoutInfo>(relaxed = true).also {
                every { it.hasLeftAxis }  returns leftAxis
                every { it.hasRightAxis } returns rightAxis
                every { it.tiles }        returns listOf(tile)
            }
            return mockk<PlotFigureLayoutInfo>(relaxed = true).also {
                every { it.geomAreaBounds } returns DoubleRectangle(off, 10.0, 300.0, 280.0)
                every { it.plotLayoutInfo } returns pl
            }
        }

        val leftPlot  = FakeFigureBuildInfo(layoutInfo = makeFigMock(leftAxis = true,  rightAxis = false))
        val rightPlot = FakeFigureBuildInfo(layoutInfo = makeFigMock(leftAxis = false, rightAxis = true))

        val result = layout.doLayout(bounds, listOf(leftPlot, rightPlot))
        assertEquals(2, result.size)

        val commonBounds = (result[0] as FakeFigureBuildInfo).layoutedGeomBounds!!
        // Left space >= off (1 left axis)
        assertTrue(commonBounds.left >= off,
            "Expected left space >= $off, was ${commonBounds.left}")
        // Right edge must not reach SVG right edge (space reserved on the right too)
        assertTrue(commonBounds.right <= bounds.right - off,
            "Expected right space >= $off, commonBounds.right=${commonBounds.right}")
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
