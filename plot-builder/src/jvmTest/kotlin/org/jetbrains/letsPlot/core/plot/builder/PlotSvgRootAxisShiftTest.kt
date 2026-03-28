/*
 * Copyright (c) 2025. JetBrains s.r.o.
 * Use of this source code is governed by the MIT license that can be found in the LICENSE file.
 */

package org.jetbrains.letsPlot.core.plot.builder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.letsPlot.datamodel.svg.dom.SvgGElement

/**
 * Unit tests for the companion object SVG-manipulation helpers in [PlotSvgRoot]:
 * - [PlotSvgRoot.shiftAxis]
 * - [PlotSvgRoot.shiftParentOfClass]
 *
 * These helpers are declared `internal` so they are accessible from the test module.
 */
class PlotSvgRootAxisShiftTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun makeGroup(vararg classes: String): SvgGElement {
        return SvgGElement().apply {
            if (classes.isNotEmpty()) {
                setAttribute("class", classes.joinToString(" "))
            }
        }
    }

    private fun transformOf(elem: SvgGElement): String? =
        elem.getAttribute("transform").get()?.toString()

    // -----------------------------------------------------------------------
    // shiftAxis tests
    // -----------------------------------------------------------------------

    @Test
    fun testShiftAxis_noMatchingClass_noTransformAdded() {
        val root = makeGroup("some-other-class")
        PlotSvgRoot.shiftAxis(root, "axis-left", 20.0)
        assertEquals(null, transformOf(root), "No transform should be added when class doesn't match")
    }

    @Test
    fun testShiftAxis_matchingClass_translatePrepended() {
        val elem = makeGroup("axis-left")
        val root = SvgGElement().apply { children().add(elem) }

        PlotSvgRoot.shiftAxis(root, "axis-left", -30.0)

        assertEquals("translate(-30.0, 0)", transformOf(elem))
    }

    @Test
    fun testShiftAxis_matchingClassAtRoot_translatePrepended() {
        val root = makeGroup("axis-right")
        PlotSvgRoot.shiftAxis(root, "axis-right", 15.0)
        assertEquals("translate(15.0, 0)", transformOf(root))
    }

    @Test
    fun testShiftAxis_existingTransform_translatePrependedBeforeExisting() {
        val elem = makeGroup("axis-left").apply {
            setAttribute("transform", "rotate(-90, 50, 50)")
        }
        val root = SvgGElement().apply { children().add(elem) }

        PlotSvgRoot.shiftAxis(root, "axis-left", -20.0)

        // Translate must come BEFORE rotate so it applies in global space (SVG applies right-to-left)
        assertEquals("translate(-20.0, 0) rotate(-90, 50, 50)", transformOf(elem))
    }

    @Test
    fun testShiftAxis_classContainsMatchAsSubstring() {
        // "axis-left-extra" contains "axis-left" → should still match (contains semantics)
        val elem = makeGroup("axis-left-extra")
        val root = SvgGElement().apply { children().add(elem) }

        PlotSvgRoot.shiftAxis(root, "axis-left", 10.0)
        assertEquals("translate(10.0, 0)", transformOf(elem))
    }

    @Test
    fun testShiftAxis_deeplyNested_matchesInSubtree() {
        val target = makeGroup("axis-right")
        val mid = SvgGElement().apply { children().add(target) }
        val root = SvgGElement().apply { children().add(mid) }

        PlotSvgRoot.shiftAxis(root, "axis-right", 25.0)
        assertEquals("translate(25.0, 0)", transformOf(target))
    }

    @Test
    fun testShiftAxis_stopsAtFirstMatch_doesNotDescendFurther() {
        // If root itself matches, it should not also shift children that match
        val child = makeGroup("axis-left")
        val root = makeGroup("axis-left").apply { children().add(child) }

        PlotSvgRoot.shiftAxis(root, "axis-left", 5.0)

        // Root matches and gets shifted; traversal returns after first match
        assertEquals("translate(5.0, 0)", transformOf(root))
        // Child should NOT be shifted (traversal returned before descending further)
        assertEquals(null, transformOf(child))
    }

    // -----------------------------------------------------------------------
    // shiftParentOfClass tests
    // -----------------------------------------------------------------------

    @Test
    fun testShiftParentOfClass_found_shiftsParentNotChild() {
        val child = makeGroup("axis-title-y")
        val parent = SvgGElement().apply { children().add(child) }
        val root = SvgGElement().apply { children().add(parent) }

        val found = PlotSvgRoot.shiftParentOfClass(root, "axis-title-y", 40.0)

        assertTrue(found, "Should return true when class is found")
        // Parent should be shifted
        assertEquals("translate(40.0, 0)", transformOf(parent),
            "Parent of the matched element should receive the transform")
        // Child itself should NOT be shifted
        assertEquals(null, transformOf(child),
            "Matched child element should not be shifted directly")
    }

    @Test
    fun testShiftParentOfClass_notFound_returnsFalse() {
        val root = makeGroup("some-class")
        val found = PlotSvgRoot.shiftParentOfClass(root, "axis-title-y", 10.0)
        assertFalse(found, "Should return false when class is not in the tree")
    }

    @Test
    fun testShiftParentOfClass_parentHasExistingTransform_translatePrepended() {
        val child = makeGroup("axis-title-y")
        val parent = SvgGElement().apply {
            setAttribute("transform", "rotate(-90, 100, 100)")
            children().add(child)
        }
        val root = SvgGElement().apply { children().add(parent) }

        PlotSvgRoot.shiftParentOfClass(root, "axis-title-y", 30.0)

        assertEquals("translate(30.0, 0) rotate(-90, 100, 100)", transformOf(parent))
    }

    @Test
    fun testShiftParentOfClass_onlyFirstOccurrenceIsShifted() {
        // Two elements with same class in different branches: only the first found is shifted
        val child1 = makeGroup("axis-title-y")
        val parent1 = SvgGElement().apply { children().add(child1) }
        val child2 = makeGroup("axis-title-y")
        val parent2 = SvgGElement().apply { children().add(child2) }
        val root = SvgGElement().apply {
            children().add(parent1)
            children().add(parent2)
        }

        val found = PlotSvgRoot.shiftParentOfClass(root, "axis-title-y", 20.0)

        assertTrue(found)
        assertEquals("translate(20.0, 0)", transformOf(parent1), "First parent should be shifted")
        assertEquals(null, transformOf(parent2), "Second parent should NOT be shifted")
    }
}
