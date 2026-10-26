package top.xiaofeigun.tvliveplayer.ui.player

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/** 播放器全屏触屏手势：单击 / 双击 / 长按 / 上下滑，仅在视频播放层（Level 0）生效 */
class PlayerGestures(private val listener: Listener) {

    interface Listener {
        fun isPlayerLayerActive(): Boolean
        fun onSingleTap()
        fun onDoubleTap()
        fun onLongPress()
        /** @param next true 表示上滑（下一个频道） */
        fun onVerticalSwipe(next: Boolean)
    }

    /** 最近一次触屏操作时间，用于屏蔽触屏设备上紧随其后的合成按键事件 */
    var lastTouchTime = 0L
        private set

    @SuppressLint("ClickableViewAccessibility")
    fun attach(vararg targets: View) {
        val detector = GestureDetector(targets.first().context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                markTouch()
                listener.onSingleTap()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                markTouch()
                listener.onDoubleTap()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                markTouch()
                listener.onLongPress()
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                markTouch()
                if (abs(diffY) > SWIPE_THRESHOLD && abs(diffY) > abs(diffX)) {
                    listener.onVerticalSwipe(next = diffY < 0)
                    return true
                }
                return false
            }
        })
        val touchListener = View.OnTouchListener { _, event ->
            if (!listener.isPlayerLayerActive()) return@OnTouchListener false
            detector.onTouchEvent(event)
            true
        }
        targets.forEach { it.setOnTouchListener(touchListener) }
    }

    private fun markTouch() {
        lastTouchTime = System.currentTimeMillis()
    }

    private companion object {
        const val SWIPE_THRESHOLD = 100
    }
}
