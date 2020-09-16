package dev.gw.dylan.camera

/**
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * https://github.com/digital-voting-pass/polling-station-app/blob/master/app/src/main/java/com/digitalvotingpass/camera/Overlay.java
 */

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.content.ContextCompat
import dev.gw.dylan.R

class Overlay(context: Context?, set: AttributeSet?) : View(context, set) {
    private val paint = Paint()
    private val transparentPaint = Paint()
    private var rect: Rect? = Rect(0, 0, 0, 0)
    private val xfermode: PorterDuffXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    fun setMargins(left: Int, top: Int, right: Int, bottom: Int) {
        if (layoutParams is MarginLayoutParams) {
            val p = layoutParams as MarginLayoutParams
            p.setMargins(left, top, right, bottom)
            requestLayout()
        }
    }

    fun setRect(rect: Rect?) {
        this.rect = rect
    }

    public override fun onDraw(canvas: Canvas) {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        // The color I chose is just a random existing grey color
        paint.color = ContextCompat.getColor(context, R.color.cardview_dark_background)
        paint.alpha = TRANSPARENCY
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        transparentPaint.alpha = 0xFF
        transparentPaint.xfermode = xfermode
        canvas.drawRect(rect!!, transparentPaint)
    }

    companion object {
        /**
         * Transparency of overlaid part in hex, 0-255
         */
        private const val TRANSPARENCY = 0xA0
    }
}