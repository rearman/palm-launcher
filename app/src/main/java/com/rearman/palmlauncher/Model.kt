package com.rearman.palmlauncher

const val UNFILED = 0   // fixed category id, cannot be renamed or deleted, always last in categories
const val ALL = -1      // filter value: show all apps

enum class ViewMode { GRID, LIST }

data class Category(val id: Int, val name: String)

class LauncherState(
	val categories: MutableList<Category>,     // display order; Unfiled always last
	val assignments: MutableMap<String, Int>,  // component to category id; missing means Unfiled
	val dock: Array<String?>,                  // size 4, component or null
	var selected: Int,                         // ALL or a category id
	var viewMode: ViewMode,
) {
	var green = false  // Green LCD screen colors (saved by Store).

	companion object {
		// Games(1), Main(2), System(3), Utilities(4), Unfiled(0); empty assignments; dock of 4 nulls; ALL; GRID
		fun defaults(): LauncherState = LauncherState(
			mutableListOf(
				Category(1, "Games"),
				Category(2, "Main"),
				Category(3, "System"),
				Category(4, "Utilities"),
				Category(UNFILED, "Unfiled"),
			),
			mutableMapOf(),
			arrayOfNulls(4),
			ALL,
			ViewMode.GRID,
		)
	}

	fun categoryOf(component: String): Int {
		val id = assignments[component] ?: return UNFILED
		return if (categories.any { it.id == id }) id else UNFILED
	}

	fun assign(component: String, id: Int) {
		assignments[component] = id
	}

	fun categoryName(id: Int): String =
		if (id == ALL) "All" else categories.firstOrNull { it.id == id }?.name ?: "Unfiled"

	// Apps in the selected category (all apps when ALL), sorted by label, case-insensitive.
	fun <T> filter(apps: List<T>, key: (T) -> String, label: (T) -> String): List<T> =
		apps.filter { selected == ALL || categoryOf(key(it)) == selected }
			.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, label))

	// Palm Home-key cycle: order is ALL, then each category in list order. Return the next entry after
	// `selected` that has at least one of `components`; ALL always qualifies. Wraps to ALL.
	fun nextCategory(components: Collection<String>): Int {
		val order = listOf(ALL) + categories.map { it.id }
		val start = order.indexOf(selected)  // -1 when selected is not listed; the cycle then starts at ALL
		for (step in 1..order.size) {
			val id = order[(start + step) % order.size]
			if (id == ALL || components.any { categoryOf(it) == id }) return id
		}
		return ALL
	}

	fun addCategory(name: String): Category {
		val category = Category((categories.maxOfOrNull { it.id } ?: 0) + 1, name)
		val unfiled = categories.indexOfFirst { it.id == UNFILED }
		if (unfiled < 0) categories.add(category) else categories.add(unfiled, category)
		return category
	}

	fun renameCategory(id: Int, name: String) {
		if (id == UNFILED) return
		val index = categories.indexOfFirst { it.id == id }
		if (index >= 0) categories[index] = categories[index].copy(name = name)
	}

	fun deleteCategory(id: Int) {
		if (id == UNFILED) return
		categories.removeAll { it.id == id }
		assignments.entries.removeAll { it.value == id }
		if (selected == id) selected = ALL
	}

	fun prune(installed: Set<String>) {
		assignments.keys.removeAll { it !in installed }
		for (i in dock.indices) {
			val component = dock[i]
			if (component != null && component !in installed) dock[i] = null
		}
	}
}
