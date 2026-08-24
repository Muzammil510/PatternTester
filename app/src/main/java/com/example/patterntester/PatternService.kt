package com.example.patterntester

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PatternService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayLayout: LinearLayout? = null
    private var statusText: TextView? = null
    private var actionButton: Button? = null

    private var isRunning = false
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private val patternQueue = mutableListOf<List<Int>>()

    // Default screen coordinates for a 3x3 pattern grid
    private val dotMap = mapOf(
        1 to Pair(250f, 800f),
        2 to Pair(540f, 800f),
        3 to Pair(830f, 800f),
        4 to Pair(250f, 1100f),
        5 to Pair(540f, 1100f),
        6 to Pair(830f, 1100f),
        7 to Pair(250f, 1400f),
        8 to Pair(540f, 1400f),
        9 to Pair(830f, 1400f)
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        generate5000Patterns()
        createFloatingOverlay()
    }

    private fun generate5000Patterns() {
        patternQueue.clear()
        val baseList = listOf(3, 2, 1, 4, 5, 6, 8, 9, 7)
        for (i in 1..5000) {
            patternQueue.add(baseList.shuffled())
        }
    }

    private fun createFloatingOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        overlayLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(24, 24, 24, 24)
        }

        statusText = TextView(this).apply {
            text = "Ready (5000 Patterns)"
            setTextColor(Color.WHITE)
            textSize = 14f
        }

        actionButton = Button(this).apply {
            text = "START"
            setBackgroundColor(Color.GREEN)
            setTextColor(Color.BLACK)
            setOnClickListener {
                if (!isRunning) {
                    isRunning = true
                    text = "STOP"
                    setBackgroundColor(Color.RED)
                    runCurrentPatternCycle()
                } else {
                    stopRunner()
                }
            }
        }

        overlayLayout?.addView(statusText)
        overlayLayout?.addView(actionButton)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 100
        }

        windowManager.addView(overlayLayout, params)
    }

    private fun runCurrentPatternCycle() {
        if (!isRunning || currentIndex >= patternQueue.size) {
            stopRunner()
            return
        }

        val pattern = patternQueue[currentIndex]
        statusText?.text = "Running #${currentIndex + 1}: Entry 1/2"

        // Entry 1
        dispatchSwipe(pattern) {
            // Wait 800ms between 1st and 2nd attempt
            handler.postDelayed({
                if (!isRunning) return@postDelayed

                statusText?.text = "Running #${currentIndex + 1}: Entry 2/2"
                // Entry 2
                dispatchSwipe(pattern) {
                    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    Log.d("PatternTester", "Completed 2x for Pattern #${currentIndex + 1}: $pattern at $timestamp")

                    currentIndex++

                    // 5-Second Interval Countdown
                    startFiveSecondDelay()
                }
            }, 800)
        }
    }

    private fun startFiveSecondDelay() {
        var remainingSeconds = 5
        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return

                if (remainingSeconds > 0) {
                    statusText?.text = "Saved #${currentIndex} | Waiting: ${remainingSeconds}s"
                    remainingSeconds--
                    handler.postDelayed(this, 1000)
                } else {
                    runCurrentPatternCycle()
                }
            }
        })
    }

    private fun dispatchSwipe(pattern: List<Int>, onComplete: () -> Unit) {
        val path = Path()
        val start = dotMap[pattern[0]] ?: return
        path.moveTo(start.first, start.second)

        for (i in 1 until pattern.size) {
            val next = dotMap[pattern[i]] ?: continue
            path.lineTo(next.first, next.second)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 400)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                onComplete()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                stopRunner()
            }
        }, null)
    }

    private fun stopRunner() {
        isRunning = false
        actionButton?.text = "START"
        actionButton?.setBackgroundColor(Color.GREEN)
        statusText?.text = "Stopped at #${currentIndex}"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopRunner() }

    override fun onDestroy() {
        super.onDestroy()
        overlayLayout?.let { windowManager.removeView(it) }
    }
}
