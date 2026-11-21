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
 * Düzen: aynı anda dört kart, okuma sırasıyla (1 sol üst, 2 sağ üst…);
 * 1 içinde olunan uygulama. Eskiler aşağı kaydırınca geliyor. Kart boyu
 * yuvaya göre: yarım görünen kart yok.
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
    private var countLabel: TextView? = null
    private var settingsButton: TextView? = null
    private val cards = mutableListOf<View>()
    private var settingsMode = false

    // Açılış geçişinin durumu: panel ancak ikisi de olunca kalkıyor.
    private var launching: String? = null
    private var zoomDone = false
    private var windowSeen = false

    val showing: Boolean get() = root != null

    /** Panel açılırken içinde olunan uygulama. */
    private var current: String? = null

    fun show(service: AccessibilityService): Boolean {
        if (root != null) {
            if (launching == null) hide(service)
            return true
        }
        // İçinde olunan uygulama da listede: en yeni kart, "Açık" işaretli.
        current = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull()
            ?.takeIf { it != service.packageName && AppHistory.snapshot(service, 1).firstOrNull() == it }
        // Kartında güncel görüntüsü olsun.
        if (current != null) AppThumbnails.capture(service)
        val apps = listOfNotNull(current) + AppHistory.others(service, COUNT - if (current != null) 1 else 0)
        if (apps.isEmpty()) {
            Toast.makeText(service, "Henüz son uygulama yok; uygulamalar arasında geçtikçe dolacak.", Toast.LENGTH_SHORT).show()
            return false
        }
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val d = service.resources.displayMetrics.density
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
        windowManager = manager
        windowParams = params
        background(service, manager, frame, params)

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
            blurView = frame
        }.isSuccess
        if (shown) {
            // Kenar şeritleri ve imleç panelin üstünde: kenardan geri hareketi çalışsın.
            QuickCursorService.raise()
            if (animate(service)) {
                enter(frame, stack, d)
                blurTo(blurTarget, SCRIM_MS, STANDARD)
            } else {
                blurTo(blurTarget, 0L, STANDARD)
            }
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
        blurTo(0, if (animate(service)) EXIT_MS else 0L, EXIT)
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
        service: AccessibilityService,
        manager: WindowManager,
        frame: FrameLayout,
        params: WindowManager.LayoutParams,
    ) {
        blurTarget = 0
        val blurred = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.isCrossWindowBlurEnabled
        if (blurred) {
            // Bulanıklık sıfırdan başlıyor; açılışta yavaşça artıyor. Sistem
            // çizdiği için panelin solmasına uymuyor, ayrıca canlandırılıyor.
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            params.blurBehindRadius = 0
            blurTarget = (BLUR_DP * service.resources.displayMetrics.density).toInt()
            frame.background = gradient(0x59101418, 0xB30B0D10.toInt())
            return
        }
        // Bulanıklık yoksa o anki ekranın bulanık kopyası. Görüntü gelene kadar
        // koyu düz zemin; elde kalan eski bir görüntü gösterilmiyor.
        frame.setBackgroundColor(0xE6121418.toInt())
        AppThumbnails.capture(service) { shot ->
            if (shot == null || root !== frame) return@capture
            val image = ImageView(service).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setImageBitmap(shot)
                    setRenderEffect(RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP))
                } else {
                    // Ucuz bulanıklık: çok küçük görüntü, büyütürken süzülüyor.
                    setImageBitmap(
                        Bitmap.createScaledBitmap(
                            shot,
                            (shot.width / 24).coerceAtLeast(1),
                            (shot.height / 24).coerceAtLeast(1),
                            true,
                        ),
                    )
                }
                alpha = 0f
            }
            val shade = View(service).apply {
                background = gradient(0xB3101318.toInt(), 0xE60B0D10.toInt())
                alpha = 0f
            }
            frame.addView(image, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            frame.addView(shade, 1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            image.animate().alpha(1f).setDuration(150).start()
            shade.animate().alpha(1f).setDuration(150).start()
        }
    }

    private var windowManager: WindowManager? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var blurTarget = 0
    private var blurAnimator: ValueAnimator? = null
    private var blurView: View? = null

    /** Pencere arkası bulanıklığını yumuşakça değiştirir; bulanıklık yoksa bir şey yapmaz. */
    private fun blurTo(target: Int, duration: Long, interpolator: android.animation.TimeInterpolator) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val params = windowParams ?: return
        if (params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND == 0) return
        val view = blurView ?: return
        blurAnimator?.cancel()
        if (duration <= 0L) {
            params.blurBehindRadius = target
            runCatching { windowManager?.updateViewLayout(view, params) }
            return
        }
        blurAnimator = ValueAnimator.ofInt(params.blurBehindRadius, target).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener {
                params.blurBehindRadius = it.animatedValue as Int
                runCatching { windowManager?.updateViewLayout(view, params) }
            }
            start()
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
        val titles = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(service).apply {
                    text = "Son uygulamalar"
                    setTextColor(0xCCFFFFFF.toInt())
                    textSize = 15f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                },
            )
            // Kaç uygulama olduğu: dörtten fazlası varsa kaydırılabildiği görülsün.
            val count = TextView(service).apply {
                setTextColor(0x99FFFFFF.toInt())
                textSize = 12f
            }
            countLabel = count
            addView(count)
        }
        row.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
     * İki sütunlu kartlar, okuma sırasıyla; görünen alan iki sıra.
     */
    private fun buildGrid(service: AccessibilityService, apps: List<String>, d: Float): View {
        cards.clear()
        val metrics = service.resources.displayMetrics
        val gap = dp(d, 16)
        // Kart tam telefon ekranı oranında: ekran görüntüsü kesilmeden sığıyor.
        // Önceden kart ekrandan kısaydı ve görüntünün altı kırpılıyordu. İki
        // sıra ekranın %62'sini geçmesin diye boy önce, en ona göre.
        val aspect = metrics.heightPixels.toFloat() / metrics.widthPixels
        val height = ((metrics.heightPixels * 0.62f).toInt() - gap) / 2
        val width = minOf((metrics.widthPixels - dp(d, 32) - gap) / 2, (height / aspect).toInt())

        fun row(left: String?, right: String?) = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(slot(service, left, d, width, height), LinearLayout.LayoutParams(width, height))
            addView(Space(service), LinearLayout.LayoutParams(gap, 1))
            addView(slot(service, right, d, width, height), LinearLayout.LayoutParams(width, height))
        }

        // Okuma sırası: 1 sol üst (açık uygulama), 2 sağ üst, 3 ve 4 altında;
        // aşağı kaydırınca 5, 6…
        val rows = (apps.size + 1) / 2
        val list = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            for (r in 0 until rows) {
                if (r > 0) addView(Space(service), LinearLayout.LayoutParams(1, gap))
                addView(row(apps.getOrNull(r * 2), apps.getOrNull(r * 2 + 1)))
            }
        }
        // Aynı anda iki sıra, yani dört kart görünüyor; fazlası kaydırılarak.
        val visible = minOf(rows, VISIBLE_ROWS)
        val viewport = visible * height + (visible - 1) * gap
        val scroll = object : android.widget.ScrollView(service) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(viewport, MeasureSpec.EXACTLY))
            }
        }
        countLabel?.text = when {
            apps.size > VISIBLE_ROWS * 2 -> "${apps.size} uygulama · aşağı kaydır"
            else -> "${apps.size} uygulama"
        }
        return scroll.apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun slot(service: AccessibilityService, pkg: String?, d: Float, width: Int, height: Int): View =
        if (pkg == null) Space(service) else card(service, pkg, d, width, height)

    /** Kart: uygulamanın son ekranı, üstte karartılmış şeritte simge ve ad. */
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
            // Ad kartın tepesinde: hangi uygulama olduğu ilk bakışta görülsün.
            gravity = Gravity.CENTER_VERTICAL or Gravity.TOP
            setPadding(dp(d, 12), dp(d, 12), dp(d, 12), 0)
            background = gradient(0xB3000000.toInt(), 0x00000000)
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
        card.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(d, 64), Gravity.TOP))
        if (pkg == current) {
            card.addView(
                TextView(service).apply {
                    text = "Açık"
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    setPadding(dp(d, 10), dp(d, 4), dp(d, 10), dp(d, 4))
                    background = GradientDrawable().apply {
                        cornerRadius = 12f * d
                        setColor(ACCENT)
                    }
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    // Ad üstte olduğu için işaret alt köşede.
                    Gravity.BOTTOM or Gravity.END,
                ).apply { setMargins(0, 0, dp(d, 10), dp(d, 10)) },
            )
        }

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
                // Açık uygulamaya dokunmak yalnız paneli kapatıyor: zaten orada.
                if (pkg == current) hide(service) else open(service, pkg, card, label, shot)
            }
        }
        // Kapatılan uygulamayı Android haber vermiyor; elle çıkarılıyor.
        card.setOnLongClickListener {
            remove(service, pkg, card)
            true
        }
        card.setOnTouchListener(Swipe(service, pkg, card))
        cards += card
        return card
    }

    // ---- Hareket ----

    /** Açılış: arka plan yavaşça, kartlar en yeniden başlayarak süzülüyor. */
    private fun enter(frame: View, stack: LinearLayout, d: Float) {
        frame.alpha = 0f
        frame.animate().alpha(1f).setDuration(SCRIM_MS).setInterpolator(STANDARD).start()
        // Kartlar sırayla, 1 numaradan başlayarak geliyor.
        cards.forEachIndexed { i, card ->
            card.alpha = 0f
            card.translationY = 24f * d
            card.scaleX = 0.94f
            card.scaleY = 0.94f
            card.animate()
                .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(STAGGER_MS * i.coerceAtMost(3))
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
        // Görüntü ekranı kaplarken bulanıklık kalkıyor; yoksa solma sırasında
        // yeni uygulama bir an bulanık görünüp birden netleşiyordu.
        blurAnimator?.cancel()
        windowParams?.let { params ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) params.blurBehindRadius = 0
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            runCatching { manager?.updateViewLayout(view, params) }
        }
        // Bayat görüntü yerine uygulamanın kendisi: kısa bir solmayla.
        view.animate().alpha(0f).setDuration(120).setInterpolator(null)
            .withEndAction { runCatching { manager?.removeView(view) } }.start()
    }

    /** Uzun basma: kart uçarak çıkıyor, yerine kalanlar diziliyor; panel yeniden açılmıyor. */
    private fun remove(service: AccessibilityService, pkg: String, card: View, fling: Int = 0) {
        val d = service.resources.displayMetrics.density
        GestureFeedback.vibrateOnce(service, 25L)
        AppHistory.forget(service, pkg)
        AppThumbnails.forget(pkg)
        close(service, pkg)
        val leave = if (fling != 0) {
            // Yana fırlatıldı: parmağın gittiği yönde ekrandan çıkıyor.
            val screen = service.resources.displayMetrics.widthPixels.toFloat()
            card.animate().translationX(fling * screen).rotation(fling * 14f).alpha(0f)
                .setDuration(FLING_MS).setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
        } else {
            card.animate().scaleX(0.9f).scaleY(0.9f).translationY(-24f * d).alpha(0f)
                .setDuration(200).setInterpolator(EXIT)
        }
        leave
            .withEndAction {
                val apps = listOfNotNull(current?.takeIf { it != pkg }) +
                    AppHistory.others(service, COUNT - if (current != null && current != pkg) 1 else 0)
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

    /**
     * Uygulamayı kapatır: arka plandaysa sistemden işlemini sonlandırmasını
     * istiyor. Android başka bir uygulamayı tamamen öldürmeye izin vermiyor;
     * bu, sistemin son uygulamalar ekranında kaydırıp atmakla aynı etki:
     * arka planda tuttuğu bellek boşalıyor. Öndeki uygulama sonlandırılamıyor.
     */
    private fun close(service: Context, pkg: String) {
        if (pkg == current) return
        runCatching {
            (service.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                .killBackgroundProcesses(pkg)
        }
    }

    /**
     * Kartı yana sürükleyip fırlatmak. Kart parmağı izliyor, uzaklaştıkça
     * dönüp soluyor; yeterince uzağa ya da hızlı atılırsa kapanıyor, değilse
     * yerine dönüyor. Yatay hareket başlamadan dokunma ve dikey kaydırma
     * eskisi gibi kalıyor.
     */
    private class Swipe(
        private val service: AccessibilityService,
        private val pkg: String,
        private val card: View,
    ) : View.OnTouchListener {
        private val slop = android.view.ViewConfiguration.get(service).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var swiping = false
        private var velocity: android.view.VelocityTracker? = null

        override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
            if (launching != null) return false
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    swiping = false
                    velocity?.recycle()
                    velocity = android.view.VelocityTracker.obtain().apply { addMovement(event) }
                }

                android.view.MotionEvent.ACTION_MOVE -> {
                    velocity?.addMovement(event)
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!swiping && kotlin.math.abs(dx) > slop && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        swiping = true
                        card.cancelLongPress()
                        card.isPressed = false
                        // Dikey kaydırma bu hareketi kendine almasın.
                        card.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (swiping) {
                        val progress = (kotlin.math.abs(dx) / card.width).coerceIn(0f, 1f)
                        card.translationX = dx
                        card.rotation = dx / card.width * 10f
                        card.alpha = 1f - progress * 0.7f
                        return true
                    }
                }

                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    val tracker = velocity
                    velocity = null
                    if (!swiping) {
                        tracker?.recycle()
                        return false
                    }
                    swiping = false
                    tracker?.addMovement(event)
                    tracker?.computeCurrentVelocity(1000)
                    val speed = tracker?.xVelocity ?: 0f
                    tracker?.recycle()
                    val dx = card.translationX
                    val d = service.resources.displayMetrics.density
                    val flung = event.actionMasked == android.view.MotionEvent.ACTION_UP &&
                        (kotlin.math.abs(dx) > card.width * 0.35f || kotlin.math.abs(speed) > 1200f * d)
                    if (flung) {
                        val direction = if ((if (kotlin.math.abs(speed) > 1200f * d) speed else dx) > 0) 1 else -1
                        remove(service, pkg, card, direction)
                    } else {
                        // Yerine yaylanarak dönüyor.
                        card.animate().translationX(0f).rotation(0f).alpha(1f)
                            .setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(1.2f)).start()
                    }
                    return true
                }
            }
            return false
        }
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

    private const val COUNT = 10
    private const val VISIBLE_ROWS = 2
    private const val ACCENT = 0xFF3D7BFF.toInt()
    private const val BLUR_DP = 40
    private const val SCRIM_MS = 280L
    private const val CARD_MS = 380L
    private const val STAGGER_MS = 30L
    private const val EXIT_MS = 180L
    private const val FLING_MS = 240L
    private const val ZOOM_MS = 320L
    private const val LAUNCH_DELAY_MS = 80L
    private const val LAUNCH_TIMEOUT_MS = 700L

    // Material'in yumuşak eğrileri.
    private val STANDARD = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val EMPHASIZED = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private val EXIT = PathInterpolator(0.3f, 0f, 1f, 1f)
}
