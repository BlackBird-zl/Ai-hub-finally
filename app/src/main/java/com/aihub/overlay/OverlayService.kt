package com.aihub.overlay

import android.app.*
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.view.WindowManager.LayoutParams.*
import android.widget.*
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var fabView: View
    private lateinit var menuView: View
    private var menuVisible = false

    // Posição do FAB
    private var fabX = 0
    private var fabY = 300
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialX = 0
    private var initialY = 0
    private var isDragging = false

    // Apps de IA configurados
    data class AIApp(
        val name: String,
        val emoji: String,
        val packageName: String,
        val color: Int,
        val supportsImage: Boolean
    )

    private val aiApps = listOf(
        AIApp("Claude",   "🤖", "com.anthropic.claude",                    Color.parseColor("#D97757"), true),
        AIApp("ChatGPT",  "🧠", "com.openai.chatgpt",                      Color.parseColor("#10a37f"), true),
        AIApp("Kimi",     "🌙", "com.moonshot.kimi",                        Color.parseColor("#4f8ef7"), false),
        AIApp("Blackbox", "⬛", "com.blackboxai.app",                       Color.parseColor("#a855f7"), false),
        AIApp("Gemini",   "✨", "com.google.android.apps.bard",             Color.parseColor("#4285f4"), true)
    )

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        createFab()
    }

    // ===== FAB (Robozinho flutuante) =====
    private fun createFab() {
        fabView = FrameLayout(this).apply {
            val size = dpToPx(60)

            // Fundo do botão
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(16).toFloat()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    colors = intArrayOf(Color.parseColor("#2a1f16"), Color.parseColor("#1a1410"))
                } else {
                    setColor(Color.parseColor("#2a1f16"))
                }
                setStroke(dpToPx(1), Color.parseColor("#D97757"))
            }

            elevation = dpToPx(8).toFloat()

            // Robozinho desenhado em canvas
            val robotView = object : View(context) {
                override fun onDraw(canvas: Canvas) {
                    drawRobot(canvas, width.toFloat(), height.toFloat())
                }
            }
            addView(robotView, FrameLayout.LayoutParams(size, size))
        }

        val params = layoutParams(dpToPx(60), dpToPx(60)).apply {
            x = fabX
            y = fabY
        }

        windowManager.addView(fabView, params)

        // Touch: arrastar ou clicar
        var downTime = 0L
        fabView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(fabView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - downTime
                    if (!isDragging && elapsed < 400) {
                        toggleMenu()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // ===== MENU =====
    private fun createMenu() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(20).toFloat()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    colors = intArrayOf(Color.parseColor("#241a12"), Color.parseColor("#1a1410"))
                } else {
                    setColor(Color.parseColor("#241a12"))
                }
                setStroke(dpToPx(1), Color.parseColor("#3d2b1a"))
            }
            elevation = dpToPx(12).toFloat()
        }

        // Título
        val title = TextView(this).apply {
            text = "⚡ O que fazer?"
            setTextColor(Color.parseColor("#D97757"))
            textSize = 11f
            setPadding(dpToPx(4), 0, 0, dpToPx(10))
            typeface = Typeface.DEFAULT_BOLD
        }
        layout.addView(title)

        // Botão: Abrir IA diretamente
        layout.addView(menuButton("🚀  Abrir uma IA", Color.parseColor("#2a1f16")) {
            hideMenu()
            showAiPicker(mode = "open")
        })

        // Separador
        val sep = View(this).apply {
            setBackgroundColor(Color.parseColor("#2d2218"))
        }
        layout.addView(sep, LinearLayout.LayoutParams(MATCH_PARENT, dpToPx(1)).apply {
            setMargins(0, dpToPx(6), 0, dpToPx(6))
        })

        // Botão: Tirar print
        layout.addView(menuButton("📸  Tirar print e enviar", Color.parseColor("#1e2a1e")) {
            hideMenu()
            takeScreenshot()
        })

        val menuParams = layoutParams(dpToPx(230), WRAP_CONTENT).apply {
            x = fabX - dpToPx(170)
            y = fabY - dpToPx(160)
        }

        menuView = layout
        windowManager.addView(menuView, menuParams)
    }

    private fun menuButton(text: String, bgColor: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#f0ebe4"))
            textSize = 14f
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(bgColor)
            }
            setOnClickListener { onClick() }
            val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            lp.bottomMargin = dpToPx(4)
            layoutParams = lp
        }
    }

    // ===== AI PICKER =====
    private fun showAiPicker(mode: String, screenshotUri: android.net.Uri? = null) {
        val pickerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(20).toFloat()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    colors = intArrayOf(Color.parseColor("#241a12"), Color.parseColor("#1a1410"))
                } else {
                    setColor(Color.parseColor("#241a12"))
                }
                setStroke(dpToPx(1), Color.parseColor("#3d2b1a"))
            }
            elevation = dpToPx(12).toFloat()
        }

        val title = TextView(this).apply {
            text = if (mode == "open") "Qual IA abrir?" else "Enviar print para:"
            setTextColor(Color.parseColor("#D97757"))
            textSize = 11f
            setPadding(dpToPx(4), 0, 0, dpToPx(10))
            typeface = Typeface.DEFAULT_BOLD
        }
        pickerLayout.addView(title)

        for (app in aiApps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(12).toFloat()
                    setColor(Color.parseColor("#1e1610"))
                }
                val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                lp.bottomMargin = dpToPx(4)
                layoutParams = lp

                // Emoji
                addView(TextView(context).apply {
                    text = app.emoji
                    textSize = 20f
                    setPadding(0, 0, dpToPx(10), 0)
                })

                // Nome
                addView(TextView(context).apply {
                    text = app.name
                    setTextColor(Color.parseColor("#f0ebe4"))
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                })

                setOnClickListener {
                    try { windowManager.removeView(pickerLayout) } catch (_: Exception) {}
                    if (mode == "open") {
                        openApp(app)
                    } else {
                        if (screenshotUri != null) sendImageToApp(app, screenshotUri)
                    }
                }
            }
            pickerLayout.addView(row)
        }

        // Botão cancelar
        pickerLayout.addView(TextView(this).apply {
            text = "✕  Cancelar"
            setTextColor(Color.parseColor("#7c6a5a"))
            textSize = 12f
            setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(4))
            setOnClickListener { try { windowManager.removeView(pickerLayout) } catch (_: Exception) {} }
        })

        val pickerParams = layoutParams(dpToPx(230), WRAP_CONTENT).apply {
            x = fabX - dpToPx(170)
            y = fabY - dpToPx(280)
        }
        windowManager.addView(pickerLayout, pickerParams)
    }

    // ===== ABRIR APP =====
    private fun openApp(app: AIApp) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            Toast.makeText(this, "${app.name} não está instalado.", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== ENVIAR IMAGEM =====
    private fun sendImageToApp(app: AIApp, uri: android.net.Uri) {
        if (!app.supportsImage) {
            Toast.makeText(
                this,
                "⚠️ ${app.name} não suporta receber imagens diretamente.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            Toast.makeText(this, "${app.name} não está instalado.", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage(app.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(shareIntent)
    }

    // ===== SCREENSHOT =====
    private fun takeScreenshot() {
        val intent = Intent(this, ScreenshotActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    // Chamado por ScreenshotActivity após captura
    fun onScreenshotReady(uri: android.net.Uri) {
        showAiPicker(mode = "screenshot", screenshotUri = uri)
    }

    // ===== TOGGLE MENU =====
    private fun toggleMenu() {
        if (menuVisible) hideMenu() else showMenu()
    }

    private fun showMenu() {
        menuVisible = true
        createMenu()
    }

    private fun hideMenu() {
        if (menuVisible && ::menuView.isInitialized) {
            try { windowManager.removeView(menuView) } catch (_: Exception) {}
        }
        menuVisible = false
    }

    // ===== DESENHO DO ROBOZINHO =====
    private fun drawRobot(canvas: Canvas, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Corpo/cabeça
        paint.color = Color.parseColor("#D97757")
        val headRect = RectF(w * 0.18f, h * 0.25f, w * 0.82f, h * 0.78f)
        canvas.drawRoundRect(headRect, 14f, 14f, paint)

        // Olhos
        paint.color = Color.parseColor("#1a1410")
        canvas.drawCircle(w * 0.36f, h * 0.50f, w * 0.09f, paint)
        canvas.drawCircle(w * 0.64f, h * 0.50f, w * 0.09f, paint)

        // Brilho nos olhos
        paint.color = Color.parseColor("#ffffff")
        paint.alpha = 180
        canvas.drawCircle(w * 0.39f, h * 0.47f, w * 0.04f, paint)
        canvas.drawCircle(w * 0.67f, h * 0.47f, w * 0.04f, paint)
        paint.alpha = 255

        // Boca
        paint.color = Color.parseColor("#1a1410")
        paint.alpha = 150
        val mouthRect = RectF(w * 0.34f, h * 0.64f, w * 0.66f, h * 0.71f)
        canvas.drawRoundRect(mouthRect, 4f, 4f, paint)
        paint.alpha = 255

        // Antena
        paint.color = Color.parseColor("#D97757")
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(w * 0.5f, h * 0.25f, w * 0.5f, h * 0.12f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#E8A87C")
        canvas.drawCircle(w * 0.5f, h * 0.10f, w * 0.08f, paint)

        // Orelhas
        paint.color = Color.parseColor("#b85e3a")
        canvas.drawRoundRect(RectF(w*0.08f, h*0.38f, w*0.18f, h*0.58f), 6f, 6f, paint)
        canvas.drawRoundRect(RectF(w*0.82f, h*0.38f, w*0.92f, h*0.58f), 6f, 6f, paint)

        // Pescoço/base
        paint.alpha = 150
        canvas.drawRoundRect(RectF(w*0.32f, h*0.78f, w*0.68f, h*0.90f), 6f, 6f, paint)
        paint.alpha = 255
    }

    // ===== HELPERS =====
    private fun layoutParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) TYPE_APPLICATION_OVERLAY else TYPE_PHONE,
        FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "AI Hub", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Hub ativo")
            .setContentText("Robozinho flutuando na tela 🤖")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { windowManager.removeView(fabView) } catch (_: Exception) {}
        hideMenu()
    }

    companion object {
        const val CHANNEL_ID = "ai_hub_channel"
        const val NOTIF_ID = 1
        @Volatile var instance: OverlayService? = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        return START_STICKY // reinicia automaticamente se o sistema matar o serviço
    }
}
