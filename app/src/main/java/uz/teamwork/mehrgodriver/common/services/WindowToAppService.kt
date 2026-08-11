package uz.teamwork.mehrgodriver.common.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.RelativeLayout
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.databinding.LayoutServiceWindowToAppBinding
import uz.teamwork.mehrgodriver.presentation.activity.main.MainActivity

class WindowToAppService : Service() {
    private lateinit var binding: LayoutServiceWindowToAppBinding
    private lateinit var windowManager: WindowManager

    // Only removeView what was actually added — addView can fail (revoked overlay permission).
    private var viewAdded = false

    override fun onBind(p0: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        binding = LayoutServiceWindowToAppBinding.inflate(LayoutInflater.from(applicationContext))

        val params: WindowManager.LayoutParams =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
            } else {
                WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
            }

        params.gravity = Gravity.TOP or Gravity.START
        params.x = this.resources.displayMetrics.widthPixels - dipToPixels(this, 64f).toInt()
        params.y = dipToPixels(this, 120f).toInt()

        // A little larger than the visible bubble so the card's drop shadow
        // isn't clipped by the window edge.
        params.height = dipToPixels(this, 64f).toInt()
        params.width = dipToPixels(this, 64f).toInt()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // SYSTEM_ALERT_WINDOW can be revoked at any time; addView then throws
        // BadTokenException inside Service.onCreate → RuntimeException (prod crash).
        // Deliberately NO canDrawOverlays pre-check — some OEMs misreport it false while
        // overlays still work (see AccessPermissionsFragment) — catch and bow out instead.
        try {
            windowManager.addView(binding.root, params)
            viewAdded = true
        } catch (e: Exception) {
            stopSelf()
            return
        }

        val relativeLayout: RelativeLayout = binding.root
        relativeLayout.setOnTouchListener(
            @SuppressLint("ClickableViewAccessibility")
            object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initTouchX = 0f
                private var initTouchY = 0f
                private var lastAction = 0

                override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {

                    if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                        initialX = params.x
                        initialY = params.y
                        initTouchX = motionEvent.rawX
                        initTouchY = motionEvent.rawY
                        lastAction = motionEvent.action
                        return false
                    }
                    if (motionEvent.action == MotionEvent.ACTION_UP) {
                        if (lastAction == MotionEvent.ACTION_DOWN) {
                            lastAction = motionEvent.action
                            return false

                        }
                    }

                    if (motionEvent.action == MotionEvent.ACTION_MOVE) {
                        params.x = initialX + (motionEvent.rawX - initTouchX).toInt()
                        params.y = initialY + (motionEvent.rawY - initTouchY).toInt()
                        windowManager.updateViewLayout(binding.root, params)
                        lastAction = motionEvent.action

                        return false
                    }
                    return false
                }
            })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        binding.root.setOnClickListener {
            val intent1 = Intent(this@WindowToAppService, MainActivity::class.java)
            intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent1)
        }

        intent?.let {
            when (it.action) {
                Constants.ACTION_START_WINDOW_SERVICE -> {}

                Constants.ACTION_STOP_WINDOW_SERVICE -> {
                    stopSelf()
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun dipToPixels(context: Context, dipValue: Float): Float {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (viewAdded) {
            try {
                windowManager.removeView(binding.root)
            } catch (_: Exception) {
            }
        }
    }
}