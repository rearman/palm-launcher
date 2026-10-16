package com.rearman.palmlauncher

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

// Palm menu bar, as in Datebook: menu titles across the top, the commands of the open menu below.
class MenuBar(
	private val act: MainActivity,
	private val menus: List<Pair<String, List<Pair<String, () -> Unit>>>>,
) : Dialog(act) {
	private val titles = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
	private val items = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
	private var open = 0

	init {
		requestWindowFeature(Window.FEATURE_NO_TITLE)
		val frame = LinearLayout(act).apply {
			orientation = LinearLayout.VERTICAL
			background = act.palmBox(act.paper())
			setPadding(act.pp(1f), act.pp(1f), act.pp(1f), act.pp(1.5f))
		}
		frame.addView(titles, LinearLayout.LayoutParams(MATCH, WRAP))
		frame.addView(View(act).apply { setBackgroundColor(Color.BLACK) }, LinearLayout.LayoutParams(MATCH, act.pp(1f)))
		frame.addView(items, LinearLayout.LayoutParams(MATCH, WRAP))
		// The menu fills the screen. A tap below the commands closes it.
		frame.addView(View(act).apply { setOnClickListener { dismiss() } }, LinearLayout.LayoutParams(MATCH, 0, 1f))
		setContentView(frame)
		window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
		window?.setGravity(Gravity.TOP)
		window?.setLayout(MATCH, MATCH)
		draw()
	}

	private fun draw() {
		titles.removeAllViews()
		menus.forEachIndexed { i, (name, _) ->
			titles.addView(act.text(name, bold = true).apply {
				setPadding(act.pp(5f), act.pp(3f), act.pp(5f), act.pp(3f))
				if (i == open) {
					setBackgroundColor(Color.BLACK)
					setTextColor(act.paper())
				}
				setOnClickListener { open = i; draw() }
			})
		}
		items.removeAllViews()
		for ((name, action) in menus[open].second) {
			items.addView(act.text(name).apply {
				setPadding(act.pp(6.5f), act.pp(3.5f), act.pp(6.5f), act.pp(3.5f))
				setOnClickListener { dismiss(); action() }
			}, LinearLayout.LayoutParams(MATCH, WRAP))
		}
	}
}
