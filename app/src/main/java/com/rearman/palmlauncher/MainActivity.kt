package com.rearman.palmlauncher

import android.app.Activity
import android.content.Intent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
	// Rebuild the list each time the home screen shows, so new and removed apps appear.
	override fun onResume() {
		super.onResume()
		val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
		val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
		packageManager.queryIntentActivities(main, 0)
			.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
			.forEach { app ->
				list.addView(TextView(this).apply {
					text = app.loadLabel(packageManager)
					textSize = 20f
					setPadding(32, 24, 32, 24)
					setOnClickListener {
						startActivity(
							Intent(main)
								.setClassName(app.activityInfo.packageName, app.activityInfo.name)
								.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
						)
					}
				})
			}
		setContentView(ScrollView(this).apply { addView(list) })
	}
}
