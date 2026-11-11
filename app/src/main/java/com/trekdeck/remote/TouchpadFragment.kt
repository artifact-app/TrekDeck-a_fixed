package com.trekdeck.remote

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.trekdeck.remote.databinding.FragmentTouchpadBinding
import kotlin.math.abs

/** Тачпад: 1 палец — мышь, тап — клик, 2 пальца — прокрутка, тап двумя — правый клик. */
class TouchpadFragment : Fragment() {

    private var _b: FragmentTouchpadBinding? = null
    private val b get() = _b!!

    private val sensitivity = 1.6f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var travel = 0f
    private var maxPointers = 0
    private var accX = 0f
    private var accY = 0f
    private var accScroll = 0f
    private var lastSend = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentTouchpadBinding.inflate(inflater, container, false)
        return b.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // делаем область квадратной: высота = ширине
        b.pad.post {
            _b?.let {
                it.pad.layoutParams.height = it.pad.width
                it.pad.requestLayout()
            }
        }
        b.pad.setOnTouchListener { _, e -> onTouch(e); true }
        b.leftClickBtn.setOnClickListener { sendCmd("mouse_click", "button" to "left") }
        b.rightClickBtn.setOnClickListener { sendCmd("mouse_click", "button" to "right") }
    }

    private fun onTouch(e: MotionEvent) {
        val slop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y
                downTime = e.eventTime
                travel = 0f
                maxPointers = 1
                accX = 0f; accY = 0f; accScroll = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // продолжаем с оставшимся пальцем, чтобы курсор не прыгал
                val idx = if (e.actionIndex == 0) 1 else 0
                if (idx < e.pointerCount) {
                    lastX = e.getX(idx); lastY = e.getY(idx)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - lastX
                val dy = e.y - lastY
                lastX = e.x; lastY = e.y
                travel += abs(dx) + abs(dy)
                if (e.pointerCount >= 2) {
                    accScroll -= dy / 25f  // палец вниз = прокрутка вверх (как на телефоне)
                    val steps = accScroll.toInt()
                    if (steps != 0) {
                        TrekDeck.client.send("scroll", "dy" to steps) // без тоста «нет связи» на каждый шаг прокрутки
                        accScroll -= steps
                    }
                } else {
                    accX += dx * sensitivity
                    accY += dy * sensitivity
                    flushMove(e.eventTime, force = false)
                }
            }
            MotionEvent.ACTION_UP -> {
                flushMove(e.eventTime, force = true)
                if (travel < slop && e.eventTime - downTime < 250) {
                    sendCmd("mouse_click", "button" to if (maxPointers >= 2) "right" else "left")
                }
            }
        }
    }

    /** Отправляем движение не чаще раза в ~16 мс; остаток (дробную часть) копим дальше. */
    private fun flushMove(now: Long, force: Boolean) {
        if (!force && now - lastSend < 16) return
        val ix = accX.toInt()
        val iy = accY.toInt()
        if (ix == 0 && iy == 0) return
        lastSend = now
        accX -= ix
        accY -= iy
        TrekDeck.client.send("mouse_move", "dx" to ix, "dy" to iy)
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
