package com.ahmety.uygulama.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
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
 * karıştırıyor. Sıra "önceki uygulama" geçişinin tuttuğu kayıttan geliyor;
 * her kartta uygulamanın son ekranı ([AppThumbnails]) duruyor.
 *
 * Düzen tek elle kullanım için: kaydırma yok, en fazla dört kart, en son
 * kullanılan sağ altta başparmağın dibinde. Önceki sürüm on uygulamayı
 * kaydırarak gösteriyordu; en yenisi ekranın tepesinde kalıyor, yarım
 * kartlar karmaşık duruyordu.
 *
 * Dokunulan kart büyüyerek ekranı kaplıyor ve uygulama onun arkasında,
 * sistem animasyonu olmadan açılıyor; panel ancak uygulamanın penceresi
 * gelince kalkıyor. Önceden panel önce soluyor, sonra uygulama kendi
 * animasyonuyla geliyordu; arada eski uygulama görünüp titriyordu.
 */
internal object RecentAppsPanel {

    private val handler = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var column: LinearLayout? = null
    private var grid: View? = null
    private var hint: TextView? = null
    private var settingsButton: TextView? = null
    private val cards = mutableListOf<View>()
    private var settingsMode = false

    // Açılış geçişinin durumu: panel ancak ikisi de olunca kalkıyor.
    private var launching: String? = null
    private var zoomDone = false
    private var windowSeen = false

    val showing: Boolean get() = root != null

    fun show(service: AccessibilityService): Boolean {
        if (root != null) {
            if (launching == null) hide(service)
            return true
        }
        // İçinde olunan uygulamanın güncel görüntüsü: bir sonraki açılışta
        // o da listede olacak, arka planda da bulanık hâli kullanılıyor.
        AppThumbnails.capture(service)
        val apps = AppHistory.others(service, COUNT)
        if (apps.isEmpty()) {
            Toast.makeText(service, "Henüz son uygulama yok; uygulamalar arasında geçtikçe dolacak.", Toast.LENGTH_SHORT).show()
            return false
        }
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val d = service.resources.displayMetrics.density
        val current = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull()
        settingsMode = false
        launching = null

        val frame = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    hide(service)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        frame.setOnClickListener { hide(service) }

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        background(service, manager, frame, params, current)

        val stack = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = false
        }
        column = stack
        hint = TextView(service).apply {
            text = "Ayarlarını açmak istediğin uygulamaya dokun"
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(d, 8))
            visibility = View.GONE
        }
        stack.addView(hint)
        stack.addView(header(service, d))
        val built = buildGrid(service, apps, d)
        grid = built
        stack.addView(built)

        frame.addView(
            stack,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ).apply { setMargins(dp(d, 16), 0, dp(d, 16), dp(d, 24) + navBar(service)) },
        )

        val shown = runCatching {
            manager.addView(frame, params)
            root = frame
        }.isSuccess
        if (shown) {
            // Kenar şeritleri ve imleç panelin üstünde: kenardan geri hareketi çalışsın.
            QuickCursorService.raise()
            if (animate(service)) enter(frame, stack, d)
        }
        return shown
    }

    /** Servisten: öne bir pencere geldi; açılış geçişi bunu bekliyor. */
    fun onWindowChanged(pkg: String?) {
        if (pkg != null && pkg == launching) {
            windowSeen = true
            // Bir kare daha bekle: uygulamanın ilk karesi çizilmiş olsun.
            handler.post { finishLaunch() }
        }
    }

    fun hide(service: AccessibilityService) {
        val view = root ?: return
        if (launching != null) return
        root = null
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val remove = { runCatching { manager?.removeView(view) } }
        if (!animate(service)) {
            remove()
            return
        }
        val d = service.resources.displayMetrics.density
        column?.animate()?.alpha(0f)?.translationY(16f * d)?.setDuration(EXIT_MS)?.setInterpolator(EXIT)?.start()
        view.animate().alpha(0f).setDuration(EXIT_MS + 20).setInterpolator(EXIT).withEndAction { remove() }.start()
    }

    // ---- Görünüm ----

    /**
     * Arka plan: buzlu cam. Telefon pencere arkası bulanıklığı destekliyorsa
     * o; desteklemiyorsa içinde olunan ekranın bulanık bir kopyası. Her ikisinin
     * üstünde yukarıdan aşağı koyulaşan bir renk geçişi var; düz siyah örtü
     * kötü görünüyordu ama arkadaki uygulama da seçilmemeli.
     */
    private fun background(
        service: Context,
        manager: WindowManager,
        frame: FrameLayout,
        params: WindowManager.LayoutParams,
        current: String?,
    ) {
        val blurred = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.isCrossWindowBlurEnabled
        if (blurred) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            params.blurBehindRadius = (BLUR_DP * service.resources.displayMetrics.density).toInt()
            frame.background = gradient(0x59101418, 0xB30B0D10.toInt())
            return
        }
        val shot = current?.let { AppThumbnails.get(it) }
        if (shot != null) {
            frame.addView(
                ImageView(service).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setImageBitmap(shot)
                        setRenderEffect(RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP))
                    } else {
                        // Eski sürümde ucuz bulanıklık: çok küçültüp süzerek büyütmek.
                        val tiny = Bitmap.createScaledBitmap(shot, (shot.width / 24).coerceAtLeast(1), (shot.height / 24).coerceAtLeast(1), true)
                        setImageBitmap(Bitmap.createScaledBitmap(tiny, shot.width, shot.height, true))
                    }
                },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            frame.addView(
                View(service).apply { background = gradient(0xB3101318.toInt(), 0xE60B0D10.toInt()) },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        } else {
            frame.setBackgroundColor(0xE6121418.toInt())
        }
    }

    private fun gradient(top: Int, bottom: Int) =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))

    /** Başlık ve düğmeler: kartların hemen üstünde, başparmağa yakın. */
    private fun header(service: AccessibilityService, d: Float): View {
        val row = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(d, 4), 0, 0, dp(d, 16))
        }
        row.addView(
            TextView(service).apply {
                text = "Son uygulamalar"
                setTextColor(0xCCFFFFFF.toInt())
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        val settings = pill(service, d, "⚙  Ayarlar") { toggleSettings(d) }
        settingsButton = settings
        row.addView(settings)
        row.addView(Space(service), LinearLayout.LayoutParams(dp(d, 8), 1))
        row.addView(
            // Android kapatılan uygulamayı haber vermiyor; liste kendiliğinden boşalmıyor.
            pill(service, d, "Temizle") {
                AppHistory.clearOthers(service)
                Toast.makeText(service, "Liste temizlendi.", Toast.LENGTH_SHORT).show()
                hide(service)
            },
        )
        return row
    }

    private fun pill(service: Context, d: Float, label: String, onClick: () -> Unit) =
        TextView(service).apply {
            text = label
            setTextColor(0xE6FFFFFF.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            minHeight = dp(d, 48)
            setPadding(dp(d, 16), 0, dp(d, 16), 0)
            background = pillBackground(d, active = false)
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun pillBackground(d: Float, active: Boolean) = GradientDrawable().apply {
        cornerRadius = 24f * d
        setColor(if (active) ACCENT else 0x24FFFFFF)
    }

    private fun toggleSettings(d: Float) {
        settingsMode = !settingsMode
        settingsButton?.apply {
            background = pillBackground(d, settingsMode)
            setTextColor(if (settingsMode) Color.WHITE else 0xE6FFFFFF.toInt())
        }
        hint?.visibility = if (settingsMode) View.VISIBLE else View.GONE
        cards.forEach { card -> (card.tag as? GradientDrawable)?.setStroke(
            dp(d, if (settingsMode) 2 else 1),
            if (settingsMode) ACCENT else 0x1FFFFFFF,
        ) }
    }

    /**
     * 2×2 kart: 1 sağ alt, 2 sol alt, 3 sağ üst, 4 sol üst. İki ve daha az
     * uygulamada yalnız alt sıra.
     */
    private fun buildGrid(service: AccessibilityService, apps: List<String>, d: Float): View {
        cards.clear()
        val metrics = service.resources.displayMetrics
        val gap = dp(d, 16)
        val width = (metrics.widthPixels - dp(d, 32) - gap) / 2
        // Telefon ekranı oranında; küçük ekranda iki sıra ekranın %62'sini geçmesin.
        val height = (width * 1.55f).toInt().coerceAtMost(((metrics.heightPixels * 0.62f).toInt() - gap) / 2)

        fun row(left: String?, right: String?) = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(slot(service, left, d, width, height), LinearLayout.LayoutParams(width, height))
            addView(Space(service), LinearLayout.LayoutParams(gap, 1))
            addView(slot(service, right, d, width, height), LinearLayout.LayoutParams(width, height))
        }

        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            if (apps.size > 2) {
                addView(row(apps.getOrNull(3), apps.getOrNull(2)))
                addView(Space(service), LinearLayout.LayoutParams(1, gap))
            }
            addView(row(apps.getOrNull(1), apps.getOrNull(0)))
        }
    }

    private fun slot(service: AccessibilityService, pkg: String?, d: Float, width: Int, height: Int): View =
        if (pkg == null) Space(service) else card(service, pkg, d, width, height)

    /** Kart: uygulamanın son ekranı, altta karartılmış şeritte simge ve ad. */
    private fun card(service: AccessibilityService, pkg: String, d: Float, width: Int, height: Int): View {
        val radius = 24f * d
        val shape = GradientDrawable().apply {
            cornerRadius = radius
            setColor(0xFF1E2127.toInt())
            setStroke(dp(d, 1), 0x1FFFFFFF)
        }
        val shot = AppThumbnails.get(pkg)
        val card = FrameLayout(service).apply {
            background = shape
            tag = shape
            clipToOutline = true
            isClickable = true
        }
        if (shot != null) {
            card.addView(
                ImageView(service).apply {
                    setImageBitmap(shot)
                    // Üstten hizalı: ekranın baş tarafı en çok bilgiyi taşıyor.
                    scaleType = ImageView.ScaleType.MATRIX
                    val scale = width.toFloat() / shot.width
                    imageMatrix = Matrix().apply { setScale(scale, scale) }
                },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        } else {
            // Görüntü yok (henüz çekilmedi ya da banka gibi izin vermiyor).
            card.addView(
                ImageView(service).apply { setImageDrawable(AppIcons.get(service, pkg)) },
                FrameLayout.LayoutParams(dp(d, 56), dp(d, 56), Gravity.CENTER_HORIZONTAL).apply {
                    topMargin = (height * 0.4f).toInt() - dp(d, 28)
                },
            )
        }
        val label = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.BOTTOM
            setPadding(dp(d, 12), 0, dp(d, 12), dp(d, 12))
            background = gradient(0x00000000, 0xB3000000.toInt())
            addView(
                ImageView(service).apply { setImageDrawable(AppIcons.get(service, pkg)) },
                LinearLayout.LayoutParams(dp(d, 20), dp(d, 20)),
            )
            addView(
                TextView(service).apply {
                    text = label(service, pkg)
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(dp(d, 8), 0, 0, 0)
                },
            )
        }
        card.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(d, 64), Gravity.BOTTOM))

        card.setOnClickListener {
            if (launching != null) return@setOnClickListener
            if (settingsMode) {
                hide(service)
                runCatching {
                    service.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            } else {
                open(service, pkg, card, label, shot)
            }
        }
        // Kapatılan uygulamayı Android haber vermiyor; elle çıkarılıyor.
        card.setOnLongClickListener {
            remove(service, pkg, card)
            true
        }
        cards += card
        return card
    }

    // ---- Hareket ----

    /** Açılış: arka plan yavaşça, kartlar en yeniden başlayarak süzülüyor. */
    private fun enter(frame: View, stack: LinearLayout, d: Float) {
        frame.alpha = 0f
        frame.animate().alpha(1f).setDuration(SCRIM_MS).setInterpolator(STANDARD).start()
        // Kartlar en yeniden (sağ alt) başlayarak geliyor.
        cards.reversed().forEachIndexed { i, card ->
            card.alpha = 0f
            card.translationY = 24f * d
            card.scaleX = 0.94f
            card.scaleY = 0.94f
            card.animate()
                .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(STAGGER_MS * i)
                .setDuration(CARD_MS)
                .setInterpolator(EMPHASIZED)
                .start()
        }
        for (i in 0 until stack.childCount - 1) {
            val view = stack.getChildAt(i)
            view.alpha = 0f
            view.animate().alpha(1f).setStartDelay(120).setDuration(200).start()
        }
    }

    /**
     * Dokunulan kartın görüntüsü büyüyerek tam ekran oluyor; uygulama onun
     * arkasında, sistem animasyonu olmadan açılıyor. Panel uygulamanın
     * penceresi gelince kalkıyor, arada eski uygulama hiç görünmüyor.
     */
    private fun open(service: AccessibilityService, pkg: String, card: View, label: View, shot: Bitmap?) {
        val frame = root ?: return
        val d = service.resources.displayMetrics.density
        GestureFeedback.vibrateOnce(service, 10L)
        launching = pkg
        zoomDone = false
        windowSeen = false

        // Geri kalanlar soluyor.
        cards.filter { it !== card }.forEach {
            it.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setStartDelay(0).setDuration(120).start()
        }
        for (i in 0 until (column?.childCount ?: 1) - 1) column?.getChildAt(i)?.animate()?.alpha(0f)?.setDuration(120)?.start()
        label.animate().alpha(0f).setDuration(120).start()

        // Kartın üstüne, ekranı kaplayacak bir "hayalet" görüntü.
        val from = IntArray(2).also { card.getLocationInWindow(it) }
        val start = RectF(from[0].toFloat(), from[1].toFloat(), from[0] + card.width.toFloat(), from[1] + card.height.toFloat())
        val end = RectF(0f, 0f, frame.width.toFloat(), frame.height.toFloat())
        val now = RectF(start)
        var radius = 24f * d
        val ghost = ImageView(service).apply {
            if (shot != null) {
                setImageBitmap(shot)
                scaleType = ImageView.ScaleType.MATRIX
            } else {
                setBackgroundColor(0xFF121418.toInt())
                setImageDrawable(AppIcons.get(service, pkg))
                scaleType = ImageView.ScaleType.CENTER
            }
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(now.left.toInt(), now.top.toInt(), now.right.toInt(), now.bottom.toInt(), radius)
                }
            }
            clipToOutline = true
        }
        frame.addView(ghost, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fun place(t: Float) {
            now.set(
                lerp(start.left, end.left, t), lerp(start.top, end.top, t),
                lerp(start.right, end.right, t), lerp(start.bottom, end.bottom, t),
            )
            radius = lerp(24f * d, 0f, t)
            if (shot != null) {
                val scale = now.width() / shot.width
                ghost.imageMatrix = Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(now.left, now.top)
                }
            }
            ghost.invalidateOutline()
        }
        place(0f)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (animate(service)) ZOOM_MS else 0L
            interpolator = STANDARD
            addUpdateListener { place(it.animatedValue as Float) }
            withEnd {
                zoomDone = true
                finishLaunch()
            }
            start()
        }

        // Uygulama yakınlaşmayla aynı anda başlıyor; açılışı geçişle örtüşsün.
        handler.postDelayed({
            val options = runCatching { ActivityOptions.makeCustomAnimation(service, 0, 0).toBundle() }.getOrNull()
            val ok = launchPackage(service, pkg, options)
            if (ok) {
                AppHistory.promote(service, pkg)
            } else {
                launching = null
                windowSeen = true
                zoomDone = true
                hide(service)
            }
        }, LAUNCH_DELAY_MS)
        // Pencere haberi gelmezse de panel kalksın.
        handler.postDelayed({
            windowSeen = true
            finishLaunch()
        }, LAUNCH_TIMEOUT_MS)
        this.service = java.lang.ref.WeakReference(service)
    }

    private var service: java.lang.ref.WeakReference<AccessibilityService>? = null

    private fun finishLaunch() {
        if (launching == null || !zoomDone || !windowSeen) return
        launching = null
        handler.removeCallbacksAndMessages(null)
        val view = root ?: return
        root = null
        val manager = service?.get()?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        // Bayat görüntü yerine uygulamanın kendisi: kısa bir solmayla.
        view.animate().alpha(0f).setDuration(120).setInterpolator(null)
            .withEndAction { runCatching { manager?.removeView(view) } }.start()
    }

    /** Uzun basma: kart uçarak çıkıyor, yerine kalanlar diziliyor; panel yeniden açılmıyor. */
    private fun remove(service: AccessibilityService, pkg: String, card: View) {
        val d = service.resources.displayMetrics.density
        GestureFeedback.vibrateOnce(service, 25L)
        AppHistory.forget(service, pkg)
        AppThumbnails.forget(pkg)
        card.animate().scaleX(0.9f).scaleY(0.9f).translationY(-24f * d).alpha(0f)
            .setDuration(200).setInterpolator(EXIT)
            .withEndAction {
                val apps = AppHistory.others(service, COUNT)
                if (apps.isEmpty()) {
                    hide(service)
                    return@withEndAction
                }
                val stack = column ?: return@withEndAction
                val old = grid ?: return@withEndAction
                val index = stack.indexOfChild(old)
                stack.removeView(old)
                val fresh = buildGrid(service, apps, d)
                grid = fresh
                stack.addView(fresh, index)
                if (settingsMode) {
                    settingsMode = false
                    toggleSettings(d)
                }
                fresh.alpha = 0f
                fresh.animate().alpha(1f).setDuration(300).setInterpolator(EMPHASIZED).start()
            }.start()
    }

    // ---- Yardımcılar ----

    private fun ValueAnimator.withEnd(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun dp(d: Float, v: Int) = (v * d).toInt()

    private fun navBar(context: Context): Int {
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun label(context: Context, pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Sistem animasyonları kapatıldıysa panel de kıpırdamasın. */
    private fun animate(context: Context): Boolean = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }.getOrDefault(true)

    private const val COUNT = 4
    private const val ACCENT = 0xFF3D7BFF.toInt()
    private const val BLUR_DP = 40
    private const val SCRIM_MS = 280L
    private const val CARD_MS = 380L
    private const val STAGGER_MS = 30L
    private const val EXIT_MS = 180L
    private const val ZOOM_MS = 320L
    private const val LAUNCH_DELAY_MS = 80L
    private const val LAUNCH_TIMEOUT_MS = 700L

    // Material'in yumuşak eğrileri.
    private val STANDARD = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val EMPHASIZED = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private val EXIT = PathInterpolator(0.3f, 0f, 1f, 1f)
}
