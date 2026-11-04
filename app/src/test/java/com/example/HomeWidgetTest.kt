package com.example

import com.example.ui.components.HomeWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Home page lets the athlete drag its cards into any order.
 *
 * The saved order is a list of strings in preferences, so it can outlive a release: it may contain
 * a widget this build no longer has, miss one it just gained, or be empty on a fresh install. The
 * merge is what keeps the page complete and stable through all three.
 */
class HomeWidgetTest {

    @Test
    fun `an empty saved order is the default order`() {
        assertEquals(HomeWidget.entries.toList(), HomeWidget.merge(emptyList()))
    }

    @Test
    fun `a saved order is preserved`() {
        val saved = listOf("quote", "recent", "today", "weight", "stats", "heatmap", "actions", "week")
        assertEquals(
            listOf(
                HomeWidget.QUOTE,
                HomeWidget.RECENT,
                HomeWidget.TODAY,
                HomeWidget.WEIGHT,
                HomeWidget.STREAK,
                HomeWidget.STATS,
                HomeWidget.HEATMAP,
                HomeWidget.ACTIONS,
                HomeWidget.WEEK,
                HomeWidget.PHOTO,
                HomeWidget.GOAL
            ),
            HomeWidget.merge(saved)
        )
    }

    @Test
    fun `ids the build no longer knows are dropped`() {
        val merged = HomeWidget.merge(listOf("removed_widget", "recent", "today"))
        assertTrue(merged.none { it.id == "removed_widget" })
        assertEquals(
            "the page still contains every current widget exactly once",
            HomeWidget.entries.toSet(),
            merged.toSet()
        )
    }

    @Test
    fun `duplicate ids do not duplicate a widget`() {
        val merged = HomeWidget.merge(listOf("recent", "recent", "today", "today"))
        assertEquals(merged.size, merged.distinct().size)
        assertEquals(HomeWidget.entries.toSet(), merged.toSet())
    }

    @Test
    fun `a widget added in a new release appears before its default successor`() {
        // An athlete saved an order before HEATMAP existed. It belongs between STATS and WEIGHT in
        // the default order, and that is where it should appear instead of at the bottom.
        val saved = listOf("today", "actions", "week", "stats", "weight", "recent", "quote")
        val merged = HomeWidget.merge(saved)
        assertEquals(
            HomeWidget.STATS,
            merged[merged.indexOf(HomeWidget.HEATMAP) - 1]
        )
        assertEquals(
            HomeWidget.WEIGHT,
            merged[merged.indexOf(HomeWidget.HEATMAP) + 1]
        )
    }

    @Test
    fun `every widget id is unique and stable`() {
        assertEquals(HomeWidget.entries.size, HomeWidget.entries.map { it.id }.distinct().size)
        assertEquals(HomeWidget.entries.size, HomeWidget.entries.map { it.label }.distinct().size)
    }

    // === Optional widgets and the manager ===

    @Test
    fun `a fresh install shows the stock widgets and leaves the optional ones off`() {
        assertEquals(
            listOf(
                HomeWidget.TODAY,
                HomeWidget.ACTIONS,
                HomeWidget.WEEK,
                HomeWidget.STREAK,
                HomeWidget.STATS,
                HomeWidget.HEATMAP,
                HomeWidget.WEIGHT,
                HomeWidget.RECENT,
                HomeWidget.QUOTE
            ),
            HomeWidget.defaultVisible()
        )
        assertEquals(
            setOf(HomeWidget.PHOTO.id, HomeWidget.GOAL.id),
            HomeWidget.defaultHiddenIds
        )
    }

    @Test
    fun `an unconfigured page resolves to the default widgets`() {
        assertEquals(HomeWidget.defaultVisible(), HomeWidget.resolve(emptyList(), null))
        assertEquals(HomeWidget.defaultVisible(), HomeWidget.resolve(emptyList(), HomeWidget.defaultHiddenIds))
    }

    @Test
    fun `an empty hidden set means the athlete enabled everything`() {
        assertEquals(HomeWidget.entries.toList(), HomeWidget.resolve(emptyList(), emptySet()))
    }

    @Test
    fun `removed widgets are filtered from the resolved page`() {
        val hidden = setOf(HomeWidget.WEEK.id, HomeWidget.RECENT.id)
        assertEquals(
            "an explicit hidden set shows every widget it does not name, including the optional ones",
            HomeWidget.entries.filterNot { it.id in hidden },
            HomeWidget.resolve(emptyList(), hidden)
        )
    }

    @Test
    fun `a removed widget keeps its saved place when it is added back`() {
        // The athlete removed the stats card and moved the quote card to the front. Adding stats
        // back must not append it to the bottom; the default successor still decides its slot.
        val visible = listOf(
            HomeWidget.QUOTE,
            HomeWidget.TODAY,
            HomeWidget.ACTIONS,
            HomeWidget.WEEK,
            HomeWidget.HEATMAP,
            HomeWidget.WEIGHT,
            HomeWidget.RECENT
        )
        val added = HomeWidget.add(visible, HomeWidget.STATS)
        assertEquals(
            "stats belongs before its default successor, the heat map",
            HomeWidget.HEATMAP,
            added[added.indexOf(HomeWidget.STATS) + 1]
        )
        assertEquals(visible.size + 1, added.size)
    }

    @Test
    fun `adding a widget that is already visible is a no-op`() {
        val visible = HomeWidget.defaultVisible()
        assertEquals(visible, HomeWidget.add(visible, HomeWidget.TODAY))
        assertEquals(visible, HomeWidget.add(visible, HomeWidget.QUOTE))
    }

    @Test
    fun `the optional widgets are added at the end of the stock page`() {
        val visible = HomeWidget.defaultVisible()
        assertEquals(
            visible + HomeWidget.PHOTO,
            HomeWidget.add(visible, HomeWidget.PHOTO)
        )
        assertEquals(
            visible + HomeWidget.GOAL,
            HomeWidget.add(visible, HomeWidget.GOAL)
        )
    }

    @Test
    fun `removing a widget only hides it and never touches its data`() {
        val visible = HomeWidget.defaultVisible()
        val removed = HomeWidget.remove(visible, HomeWidget.WEIGHT)
        assertEquals(visible.size - 1, removed.size)
        assertTrue(HomeWidget.WEIGHT !in removed)
        assertEquals(
            "every other widget keeps its exact position",
            visible.filterNot { it == HomeWidget.WEIGHT },
            removed
        )
    }

    @Test
    fun `removing a widget that is not visible is a no-op`() {
        val visible = HomeWidget.defaultVisible()
        assertEquals(visible, HomeWidget.remove(visible, HomeWidget.PHOTO))
    }

    @Test
    fun `moving a widget up or down swaps it with its neighbour`() {
        val visible = HomeWidget.defaultVisible()
        assertEquals(
            listOf(
                HomeWidget.ACTIONS,
                HomeWidget.TODAY,
                HomeWidget.WEEK
            ),
            HomeWidget.move(visible, HomeWidget.TODAY, 1).take(3)
        )
        assertEquals(
            listOf(
                HomeWidget.TODAY,
                HomeWidget.WEEK,
                HomeWidget.ACTIONS
            ),
            HomeWidget.move(visible, HomeWidget.ACTIONS, 1).take(3)
        )
    }

    @Test
    fun `moving past either end is clamped`() {
        val visible = HomeWidget.defaultVisible()
        assertEquals(visible, HomeWidget.move(visible, HomeWidget.TODAY, -1))
        assertEquals(visible, HomeWidget.move(visible, HomeWidget.QUOTE, 1))
    }

    @Test
    fun `moving a widget that is not visible is a no-op`() {
        val visible = HomeWidget.defaultVisible()
        assertEquals(visible, HomeWidget.move(visible, HomeWidget.PHOTO, 1))
    }

    @Test
    fun `available lists every widget that is not on Home`() {
        val visible = HomeWidget.defaultVisible() - HomeWidget.RECENT
        val available = HomeWidget.available(visible)
        assertEquals(
            listOf(HomeWidget.RECENT, HomeWidget.PHOTO, HomeWidget.GOAL),
            available
        )
        assertTrue(HomeWidget.RECENT !in HomeWidget.available(HomeWidget.entries.toList()))
    }

    // === The insertion preview ===

    private val full = HomeWidget.defaultVisible()

    @Test
    fun `moving the first widget to the end puts it after the last card`() {
        val moved = HomeWidget.moveToInsertion(
            full = full,
            visible = full,
            draggedId = HomeWidget.TODAY.id,
            insertionIndex = full.size - 1
        )
        assertEquals(HomeWidget.TODAY, moved.last())
    }

    @Test
    fun `moving the last widget to the front puts it before the first card`() {
        val moved = HomeWidget.moveToInsertion(
            full = full,
            visible = full,
            draggedId = HomeWidget.QUOTE.id,
            insertionIndex = 0
        )
        assertEquals(HomeWidget.QUOTE, moved.first())
    }

    @Test
    fun `moving a widget down one slot lands before the next visible card`() {
        val moved = HomeWidget.moveToInsertion(
            full = full,
            visible = full,
            draggedId = HomeWidget.TODAY.id,
            insertionIndex = 1
        )
        assertEquals(
            listOf(
                HomeWidget.ACTIONS,
                HomeWidget.TODAY,
                HomeWidget.WEEK,
                HomeWidget.STREAK,
                HomeWidget.STATS,
                HomeWidget.HEATMAP,
                HomeWidget.WEIGHT,
                HomeWidget.RECENT,
                HomeWidget.QUOTE
            ),
            moved
        )
    }

    @Test
    fun `an insertion index in the dragged widget's own slot is a no-op`() {
        assertEquals(
            full,
            HomeWidget.moveToInsertion(
                full = full,
                visible = full,
                draggedId = HomeWidget.STATS.id,
                insertionIndex = full.indexOf(HomeWidget.STATS)
            )
        )
    }

    @Test
    fun `an insertion index past the end is clamped`() {
        val moved = HomeWidget.moveToInsertion(
            full = full,
            visible = full,
            draggedId = HomeWidget.TODAY.id,
            insertionIndex = 999
        )
        assertEquals(HomeWidget.TODAY, moved.last())
    }

    @Test
    fun `an unknown dragged widget leaves the order untouched`() {
        assertEquals(
            full,
            HomeWidget.moveToInsertion(
                full = full,
                visible = full,
                draggedId = "removed_widget",
                insertionIndex = 2
            )
        )
    }

    @Test
    fun `a hidden widget keeps its place relative to the visible cards`() {
        // No routine is scheduled, so the week card is not on screen. Dragging the last visible
        // card to the front must move it in front of the first visible card while the hidden week
        // card stays between the quick actions and the training numbers.
        val visible = full.filter { it != HomeWidget.WEEK }
        val moved = HomeWidget.moveToInsertion(
            full = full,
            visible = visible,
            draggedId = HomeWidget.QUOTE.id,
            insertionIndex = 0
        )
        assertEquals(HomeWidget.QUOTE, moved.first())
        assertEquals(
            HomeWidget.WEEK,
            moved[moved.indexOf(HomeWidget.ACTIONS) + 1]
        )
    }

    @Test
    fun `the preview moves the dragged widget into the gap without losing one`() {
        assertEquals(
            listOf(
                HomeWidget.ACTIONS,
                HomeWidget.TODAY,
                HomeWidget.WEEK,
                HomeWidget.STREAK,
                HomeWidget.STATS,
                HomeWidget.HEATMAP,
                HomeWidget.WEIGHT,
                HomeWidget.RECENT,
                HomeWidget.QUOTE
            ),
            HomeWidget.previewOrder(full, HomeWidget.TODAY.id, 1)
        )
    }

    @Test
    fun `the preview is the saved order when the card is in its own slot`() {
        val index = full.indexOf(HomeWidget.STATS)
        assertEquals(full, HomeWidget.previewOrder(full, HomeWidget.STATS.id, index))
    }

    @Test
    fun `the preview clamps an index past the end`() {
        assertEquals(
            HomeWidget.TODAY,
            HomeWidget.previewOrder(full, HomeWidget.TODAY.id, 999).last()
        )
        assertEquals(
            HomeWidget.QUOTE,
            HomeWidget.previewOrder(full, HomeWidget.QUOTE.id, -1).first()
        )
    }

    @Test
    fun `the preview keeps every widget exactly once`() {
        for (widget in full) {
            for (index in 0..full.size) {
                val preview = HomeWidget.previewOrder(full, widget.id, index)
                assertEquals(full.size, preview.size)
                assertEquals(full.toSet(), preview.toSet())
            }
        }
    }

    @Test
    fun `dragging to the bottom keeps a hidden widget in place`() {
        val visible = full.filter { it != HomeWidget.WEEK }
        val moved = HomeWidget.moveToInsertion(
            full = full,
            visible = visible,
            draggedId = HomeWidget.TODAY.id,
            insertionIndex = visible.size - 1
        )
        assertEquals(HomeWidget.TODAY, moved.last())
        assertEquals(
            listOf(
                HomeWidget.ACTIONS,
                HomeWidget.WEEK,
                HomeWidget.STREAK,
                HomeWidget.STATS,
                HomeWidget.HEATMAP,
                HomeWidget.WEIGHT,
                HomeWidget.RECENT,
                HomeWidget.QUOTE,
                HomeWidget.TODAY
            ),
            moved
        )
    }
}
