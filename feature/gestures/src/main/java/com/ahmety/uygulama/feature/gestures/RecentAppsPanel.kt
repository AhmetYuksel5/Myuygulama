package com.ahmety.uygulama.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * "Son uygulamalar" eylemi: ayara göre Merkez'in paneli ya da sistemin ekranı.
 */
internal fun openRecents(service: AccessibilityService): Boolean =
    if (GestureSettings(service).ownRecents) {
        RecentAppsPanel.show(service)
    } else {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

/**
 * Merkez'in kendi son uygulamalar paneli.
 *
 * Bazı telefonlarda sistemin son uygulamalar ekranı kayıyor, sırayı
 * karıştırıyor. Bu panel sırayı "önceki uygulama" geçişinin tuttuğu
 * kayıttan alıyor: en son kullanılan başta, içinde olunan uygulama yok.
 * Her uygulamanın son ekranı [AppThumbnails]'ten geliyor; görüntüsü
 * olmayanda simge duruyor.
 *
 * İki görünüm: kayan büyük kartlar (sistemin ekranına benzer) ya da iki
 * sütunlu ızgara (aynı anda daha çok uygulama). Dokunmak geçiyor, uzun
 * basmak listeden çıkarıyor.
 */
internal object RecentAppsPanel {

    private var root: View? = null

    val showing: Boolean get() = root != null

    fun show(service: AccessibilityService): Boolean {
        if (root != null) {
            hide(service)
            return true
        }
        // İçinde olunan uygulamanın da güncel görüntüsü olsun; bir sonraki
        // açılışta listede o da olacak.
        AppThumbnails.capture(service)
        val apps = AppHistory.others(service, COUNT)
        if (apps.isEmpty()) {
            Toast.makeText(service, "Henüz son uygulama yok; uygulamalar arasında geçtikçe dolacak.", Toast.LENGTH_SHORT).show()
            return false
        }
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val metrics = service.resources.displayMetrics
        val d = metrics.density
        fun dp(v: Int) = (v * d).toInt()

        val frame = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    hide(service)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        // Arkadaki uygulama seçilmesin: koyu örtü, destekleyen telefonda bulanıklık da.
        frame.setBackgroundColor(Color.argb(SCRIM_ALPHA, 0, 0, 0))
        frame.setOnClickListener { hide(service) }

        val content: View = if (GestureSettings(service).recentsStyle == GestureSettings.RECENTS_GRID) {
            grid(service, apps, d)
        } else {
            cards(service, apps, d)
        }
        frame.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM
                setMargins(0, dp(48), 0, dp(36))
            },
        )

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            manager.isCrossWindowBlurEnabled
        ) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            params.blurBehindRadius = (BLUR_DP * d).toInt()
            frame.setBackgroundColor(Color.argb(BLURRED_SCRIM_ALPHA, 0, 0, 0))
        }
        val shown = runCatching {
            manager.addView(frame, params)
            root = frame
        }.isSuccess
        // Kenar şeritleri ve imleç panelin üstünde: kenardan geri hareketi
        // paneli kapatabilsin.
        if (shown) QuickCursorService.raise()
        return shown
    }

    fun hide(service: AccessibilityService) {
        val view = root ?: return
        root = null
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        runCatching { manager?.removeView(view) }
    }

    /** Kayan büyük kartlar: en son kullanılan solda, ekranın oranında. */
    private fun cards(service: AccessibilityService, apps: List<String>, d: Float): View {
        val metrics = service.resources.displayMetrics
        val width = (metrics.widthPixels * 0.56f).toInt()
        val ratio = metrics.heightPixels.toFloat() / metrics.widthPixels
        val height = (width * ratio).toInt().coerceAtMost((metrics.heightPixels * 0.58f).toInt())
        val row = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * d).toInt(), 0, (16 * d).toInt(), 0)
        }
        apps.forEach { pkg ->
            row.addView(
                tile(service, pkg, d, width, height, labelAbove = true),
                LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (12 * d).toInt()
                },
            )
        }
        return HorizontalScrollView(service).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(row)
        }
    }

    /** İki sütunlu ızgara: ekranların üst kısmı, aşağı kaydırılabilir. */
    private fun grid(service: AccessibilityService, apps: List<String>, d: Float): View {
        val metrics = service.resources.displayMetrics
        val gap = (12 * d).toInt()
        val width = (metrics.widthPixels - gap * 3) / 2
        val height = (width * 1.25f).toInt()
        val grid = GridLayout(service).apply {
            columnCount = 2
            setPadding(gap, 0, gap, 0)
        }
        apps.forEach { pkg ->
            grid.addView(
                tile(service, pkg, d, width, height, labelAbove = false),
                GridLayout.LayoutParams().apply {
                    this.width = width
                    setMargins(0, 0, if (grid.childCount % 2 == 0) gap else 0, gap)
                },
            )
        }
        val maxHeight = (metrics.heightPixels * 0.75f).toInt()
        return object : ScrollView(service) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
            }
        }.apply {
            isVerticalScrollBarEnabled = false
            addView(grid)
        }
    }

    /** Bir uygulama: başlık (simge + ad) ve son ekranının görüntüsü. */
    private fun tile(
        service: AccessibilityService,
        pkg: String,
        d: Float,
        width: Int,
        height: Int,
        labelAbove: Boolean,
    ): View {
        val header = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((2 * d).toInt(), (6 * d).toInt(), 0, (6 * d).toInt())
            addView(
                ImageView(service).apply { setImageDrawable(AppIcons.get(service, pkg)) },
                LinearLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt()),
            )
            addView(
                TextView(service).apply {
                    text = label(service, pkg)
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding((8 * d).toInt(), 0, 0, 0)
                },
            )
        }

        val shot = AppThumbnails.get(pkg)
        val preview = FrameLayout(service).apply {
            background = GradientDrawable().apply {
                cornerRadius = 16f * d
                setColor(Color.argb(255, 38, 40, 46))
            }
            clipToOutline = true
            if (shot != null) {
                addView(
                    ImageView(service).apply {
                        setImageBitmap(shot)
                        // Üstten hizalı: ekranın baş tarafı en çok bilgiyi taşıyor.
                        scaleType = ImageView.ScaleType.MATRIX
                        val scale = width.toFloat() / shot.width
                        imageMatrix = android.graphics.Matrix().apply { setScale(scale, scale) }
                    },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            } else {
                // Görüntü yok (henüz çekilmedi ya da banka gibi izin vermiyor).
                addView(
                    ImageView(service).apply { setImageDrawable(AppIcons.get(service, pkg)) },
                    FrameLayout.LayoutParams((64 * d).toInt(), (64 * d).toInt(), Gravity.CENTER),
                )
            }
        }

        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            if (labelAbove) addView(header)
            addView(preview, LinearLayout.LayoutParams(width, height))
            if (!labelAbove) addView(header)
            setOnClickListener {
                hide(service)
                val ok = launchPackage(service, pkg)
                if (ok) AppHistory.promote(service, pkg)
                GestureFeedback.vibrateOnce(service, if (ok) 15L else 8L)
            }
            // Kapatılan uygulamayı Android haber vermiyor; elle çıkarılıyor.
            setOnLongClickListener {
                AppHistory.forget(service, pkg)
                AppThumbnails.forget(pkg)
                GestureFeedback.vibrateOnce(service, 25L)
                hide(service)
                show(service)
                true
            }
        }
    }

    private fun label(context: Context, pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private const val COUNT = 10
    private const val SCRIM_ALPHA = 235
    private const val BLURRED_SCRIM_ALPHA = 190
    private const val BLUR_DP = 30
}
