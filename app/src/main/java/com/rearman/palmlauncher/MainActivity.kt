package com.rearman.palmlauncher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
	lateinit var state: LauncherState
	var apps: List<AppEntry> = emptyList()
	var receiver: BroadcastReceiver? = null
	var hasFocus = false
	private var scroll: ScrollView? = null

	// Damien Guard's Palm OS font, the same files as in Datebook (assets/fonts).
	private val palmRegular by lazy { Typeface.createFromAsset(assets, "fonts/palm-os.ttf") }
	private val palmBold by lazy { Typeface.createFromAsset(assets, "fonts/palm-os-bold.ttf") }

	private val match = ViewGroup.LayoutParams.MATCH_PARENT
	private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

	fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

	// The green is the Green LCD paper color of the Datebook app.
	internal fun paper() = if (state.green) 0xFFC9D3A8.toInt() else Color.WHITE
	private fun dockColor() = if (state.green) 0xFFB5BF94.toInt() else 0xFFEEEEEE.toInt()

	// Text Size multiplies the system font scale, so every sp size in the launcher follows it.
	// A change of Text Size recreates the activity.
	override fun attachBaseContext(base: Context) {
		super.attachBaseContext(base)
		applyOverrideConfiguration(Configuration().apply {
			fontScale = base.resources.configuration.fontScale * Store.load(base).textSize.scale
		})
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		state = Store.load(this)
		reload()
		receiver = Apps.watch(this) { reload(); render(keepScroll = true) }
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

	// keepScroll: stay at the same place in the app list (an edit of one app); else go to the top.
	fun render(keepScroll: Boolean = false) {
		val y = if (keepScroll) scroll?.scrollY ?: 0 else 0
		val root = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setBackgroundColor(paper())
		}
		val area = appArea()
		scroll = area
		root.addView(topBar(), lp(match, wrap))
		root.addView(rule(), lp(match, dp(1)))
		root.addView(area, lp(match, 0, 1f))
		root.addView(rule(), lp(match, dp(1)))
		root.addView(dock(), lp(match, wrap))
		setContentView(root)
		window.decorView.setBackgroundColor(paper())
		// The new list has no height until layout, so scroll after it.
		if (y > 0) area.post { area.scrollTo(0, y) }
	}

	private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

	private fun rule() = View(this).apply { setBackgroundColor(Color.BLACK) }

	// Sets the size and face of launcher text; call it after the text is set. The system font is `sp`
	// in size. Palm Font uses the Datebook sizes for all text: Text Size gives 13, 16 or 20 Palm px,
	// where the shorter screen side is 160 Palm px (Palm IIIxe).
	internal fun TextView.font(sp: Float, bold: Boolean) {
		if (!state.palmFont) {
			textSize = sp
			typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
			return
		}
		val dm = resources.displayMetrics
		setTextSize(TypedValue.COMPLEX_UNIT_PX, state.textSize.palmPx * minOf(dm.widthPixels, dm.heightPixels) / 160f)
		typeface = if (bold) palmBold else palmRegular
		includeFontPadding = false
		// The Palm OS font has no ▾ or ✓ glyph, so Android draws them from the system font, which draws
		// them larger. As in Datebook, this shrinks each one to 0.6 of the text size.
		if (text.none { it == '▾' || it == '✓' }) return
		val s = SpannableString(text)
		text.forEachIndexed { i, ch ->
			if (ch == '▾' || ch == '✓') s.setSpan(RelativeSizeSpan(0.6f), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		}
		text = s
	}

	private fun topBar(): LinearLayout {
		val menu = TextView(this).apply {
			text = "Menu"
			font(16f, bold = true)
			setOnClickListener { viewMenu(it) }
		}
		val category = TextView(this).apply {
			text = "▾ " + state.categoryName(state.selected)
			font(16f, bold = false)
			setOnClickListener { categoryMenu(it) }
		}
		return LinearLayout(this).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER_VERTICAL
			setPadding(dp(8), dp(8), dp(8), dp(8))
			addView(menu, lp(wrap, wrap))
			// A plain View with WRAP_CONTENT takes all the height it is offered, so the spacer gets 0.
			addView(View(this@MainActivity), lp(0, 0, 1f))
			addView(category, lp(wrap, wrap))
		}
	}

	private fun viewMenu(anchor: View) {
		val items = listOf(
			if (state.viewMode == ViewMode.GRID) "View as List" else "View as Grid",
			"Green LCD", "Palm Font", "Text Size...", "Icon Size...",
		)
		val checked = setOfNotNull(1.takeIf { state.green }, 2.takeIf { state.palmFont })
		popupList(anchor, items, checked) { i ->
			when (i) {
				0 -> {
					state.viewMode = if (state.viewMode == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
					save()
					render()
				}
				1 -> {
					state.green = !state.green
					save()
					render()
				}
				2 -> {
					state.palmFont = !state.palmFont
					save()
					render(keepScroll = true)
				}
				3 -> pickSize(this, "Text Size", state.textSize) { s ->
					state.textSize = s
					save()
					// attachBaseContext reads the new size.
					recreate()
				}
				4 -> pickSize(this, "Icon Size", state.iconSize) { s ->
					state.iconSize = s
					save()
					render(keepScroll = true)
				}
			}
		}
	}

	private fun categoryMenu(anchor: View) {
		// Item indexes are positions in ids; the last item is "Edit Categories...".
		val ids = listOf(ALL) + state.categories.map { it.id }
		val items = ids.map { state.categoryName(it) } + "Edit Categories..."
		popupList(anchor, items, setOf(ids.indexOf(state.selected))) { i ->
			if (i == ids.size) {
				editCategories(this, state) { save(); render() }
			} else {
				state.selected = ids[i]
				save()
				render()
			}
		}
	}

	private fun appArea(): ScrollView {
		val grid = state.viewMode == ViewMode.GRID
		val cols = if (grid) 4 else 2
		val cells = state.filter(apps, { it.component }, { it.label }).map { cell(it) }
		// List view: the two columns together are centered on the screen (null widths: equal columns).
		// Limit: it measures against the full screen width, so in split-screen mode the columns can be cut off.
		val widths = if (grid) null else {
			val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
			columnWidths(cells.map { it.measure(spec, spec); it.measuredWidth }, cols, resources.displayMetrics.widthPixels)
		}
		val list = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			gravity = Gravity.CENTER_HORIZONTAL
		}
		cells.chunked(cols).forEach { chunk ->
			val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
			if (widths != null) {
				chunk.forEachIndexed { c, v -> row.addView(v, lp(widths[c], wrap)) }
				// The fixed row width keeps a short last row at the left of the block.
				list.addView(row, lp(widths.sum(), wrap))
			} else {
				chunk.forEach { row.addView(it, lp(0, wrap, 1f)) }
				// Pad a short last row so its cells keep the same width.
				repeat(cols - chunk.size) { row.addView(View(this), lp(0, 0, 1f)) }
				list.addView(row, lp(match, wrap))
			}
		}
		return ScrollView(this).apply { addView(list) }
	}

	private fun cell(e: AppEntry): View {
		val grid = state.viewMode == ViewMode.GRID
		val cell = LinearLayout(this)
		cell.orientation = if (grid) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
		cell.gravity = if (grid) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
		val side = if (grid) dp(6) else dp(8)
		cell.setPadding(side, dp(6), side, dp(6))
		val iconSize = (dp(if (grid) 48 else 32) * state.iconSize.scale).toInt()
		val icon = ImageView(this).apply { setImageDrawable(e.icon) }
		cell.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
		val label = TextView(this).apply {
			text = e.label
			if (grid) {
				font(12f, bold = false)
				gravity = Gravity.CENTER_HORIZONTAL
				maxLines = 2
			} else {
				font(15f, bold = false)
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
		popupList(anchor, labels) { i ->
			when (i) {
				0 -> pickCategory(this, state, state.categoryOf(e.component)) { id ->
					state.assign(e.component, id)
					save()
					render(keepScroll = true)
				}
				1, 2, 3, 4 -> {
					state.dock[i - 1] = e.component
					save()
					render(keepScroll = true)
				}
				5 -> startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
					Uri.fromParts("package", e.packageName, null)))
				6 -> startSafely(Intent(Intent.ACTION_DELETE,
					Uri.fromParts("package", e.packageName, null)))
			}
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
			setBackgroundColor(dockColor())
		}
		val iconSize = (dp(48) * state.iconSize.scale).toInt()
		for (i in state.dock.indices) {
			val app = apps.find { it.component == state.dock[i] }
			val slot = FrameLayout(this)
			if (app != null) {
				val icon = ImageView(this).apply { setImageDrawable(app.icon) }
				slot.addView(icon, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
				slot.setOnClickListener { Apps.launch(this, app.component) }
				slot.setOnLongClickListener { v -> dockMenu(v, i); true }
			}
			// 8 dp above and below the icon, as at Normal size (48 dp icon in a 64 dp slot).
			bar.addView(slot, LinearLayout.LayoutParams(0, iconSize + dp(16), 1f))
		}
		return bar
	}

	private fun dockMenu(anchor: View, i: Int) {
		popupList(anchor, listOf("Clear")) {
			state.dock[i] = null
			save()
			render(keepScroll = true)
		}
	}
}
