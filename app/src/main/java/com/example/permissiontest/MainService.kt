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
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Binder
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

    private var orientation = Configuration.ORIENTATION_PORTRAIT

    private val binder = MainServiceBinder()
    inner class MainServiceBinder : Binder() {
        fun getService(): MainService = this@MainService
    }
    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Initialize service
     **/
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val notification = buildNotification()
        startForeground(1, notification)

        // Initializes views
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        viewArray = createFloatingView(windowManager, this)

        return START_STICKY
    }

    /**
     * Removes views when destroyed
     */
    override fun onDestroy() {
        super.onDestroy()
        for (view in viewArray) {
            windowManager.removeView(view)
        }
    }

    /**
     * Notification builder for foreground service
     */
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

        // Sets PendingIntent to SettingsActivity
        val intent = Intent(this, SettingsActivity::class.java)
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // Builds Notification
        val notification = NotificationCompat.Builder(this, "main_service")
            .setContentTitle("运行中")
            .setContentText("点我打开设置")
            .setSmallIcon(R.drawable.reconstruction)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.cute_icon))
            // Opens SettingsActivity when clicked
            .setContentIntent(pi)
            .build()

        return notification
    }


    /**
     * Build and show main view
     */
    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    fun createFloatingView(windowManager: WindowManager, service: MainService): Array<View> {

        // Initializes views from layout files
        visualView = LayoutInflater.from(service).inflate(R.layout.visual_view, null)
        touchableView = LayoutInflater.from(service).inflate(R.layout.touchable_view, null)
        val viewArray = arrayOf(visualView, touchableView)

        // Sets layout parameters
        val visualViewParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                // Sets view as overlay
                LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                LayoutParams.TYPE_PHONE,
            // Sets view not interactable
            LayoutParams.FLAG_NOT_FOCUSABLE or
                    LayoutParams.FLAG_NOT_TOUCHABLE or
                    LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        val touchableViewParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                // Sets view as overlay
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
            // Sets bounding box size
            width = touchableWidthByPX()
            height = touchableHeightByPX()
        }

        // Sets Listeners for touchable view
        touchableView.setOnTouchListener(
            // Uses custom OnTouchListener
            OnTouchListener(
                this,
                touchableView
            )
        )
        touchableView.setOnClickListener {
            interact()
        }

        // initializes video resources
        relaxGif = GifDrawable(resources, R.drawable.relax).apply {
            loopCount = 0
        }
        ruaGif = GifDrawable(resources, R.drawable.interact).apply {
            loopCount = 1
            pause()
        }

        // Sets initial visual view
        (visualView as GifImageView).apply {
            setImageDrawable(relaxGif)
        }

        // show views
        windowManager.apply {
            addView(visualView, visualViewParams)
            addView(touchableView, touchableViewParams)
        }

        // position views
        moveTo(0,0)

        return viewArray
    }

    /**
     * Changes to ruaGif when interacted
     */
    private fun interact() {
        // stop relaxing animation
        relaxGif.stop()
        isRelaxed = false
        ruaGif.reset()
        // start interacting animation
        (visualView as GifImageView).setImageDrawable(ruaGif)

        ruaGif.addAnimationListener(object : AnimationListener {
            override fun onAnimationCompleted(loopNumber: Int) {
                // reset visual view to relax when animation completed
                relaxGif.reset()
                relaxGif.start()
                (visualView as GifImageView).setImageDrawable(relaxGif)
                isRelaxed = true
                // remove self
                ruaGif.removeAnimationListener(this)
            }
        })
    }

    /**
     * Custom OnTouchListener for touchable view
     */
    inner class OnTouchListener(
        private val context: MainService,
        private val selfView: View,
    ) : View.OnTouchListener {

        // position recorders
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        // flags
        private var isDragged = false
        private var longPressTriggered = false

        // delayed detection related
        private val handler = Handler(Looper.getMainLooper())
        private var longPressRunnable: Runnable? = null

        /**
         * Handles press and drag events
         */
        override fun onTouch(v: View, event: MotionEvent): Boolean {

            val touchSlop = ViewConfiguration.get(v.context).scaledTouchSlop.toFloat()
            val selfParams: LayoutParams = selfView.layoutParams as LayoutParams

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {

                    // record the initial position
                    initialX = selfParams.x
                    initialY = selfParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    // reset the dragged & pressed flag
                    isDragged = false
                    longPressTriggered = false

                    // start the long press timer
                    scheduleLongPress()

                    // consume the touch event
                    return true
                }

                MotionEvent.ACTION_MOVE -> {

                    // calculate shift
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()

                    // apply shift
                    moveTo(initialX + deltaX, initialY + deltaY)

                    // set drag flag if dragged beyond touch slop
                    if (!isDragged) {
                        if (deltaX.absoluteValue > touchSlop || deltaY.absoluteValue > touchSlop) {
                            cancelLongPress()
                            isDragged = true
                        }
                    }

                }

                MotionEvent.ACTION_UP -> {

                    // interrupt the long press timer since time unreached
                    cancelLongPress()

                    // preform click if long press not triggered
                    if (!isDragged) {
                        if (!longPressTriggered) {
                            selfView.performClick()
                            longPressTriggered = false
                        }
                    }
                }

                else -> {}
            }
            return true
        }

        /**
         * Sets long press timer
         */
        private fun scheduleLongPress() {

            longPressRunnable = Runnable {
                // reset the press triggered flag
                longPressTriggered = true
                // perform long press action
                context.onLongPress()
            }

            // trigger long press if uninterrupted
            handler.postDelayed(longPressRunnable!!, LONG_PRESS_TIME)
        }

        /**
         * Cancels long press timer
         */
        private fun cancelLongPress() {
            longPressRunnable?.let {
                handler.removeCallbacks(it)
                longPressRunnable = null
            }
        }
    }

    /**
     * Opens settings activity when long pressed
     */
    private fun onLongPress() {
        val openSettingsIntent = Intent(this, SettingsActivity::class.java)
        openSettingsIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        startActivity(openSettingsIntent)
    }

    /**
     * Shifts visual view parameters according to touchable view position
     */
    fun shiftParams(originParams: LayoutParams, relatedParams: LayoutParams) {
        relatedParams.apply {
            x = originParams.x + xShiftByPX()
            y = originParams.y + yShiftByPX()
        }
    }

    /**
     * move view
     */
    fun moveTo(newX: Int, newY: Int){
        (touchableView.layoutParams as LayoutParams).apply {
            x = newX
            y = newY
        }.run {
            shiftParams(this, visualView.layoutParams as LayoutParams)
        }
        windowManager.apply {
            updateViewLayout(touchableView, touchableView.layoutParams)
            updateViewLayout(visualView, visualView.layoutParams)
        }
    }

    /**
     * Converts dp to pixel
     */
    private fun dp2px(dp: Int): Float {
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        )
        return px
    }

    // dumb value getters
    private fun xShiftByPX() = dp2px(X_SHIFT).toInt()
    private fun yShiftByPX() = dp2px(Y_SHIFT).toInt()
//    private fun videoWidthByPX() = dp2px(VIDEO_WIDTH).toInt()
//    private fun videoHeightByPX() = dp2px(VIDEO_HEIGHT).toInt()
    private fun touchableWidthByPX() = dp2px(TOUCHABLE_WIDTH).toInt()
    private fun touchableHeightByPX() = dp2px(TOUCHABLE_HEIGHT).toInt()

    companion object {

        // constants for positioning
        private const val X_SHIFT = -140
        private const val Y_SHIFT = -327
//        private const val VIDEO_WIDTH = 200
//        private const val VIDEO_HEIGHT = 200
        private const val TOUCHABLE_WIDTH = 67
        private const val TOUCHABLE_HEIGHT = 145

        // long press time in milliseconds
        private const val LONG_PRESS_TIME = 500L
    }

}