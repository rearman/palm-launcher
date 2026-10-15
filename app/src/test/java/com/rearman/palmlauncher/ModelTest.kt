package com.rearman.palmlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

data class App(val c: String, val l: String)

class ModelTest {

	private fun labels(apps: List<App>): List<String> = apps.map { it.l }

	private fun filterApps(s: LauncherState, apps: List<App>): List<App> =
		s.filter(apps, { it.c }, { it.l })

	@Test
	fun defaultsHaveFiveCategoriesWithUnfiledLast() {
		val s = LauncherState.defaults()
		assertEquals(5, s.categories.size)
		assertEquals(listOf(1, 2, 3, 4, 0), s.categories.map { it.id })
		assertEquals(listOf("Games", "Main", "System", "Utilities", "Unfiled"), s.categories.map { it.name })
		assertEquals(ALL, s.selected)
		assertEquals(ViewMode.GRID, s.viewMode)
		assertEquals(4, s.dock.size)
		assertTrue(s.dock.all { it == null })
		assertTrue(s.assignments.isEmpty())
	}

	@Test
	fun filterAllSortsByLabelCaseInsensitive() {
		val s = LauncherState.defaults()
		val apps = listOf(App("a/.A", "banana"), App("b/.B", "Apple"), App("c/.C", "cherry"))
		assertEquals(listOf("Apple", "banana", "cherry"), labels(filterApps(s, apps)))
	}

	@Test
	fun filterCategoryReturnsOnlyItsApps() {
		val s = LauncherState.defaults()
		val apps = listOf(App("a/.A", "banana"), App("b/.B", "Apple"), App("c/.C", "cherry"))
		s.assign("a/.A", 1)
		s.assign("c/.C", 2)

		s.selected = 2
		assertEquals(listOf("cherry"), labels(filterApps(s, apps)))

		s.selected = 1
		assertEquals(listOf("banana"), labels(filterApps(s, apps)))
	}

	@Test
	fun unassignedAppsAreInUnfiled() {
		val s = LauncherState.defaults()
		val apps = listOf(App("a/.A", "banana"), App("b/.B", "Apple"), App("c/.C", "cherry"))
		s.assign("a/.A", 1)
		s.assign("c/.C", 2)

		assertEquals(UNFILED, s.categoryOf("b/.B"))
		s.selected = UNFILED
		assertEquals(listOf("Apple"), labels(filterApps(s, apps)))
	}

	@Test
	fun categoryOfUnknownIdGivesUnfiled() {
		val s = LauncherState.defaults()
		s.assign("x/.X", 99)
		assertEquals(UNFILED, s.categoryOf("x/.X"))
	}

	@Test
	fun categoryOfDeletedIdGivesUnfiled() {
		val s = LauncherState.defaults()
		s.assign("a/.A", 1)
		s.deleteCategory(1)
		assertEquals(UNFILED, s.categoryOf("a/.A"))
	}

	@Test
	fun nextCategoryFromAllGoesToFirstCategoryWithApp() {
		val s = LauncherState.defaults()
		s.assign("a/.A", 2)
		s.assign("b/.B", 4)
		assertEquals(2, s.nextCategory(listOf("a/.A", "b/.B")))
	}

	@Test
	fun nextCategorySkipsEmptyCategories() {
		val s = LauncherState.defaults()
		s.assign("a/.A", 4)
		assertEquals(4, s.nextCategory(listOf("a/.A")))

		s.selected = 2
		assertEquals(4, s.nextCategory(listOf("a/.A")))
	}

	@Test
	fun nextCategoryWrapsToAllAfterLast() {
		val s = LauncherState.defaults()
		s.assign("a/.A", 2)
		s.selected = UNFILED
		assertEquals(ALL, s.nextCategory(listOf("a/.A")))
	}

	@Test
	fun nextCategoryWithNoComponentsReturnsAll() {
		val s = LauncherState.defaults()
		assertEquals(ALL, s.nextCategory(emptyList()))
	}

	@Test
	fun addCategoryUsesMaxIdPlusOneAndSorts() {
		val s = LauncherState.defaults()
		val c = s.addCategory("apps")
		assertEquals(5, c.id)
		assertEquals(listOf("apps", "Games", "Main", "System", "Utilities", "Unfiled"), s.categories.map { it.name })

		s.addCategory("Zebra")
		assertEquals(listOf("apps", "Games", "Main", "System", "Utilities", "Zebra", "Unfiled"), s.categories.map { it.name })
		assertEquals(UNFILED, s.categories.last().id)
	}

	@Test
	fun renameCategoryResorts() {
		val s = LauncherState.defaults()
		s.renameCategory(1, "Xylo")
		assertEquals(listOf("Main", "System", "Utilities", "Xylo", "Unfiled"), s.categories.map { it.name })
	}

	@Test
	fun constructorSortsCategories() {
		val s = LauncherState(
			mutableListOf(Category(UNFILED, "Unfiled"), Category(7, "beta"), Category(8, "Alpha")),
			mutableMapOf(),
			arrayOfNulls(4),
			ALL,
			ViewMode.GRID
		)
		assertEquals(listOf("Alpha", "beta", "Unfiled"), s.categories.map { it.name })
	}

	@Test
	fun greenDefaultsToFalse() {
		assertFalse(LauncherState.defaults().green)
	}

	@Test
	fun renameCategoryChangesNameExceptUnfiled() {
		val s = LauncherState.defaults()
		s.renameCategory(1, "Gaming")
		assertEquals("Gaming", s.categoryName(1))

		s.renameCategory(UNFILED, "Other")
		assertEquals("Unfiled", s.categoryName(UNFILED))
		assertEquals("Unfiled", s.categories.last().name)
	}

	@Test
	fun deleteUnfiledIsNoOp() {
		val s = LauncherState.defaults()
		s.selected = UNFILED
		s.deleteCategory(UNFILED)
		assertEquals(5, s.categories.size)
		assertTrue(s.categories.any { it.id == UNFILED })
		assertEquals(UNFILED, s.selected)
	}

	@Test
	fun deleteCategoryMovesAppsToUnfiledAndResetsSelected() {
		val s = LauncherState.defaults()
		s.assign("a/.A", 1)
		s.selected = 1
		s.deleteCategory(1)

		assertFalse(s.categories.any { it.id == 1 })
		assertEquals(UNFILED, s.categoryOf("a/.A"))
		assertFalse(s.assignments.containsKey("a/.A"))
		assertEquals(ALL, s.selected)
	}

	@Test
	fun deleteCategoryKeepsSelectedWhenNotSelected() {
		val s = LauncherState.defaults()
		s.selected = 2
		s.deleteCategory(1)
		assertEquals(2, s.selected)
	}

	@Test
	fun pruneDropsUninstalledAssignmentsAndDockSlots() {
		val s = LauncherState.defaults()
		s.assign("a/.A", 1)
		s.assign("b/.B", 2)
		s.dock[0] = "a/.A"
		s.dock[1] = "b/.B"
		s.dock[2] = "c/.C"

		s.prune(setOf("a/.A"))

		assertEquals(1, s.assignments["a/.A"])
		assertFalse(s.assignments.containsKey("b/.B"))
		assertEquals("a/.A", s.dock[0])
		assertNull(s.dock[1])
		assertNull(s.dock[2])
		assertNull(s.dock[3])
	}
}
