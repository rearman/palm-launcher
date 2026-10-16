package com.rearman.palmlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.roundToInt

// The Palm battery gauge: a black outline with a nub at the right, filled to the charge level.
// While the phone charges, a lightning bolt shows across the body, in paper over the fill and black
// over the empty part. Sizes are Palm px; `pp` converts Palm px to screen px.
class BatteryGauge(ctx: Context, private val pp: (Float) -> Float, private val paper: Int) : View(ctx) {
	companion object {
		// Gauge size in Palm px (adjustable): body width and height, nub width and height, line width.
		const val BODY_W = 14f
		const val BODY_H = 7f
		const val NUB_W = 2f
		const val NUB_H = 3f
		const val LINE = 1f

		// Bolt corners as fractions of the body width and height.
		private val BOLT = floatArrayOf(
			0.58f, 0.05f, 0.30f, 0.58f, 0.48f, 0.58f, 0.40f, 0.95f, 0.72f, 0.40f, 0.54f, 0.40f, 0.64f, 0.05f,
		)
	}

	private var level = 100 // percent, 0 to 100
	private var charging = false
	private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

	fun set(level: Int, charging: Boolean) {
		val l = level.coerceIn(0, 100)
		if (l == this.level && charging == this.charging) return
		this.level = l
		this.charging = charging
		invalidate()
	}

	override fun onMeasure(widthSpec: Int, heightSpec: Int) =
		setMeasuredDimension(pp(BODY_W + NUB_W).roundToInt(), pp(BODY_H).roundToInt())

	override fun onDraw(c: Canvas) {
		val line = pp(LINE)
		val bw = pp(BODY_W)
		val bh = pp(BODY_H)
		paint.color = Color.BLACK
		// The outline as four bars, so each line stays sharp.
		c.drawRect(0f, 0f, bw, line, paint)
		c.drawRect(0f, bh - line, bw, bh, paint)
		c.drawRect(0f, 0f, line, bh, paint)
		c.drawRect(bw - line, 0f, bw, bh, paint)
		val nubTop = (bh - pp(NUB_H)) / 2f
		c.drawRect(bw, nubTop, bw + pp(NUB_W), nubTop + pp(NUB_H), paint)
		// The fill, with a 1 line gap inside the outline.
		val left = line * 2
		val right = bw - line * 2
		val fillRight = left + (right - left) * level / 100f
		c.drawRect(left, line * 2, fillRight, bh - line * 2, paint)
		if (!charging) return
		val bolt = Path()
		for (i in BOLT.indices step 2) {
			val x = BOLT[i] * bw
			val y = BOLT[i + 1] * bh
			if (i == 0) bolt.moveTo(x, y) else bolt.lineTo(x, y)
		}
		bolt.close()
		c.save()
		c.clipRect(0f, 0f, fillRight, bh)
		paint.color = paper
		c.drawPath(bolt, paint)
		c.restore()
		c.save()
		c.clipOutRect(0f, 0f, fillRight, bh)
		paint.color = Color.BLACK
		c.drawPath(bolt, paint)
		c.restore()
	}
}
