package com.example.permissiontest


import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.core.app.NotificationCompat
import pl.droidsonroids.gif.AnimationListener
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView
import kotlin.math.absoluteValue


class MainService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var viewArray: Array<View>
    private lateinit var visualView: View
    private lateinit var touchableView: View

    private lateinit var relaxGif: GifDrawable
    private lateinit var ruaGif: GifDrawable
    private var isRelaxed = true

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val notification = buildNotification()
        startForeground(1, notification)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        viewArray = createFloatingView(windowManager, this)
        return START_STICKY
    }


    override fun onDestroy() {
        super.onDestroy()
        for (view in viewArray) {
            windowManager.removeView(view)
        }
    }


    override fun onBind(intent: Intent?): IBinder? = null


    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "main_service",
                "运行通知",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
        val intent = Intent(this, SettingsActivity::class.java)
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, "main_service")
            .setContentTitle("运行中")
            .setContentText("点我打开设置")
            .setSmallIcon(R.drawable.reconstruction)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.cute_icon))
            .setContentIntent(pi)
            .build()
        return notification
    }


    /**
     * Build and show main view
     */
    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    fun createFloatingView(windowManager: WindowManager, service: MainService): Array<View> {

        visualView = LayoutInflater.from(service).inflate(R.layout.visual_view, null)
        touchableView = LayoutInflater.from(service).inflate(R.layout.touchable_view, null)
        val viewArray = arrayOf(visualView, touchableView)

        val visualViewParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                LayoutParams.TYPE_PHONE,
            LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_NOT_TOUCHABLE or
//                    LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
//            alpha = 0.9f
            alpha = 1.0f
            x = videoWidthByPX()
            y = videoHeightByPX()
        }

        val touchableViewParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                LayoutParams.TYPE_PHONE,
            LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            width = touchableWidthByPX()
            height = touchableHeightByPX()
        }

//        visualView.setOnTouchListener { _, _ -> false }
//        visualView.setOnClickListener { }

        shiftParams(touchableViewParams, visualViewParams)

        touchableView.setOnTouchListener(
            OnTouchListener(
                this,
                windowManager,
                touchableView,
                touchableViewParams,
                visualView,
                visualViewParams
            )
        )

        touchableView.setOnClickListener {
            interact()
        }

        relaxGif = GifDrawable(resources, R.drawable.relax).apply {
            loopCount = 0
        }

        ruaGif = GifDrawable(resources, R.drawable.interact).apply {
            loopCount = 1
        }

        (visualView as GifImageView).apply {
            setImageDrawable(relaxGif)
        }

        windowManager.run {
            addView(visualView, visualViewParams)
            addView(touchableView, touchableViewParams)
        }

        return viewArray

    }


    private fun interact() {
        relaxGif.stop()
        isRelaxed = false
        ruaGif.reset()
        (visualView as GifImageView).setImageDrawable(ruaGif)

        ruaGif.addAnimationListener(object : AnimationListener {
            override fun onAnimationCompleted(loopNumber: Int) {
                relaxGif.reset()
                relaxGif.start()
                (visualView as GifImageView).setImageDrawable(relaxGif)
                isRelaxed = true
                ruaGif.removeAnimationListener(this) // 移除监听避免内存泄漏
            }
        })
    }

    inner class OnTouchListener(
        private val context: MainService,
        private val windowManager: WindowManager,
        private val selfView: View,
        private val selfParams: LayoutParams,
        private val relatedView: View,
        private val relatedParams: LayoutParams,
    ) : View.OnTouchListener {

        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragged = false
        private var isPressed = false
        private var longPressTriggered = false

        private val handler = Handler(Looper.getMainLooper())
        private var longPressRunnable: Runnable? = null

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val touchSlop = ViewConfiguration.get(v.context).scaledTouchSlop.toFloat()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录初始位置
                    initialX = selfParams.x
                    initialY = selfParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragged = false
                    isPressed = true
                    longPressTriggered = false
                    scheduleLongPress()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    selfParams.x = initialX + deltaX
                    selfParams.y = initialY + deltaY

                    shiftParams(selfParams, relatedParams)

                    windowManager.updateViewLayout(selfView, selfParams)
                    windowManager.updateViewLayout(relatedView, relatedParams)

                    if (!isDragged) {
                        if (deltaX.absoluteValue > touchSlop || deltaY.absoluteValue > touchSlop) {
                            cancelLongPress()
                            isDragged = true
                        }
                    }

                    return true
                }

                MotionEvent.ACTION_UP -> {

                    cancelLongPress()

                    if (isDragged) {
                        return true
                    } else {
                        if (!longPressTriggered) {
                            selfView.performClick()
                            longPressTriggered = false
                        }
                        return false
                    }
                }

                else -> return false
            }
        }

        private fun scheduleLongPress() {
            longPressRunnable = Runnable {
                if (isPressed) {
                    longPressTriggered = true
                    context.onLongPress()
                }
            }
            handler.postDelayed(longPressRunnable!!, LONG_PRESS_TIME)
        }

        private fun cancelLongPress() {
            longPressRunnable?.let {
                handler.removeCallbacks(it)
                longPressRunnable = null
            }
        }
    }

    private fun onLongPress() {
        val openSettingsIntent = Intent(this, SettingsActivity::class.java)
        openSettingsIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        startActivity(openSettingsIntent)
    }


    fun shiftParams(originParams: LayoutParams, relatedParams: LayoutParams) {
        relatedParams.apply {
            x = originParams.x + xShiftByPX()
            y = originParams.y + yShiftByPX()
        }
    }


    private fun dp2px(dp: Int): Float {
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        )
        return px
    }

    private fun xShiftByPX() = dp2px(X_SHIFT).toInt()
    private fun yShiftByPX() = dp2px(Y_SHIFT).toInt()
    private fun videoWidthByPX() = dp2px(VIDEO_WIDTH).toInt()
    private fun videoHeightByPX() = dp2px(VIDEO_HEIGHT).toInt()
    private fun touchableWidthByPX() = dp2px(TOUCHABLE_WIDTH).toInt()
    private fun touchableHeightByPX() = dp2px(TOUCHABLE_HEIGHT).toInt()

    companion object {

        private const val X_SHIFT = -140
        private const val Y_SHIFT = -327
        private const val VIDEO_WIDTH = 200
        private const val VIDEO_HEIGHT = 200
        private const val TOUCHABLE_WIDTH = 67
        private const val TOUCHABLE_HEIGHT = 145

        private const val LONG_PRESS_TIME = 500L
    }

}