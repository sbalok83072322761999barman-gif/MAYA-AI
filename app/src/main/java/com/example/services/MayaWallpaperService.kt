package com.example.services

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.min
import kotlin.math.sin

class MayaWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = OrbWallpaperEngine()

    inner class OrbWallpaperEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var phase = 0f

        private val bgPaint = Paint().apply { color = Color.parseColor("#070B12") }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#5500E5FF")
        }
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private val drawRunnable = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler.postDelayed(this, 42L) // ~24fps lightweight for 4GB RAM battery savings
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(drawRunnable)
            if (visible) handler.post(drawRunnable)
        }

        override fun onDestroy() {
            super.onDestroy()
            handler.removeCallbacks(drawRunnable)
        }

        private fun drawFrame() {
            val holder: SurfaceHolder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                val w = canvas.width.toFloat()
                val h = canvas.height.toFloat()
                val cx = w / 2f
                val cy = h / 2f
                val baseR = min(w, h) * 0.22f
                phase += 0.06f
                val pulse = 1f + sin(phase) * 0.08f

                canvas.drawRect(0f, 0f, w, h, bgPaint)

                corePaint.shader = RadialGradient(
                    cx, cy, baseR * 1.4f * pulse,
                    intArrayOf(Color.parseColor("#FF00E5FF"), Color.parseColor("#AA7C4DFF"), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx, cy, baseR * 1.4f * pulse, corePaint)

                ringPaint.color = Color.parseColor("#6600E5FF")
                canvas.drawCircle(cx, cy, baseR * 1.15f * pulse, ringPaint)
                ringPaint.color = Color.parseColor("#447C4DFF")
                canvas.drawCircle(cx, cy, baseR * 1.55f / pulse, ringPaint)
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
