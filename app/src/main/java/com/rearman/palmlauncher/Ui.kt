package com.rearman.palmlauncher

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

// Palm-style pop-up lists and dialogs in the launcher colors (Green LCD) and font (Palm OS, Text Size).

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

// A rounded box: `fill` inside a 1 dp black line.
internal fun MainActivity.box(fill: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
	setColor(fill)
	setStroke(dp(1), Color.BLACK)
	cornerRadius = dp(radiusDp).toFloat()
}

// Black text in the launcher font.
internal fun MainActivity.text(s: CharSequence, bold: Boolean = false): TextView = TextView(this).apply {
	text = s
	setTextColor(Color.BLACK)
	font(bold)
}

// A Palm pop-up list at `anchor`. Items in `checked` get a check mark. A tap closes the list, then
// calls onPick(index).
internal fun MainActivity.popupList(anchor: View, items: List<String>, checked: Set<Int> = emptySet(), onPick: (Int) -> Unit) {
	val col = LinearLayout(this).apply {
		orientation = LinearLayout.VERTICAL
		background = box(paper(), 3)
		setPadding(dp(1), dp(1), dp(1), dp(1))
	}
	val sv = ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false }
	val pw = PopupWindow(sv, WRAP, WRAP, true)
	items.forEachIndexed { i, s ->
		col.addView(text(if (i in checked) "✓ $s" else s).apply {
			setPadding(dp(12), dp(8), dp(24), dp(8))
			setOnClickListener { pw.dismiss(); onPick(i) }
		}, LinearLayout.LayoutParams(MATCH, WRAP))
	}
	pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
	pw.elevation = dp(4).toFloat()
	// As in Datebook: at most 2/3 of the visible height, at the anchor, moved left or up only as far
	// as needed to stay on the screen.
	val vis = Rect().also { anchor.getWindowVisibleDisplayFrame(it) }
	val maxH = vis.height() * 2 / 3
	sv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
	if (sv.measuredHeight > maxH) pw.height = maxH
	val h = minOf(sv.measuredHeight, maxH)
	val loc = IntArray(2).also { anchor.getLocationOnScreen(it) }
	val x = loc[0].coerceIn(0, maxOf(0, resources.displayMetrics.widthPixels - sv.measuredWidth))
	val y = loc[1].coerceIn(vis.top, maxOf(vis.top, vis.bottom - h))
	pw.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
}

// A Palm form: black title bar, bordered body, buttons along the bottom. As in Datebook it is 156 of
// 160 Palm px wide, at the bottom of the screen, with no dimming behind it.
class PalmDialog(private val act: MainActivity, title: String) : Dialog(act) {
	private val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
	private val buttons = LinearLayout(act).apply {
		orientation = LinearLayout.HORIZONTAL
		setPadding(act.dp(8), act.dp(8), act.dp(8), act.dp(8))
	}

	init {
		requestWindowFeature(Window.FEATURE_NO_TITLE)
		val frame = LinearLayout(act).apply {
			orientation = LinearLayout.VERTICAL
			background = act.box(act.paper(), 4)
			setPadding(act.dp(2), act.dp(2), act.dp(2), act.dp(2))
		}
		val bar = act.text(title, bold = true).apply {
			gravity = Gravity.CENTER
			setTextColor(act.paper())
			background = act.box(Color.BLACK, 3)
			setPadding(act.dp(4), act.dp(2), act.dp(4), act.dp(2))
		}
		frame.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP))
		frame.addView(ScrollView(act).apply { addView(body); isVerticalScrollBarEnabled = false },
			LinearLayout.LayoutParams(MATCH, 0, 1f))
		frame.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))
		setContentView(frame)
		window?.let { w ->
			w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
			val dm = act.resources.displayMetrics
			w.setLayout(minOf(dm.widthPixels, dm.heightPixels) * 156 / 160, WindowManager.LayoutParams.WRAP_CONTENT)
			w.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
			w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
		}
	}

	// A message in the body.
	fun message(s: String) {
		body.addView(act.text(s).apply { setPadding(act.dp(8), act.dp(8), act.dp(8), act.dp(4)) },
			LinearLayout.LayoutParams(MATCH, WRAP))
	}

	// A list row in the body. The `selected` row is inverted, as in a Palm list.
	fun item(s: String, selected: Boolean = false, onClick: () -> Unit) {
		body.addView(act.text(s, bold = selected).apply {
			setPadding(act.dp(8), act.dp(8), act.dp(8), act.dp(8))
			if (selected) {
				setBackgroundColor(Color.BLACK)
				setTextColor(act.paper())
			}
			setOnClickListener { onClick() }
		}, LinearLayout.LayoutParams(MATCH, WRAP))
	}

	// A one-line text field in the body, with the cursor at the end. The keyboard opens with the form.
	fun field(s: String): EditText {
		val ed = EditText(act).apply {
			setText(s)
			setTextColor(Color.BLACK)
			setSingleLine(true)
			with(act) { font(bold = false) }
			setSelection(text.length)
		}
		body.addView(ed, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(act.dp(8), act.dp(8), act.dp(8), 0) })
		window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
			WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
		ed.requestFocus()
		return ed
	}

	// A command button in the Palm oval, in the bottom row. It is inverted while pressed.
	fun button(s: String, onClick: () -> Unit) {
		val b = act.text(s).apply {
			gravity = Gravity.CENTER
			setPadding(act.dp(12), act.dp(4), act.dp(12), act.dp(4))
			background = StateListDrawable().apply {
				addState(intArrayOf(android.R.attr.state_pressed), act.box(Color.BLACK, 12))
				addState(intArrayOf(), act.box(act.paper(), 12))
			}
			setTextColor(ColorStateList(
				arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
				intArrayOf(act.paper(), Color.BLACK),
			))
			setOnClickListener { onClick() }
		}
		buttons.addView(b, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = act.dp(8) })
	}
}
