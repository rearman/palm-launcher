package com.rearman.palmlauncher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
	lateinit var state: LauncherState
	var apps: List<AppEntry> = emptyList()
	var receiver: BroadcastReceiver? = null
	var hasFocus = false

	private val match = ViewGroup.LayoutParams.MATCH_PARENT
	private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

	fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		state = Store.load(this)
		reload()
		receiver = Apps.watch(this) { reload(); render() }
		render()
	}

	override fun onDestroy() {
		super.onDestroy()
		receiver?.let { unregisterReceiver(it) }
	}

	override fun onWindowFocusChanged(focus: Boolean) {
		super.onWindowFocusChanged(focus)
		hasFocus = focus
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		// Home pressed while the launcher already shows: cycle the Palm category.
		if (intent.action == Intent.ACTION_MAIN && hasFocus &&
			(intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0) {
			state.selected = state.nextCategory(apps.map { it.component })
			save()
			render()
		}
	}

	// Back does nothing on the home screen.
	@Deprecated("Deprecated in Java")
	@Suppress("DEPRECATION")
	override fun onBackPressed() {
	}

	fun reload() {
		apps = Apps.load(this)
		if (apps.isNotEmpty()) {
			state.prune(apps.map { it.component }.toSet())
			save()
		}
	}

	fun save() = Store.save(this, state)

	fun render() {
		val root = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setBackgroundColor(Color.WHITE)
		}
		root.addView(topBar(), lp(match, wrap))
		root.addView(rule(), lp(match, dp(1)))
		root.addView(appArea(), lp(match, 0, 1f))
		root.addView(rule(), lp(match, dp(1)))
		root.addView(dock(), lp(match, wrap))
		setContentView(root)
	}

	private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

	private fun rule() = View(this).apply { setBackgroundColor(Color.BLACK) }

	private fun topBar(): LinearLayout {
		val menu = TextView(this).apply {
			text = "Menu"
			textSize = 16f
			typeface = Typeface.DEFAULT_BOLD
			setOnClickListener { viewMenu(it) }
		}
		val category = TextView(this).apply {
			text = state.categoryName(state.selected) + " ▾"
			textSize = 16f
			setOnClickListener { categoryMenu(it) }
		}
		return LinearLayout(this).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER_VERTICAL
			setPadding(dp(8), dp(8), dp(8), dp(8))
			addView(menu, lp(wrap, wrap))
			addView(View(this@MainActivity), lp(0, wrap, 1f))
			addView(category, lp(wrap, wrap))
		}
	}

	private fun viewMenu(anchor: View) {
		val label = if (state.viewMode == ViewMode.GRID) "View as List" else "View as Grid"
		PopupMenu(this, anchor).apply {
			menu.add(label)
			setOnMenuItemClickListener {
				state.viewMode = if (state.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
				save()
				render()
				true
			}
			show()
		}
	}

	private fun categoryMenu(anchor: View) {
		// Item ids are positions in ids; the last item is "Edit Categories...".
		val ids = listOf(ALL) + state.categories.map { it.id }
		PopupMenu(this, anchor).apply {
			ids.forEachIndexed { i, id -> menu.add(0, i, i, state.categoryName(id)) }
			menu.add(0, ids.size, ids.size, "Edit Categories...")
			setOnMenuItemClickListener { item ->
				if (item.itemId == ids.size) {
					editCategories(this@MainActivity, state) { save(); render() }
				} else {
					state.selected = ids[item.itemId]
					save()
					render()
				}
				true
			}
			show()
		}
	}

	private fun appArea(): ScrollView {
		val grid = state.viewMode == ViewMode.GRID
		val cols = if (grid) 4 else 2
		val visible = state.filter(apps, { it.component }, { it.label })
		val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
		visible.chunked(cols).forEach { chunk ->
			val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
			chunk.forEach { row.addView(cell(it), lp(0, wrap, 1f)) }
			// Pad a short last row so its cells keep the same width.
			repeat(cols - chunk.size) { row.addView(View(this), lp(0, wrap, 1f)) }
			list.addView(row, lp(match, wrap))
		}
		return ScrollView(this).apply { addView(list) }
	}

	private fun cell(e: AppEntry): View {
		val grid = state.viewMode == ViewMode.GRID
		val cell = LinearLayout(this)
		cell.orientation = if (grid) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
		cell.gravity = if (grid) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
		cell.setPadding(dp(6), dp(6), dp(6), dp(6))
		val iconSize = if (grid) dp(48) else dp(32)
		val icon = ImageView(this).apply { setImageDrawable(e.icon) }
		cell.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
		val label = TextView(this).apply {
			text = e.label
			if (grid) {
				textSize = 12f
				gravity = Gravity.CENTER_HORIZONTAL
				maxLines = 2
			} else {
				textSize = 15f
				setSingleLine(true)
			}
			ellipsize = TextUtils.TruncateAt.END
		}
		val labelParams = if (grid) {
			LinearLayout.LayoutParams(match, wrap)
		} else {
			LinearLayout.LayoutParams(0, wrap, 1f).apply { marginStart = dp(8) }
		}
		cell.addView(label, labelParams)
		cell.setOnClickListener { Apps.launch(this, e.component) }
		cell.setOnLongClickListener { v -> appMenu(v, e); true }
		return cell
	}

	private fun appMenu(anchor: View, e: AppEntry) {
		val labels = listOf("Category...", "Dock 1", "Dock 2", "Dock 3", "Dock 4", "Info", "Delete")
		PopupMenu(this, anchor).apply {
			labels.forEachIndexed { i, s -> menu.add(0, i, i, s) }
			setOnMenuItemClickListener { item ->
				when (item.itemId) {
					0 -> pickCategory(this@MainActivity, state, state.categoryOf(e.component)) { id ->
						state.assign(e.component, id)
						save()
						render()
					}
					1, 2, 3, 4 -> {
						state.dock[item.itemId - 1] = e.component
						save()
						render()
					}
					5 -> startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
						Uri.fromParts("package", e.packageName, null)))
					6 -> startSafely(Intent(Intent.ACTION_DELETE,
						Uri.fromParts("package", e.packageName, null)))
				}
				true
			}
			show()
		}
	}

	private fun startSafely(intent: Intent) {
		try {
			startActivity(intent)
		} catch (ex: ActivityNotFoundException) {
			// No activity on this device handles the intent.
		}
	}

	private fun dock(): LinearLayout {
		val bar = LinearLayout(this).apply {
			orientation = LinearLayout.HORIZONTAL
			setPadding(dp(8), dp(8), dp(8), dp(8))
			setBackgroundColor(0xFFEEEEEE.toInt())
		}
		for (i in state.dock.indices) {
			val app = apps.find { it.component == state.dock[i] }
			val slot = FrameLayout(this)
			if (app != null) {
				val icon = ImageView(this).apply { setImageDrawable(app.icon) }
				slot.addView(icon, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
				slot.setOnClickListener { Apps.launch(this, app.component) }
				slot.setOnLongClickListener { v -> dockMenu(v, i); true }
			}
			bar.addView(slot, LinearLayout.LayoutParams(0, dp(64), 1f))
		}
		return bar
	}

	private fun dockMenu(anchor: View, i: Int) {
		PopupMenu(this, anchor).apply {
			menu.add("Clear")
			setOnMenuItemClickListener {
				state.dock[i] = null
				save()
				render()
				true
			}
			show()
		}
	}
}
