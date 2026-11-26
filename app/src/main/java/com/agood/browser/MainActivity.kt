package com.agood.browser

import android.app.Dialog
import android.app.DownloadManager
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.PathParser
import java.io.ByteArrayInputStream
import java.net.URLEncoder

private const val P_BACK = "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"
private const val P_FORWARD = "M12,4l-1.41,1.41L16.17,11H4v2h12.17l-5.58,5.59L12,20l8,-8z"
private const val P_HOME = "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z"
private const val P_MENU = "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"
private const val P_REFRESH = "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"
private const val P_CLOSE = "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"

/** Draws a 24x24 Material-style path scaled to fit its bounds. */
class PathIcon(pathData: String, tint: Int) : Drawable() {
    private val path = PathParser.createPathFromPathData(pathData)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()) / 24f
        canvas.save()
        canvas.translate(b.left + (b.width() - 24f * s) / 2f, b.top + (b.height() - 24f * s) / 2f)
        canvas.scale(s, s)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class MainActivity : AppCompatActivity() {
    private val desktopUA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val blocked = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "adservice.google.com", "scorecardresearch.com", "adnxs.com", "taboola.com", "outbrain.com",
        "criteo.com", "criteo.net", "hotjar.com", "mc.yandex.ru", "amazon-adsystem.com", "adsrvr.org",
        "rubiconproject.com", "pubmatic.com", "moatads.com", "popads.net", "propellerads.com",
        "mgid.com", "adsterra.com", "clarity.ms", "yektanet.com", "tapin.ir", "mediaad.org"
    )

    private class Row(
        val text: String,
        val sub: String? = null,
        val onClick: () -> Unit,
        val onClose: (() -> Unit)? = null
    )

    private val tabs = mutableListOf<WebView>()
    private val history = ArrayList<String>()
    private var cur = -1
    private var desktop = false
    private var adblock = true
    private var noImages = false
    private var loading = false
    private var isDark = false
    private var cBg = 0
    private var cBar = 0
    private var cPill = 0
    private var cText = 0
    private var cSub = 0
    private var cAccent = 0

    private lateinit var root: FrameLayout
    private lateinit var container: FrameLayout
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var tabCount: TextView
    private lateinit var reloadBtn: ImageView
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private val prefs by lazy { getSharedPreferences("agood_browser", MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        initColors()
        adblock = prefs.getBoolean("adblock", true)
        noImages = prefs.getBoolean("noimg", false)
        desktop = prefs.getBoolean("desktop", false)
        history.addAll(readList("hist"))
        buildUi()
        applyBars()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val w = tabs.getOrNull(cur)
                when {
                    customView != null -> hideCustom()
                    w != null && w.canGoBack() -> w.goBack()
                    tabs.size > 1 -> closeTab(cur)
                    else -> finish()
                }
            }
        })
        val u = intent?.data?.toString()
        if (u != null) newTab(u) else openHomeTab()
    }

    private fun initColors() {
        if (isDark) {
            cBg = 0xFF121212.toInt(); cBar = 0xFF1E1E1E.toInt(); cPill = 0xFF2D2F31.toInt()
            cText = 0xFFE8EAED.toInt(); cSub = 0xFF9AA0A6.toInt(); cAccent = 0xFF4DB6FF.toInt()
        } else {
            cBg = 0xFFFFFFFF.toInt(); cBar = 0xFFFFFFFF.toInt(); cPill = 0xFFF1F3F4.toInt()
            cText = 0xFF202124.toInt(); cSub = 0xFF5F6368.toInt(); cAccent = 0xFF1E88E5.toInt()
        }
    }

    @Suppress("DEPRECATION")
    private fun applyBars() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = cBar
        window.navigationBarColor = cBar
        window.decorView.systemUiVisibility = if (isDark) 0 else
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
    }

    private fun attrRes(a: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(a, tv, true)
        return tv.resourceId
    }

    private fun icon(path: String, onClick: () -> Unit) = ImageView(this).apply {
        setImageDrawable(PathIcon(path, cText))
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(14), dp(14), dp(14), dp(14))
        setBackgroundResource(attrRes(android.R.attr.selectableItemBackgroundBorderless))
        setOnClickListener { onClick() }
    }

    private fun buildUi() {
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(cBg) }

        // ----- top bar: rounded address pill -----
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(cBar); setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(cPill); cornerRadius = dp(22).toFloat() }
            setPadding(dp(16), 0, dp(4), 0)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(44))
        }
        urlBar = EditText(this).apply {
            setSingleLine(); hint = "جستجو یا آدرس سایت"; textSize = 15f
            setTextColor(cText); setHintTextColor(cSub); background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
            setOnEditorActionListener { v, _, _ ->
                go(v.text.toString()); v.clearFocus(); hideKeyboard(v); true
            }
        }
        reloadBtn = icon(P_REFRESH) {
            val w = tabs[cur]
            if (loading) w.stopLoading() else w.reload()
        }
        reloadBtn.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        reloadBtn.setPadding(dp(10), dp(10), dp(10), dp(10))
        pill.addView(urlBar)
        pill.addView(reloadBtn)
        top.addView(pill)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.INVISIBLE
            progressTintList = ColorStateList.valueOf(cAccent)
            progressBackgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(2))
        }
        container = FrameLayout(this)

        // ----- bottom navigation bar -----
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(cBar)
        }
        fun slot(v: View) = bottom.addView(v, LinearLayout.LayoutParams(0, dp(52), 1f))
        slot(icon(P_BACK) { tabs[cur].let { if (it.canGoBack()) it.goBack() } })
        slot(icon(P_FORWARD) { tabs[cur].let { if (it.canGoForward()) it.goForward() } })
        slot(icon(P_HOME) { loadHome(tabs[cur]) })
        tabCount = TextView(this).apply {
            text = "1"; gravity = Gravity.CENTER; textSize = 12f
            setTextColor(cText); typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { setStroke(dp(2), cText); cornerRadius = dp(6).toFloat() }
        }
        val tabWrap = FrameLayout(this).apply {
            addView(tabCount, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            setBackgroundResource(attrRes(android.R.attr.selectableItemBackgroundBorderless))
            setOnClickListener { showTabs() }
        }
        slot(tabWrap)
        slot(icon(P_MENU) { showMenu() })

        val divider = View(this).apply {
            setBackgroundColor(if (isDark) 0xFF333333.toInt() else 0xFFE0E0E0.toInt())
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1))
        }

        main.addView(top)
        main.addView(progress)
        main.addView(container, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        main.addView(divider)
        main.addView(bottom)
        root = FrameLayout(this)
        root.addView(main, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
    }

    private fun hideKeyboard(v: View) {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun setLoading(b: Boolean) {
        loading = b
        reloadBtn.setImageDrawable(PathIcon(if (b) P_CLOSE else P_REFRESH, cText))
    }

    private fun go(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val u = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            !t.contains(" ") && t.contains(".") -> "https://$t"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8")
        }
        tabs[cur].loadUrl(u)
    }

    private fun open(u: String) { tabs[cur].loadUrl(u) }

    private fun isHome(u: String?) = u == null || u.startsWith("https://agood.local")
    private fun shown(u: String?) = if (u == null || !u.startsWith("http") || isHome(u)) "" else u
    private fun isBlocked(host: String) = blocked.any { host == it || host.endsWith(".$it") }

    // ---------- Home page ----------
    private fun loadHome(w: WebView) {
        w.loadDataWithBaseURL("https://agood.local/", homeHtml(), "text/html", "utf-8", "https://agood.local/")
    }

    private fun homeHtml(): String {
        val bg = if (isDark) "#121212" else "#FFFFFF"
        val fg = if (isDark) "#E8EAED" else "#202124"
        val pill = if (isDark) "#2D2F31" else "#F1F3F4"
        return """<!DOCTYPE html><html lang="fa" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><style>
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
body{margin:0;background:$bg;color:$fg;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;padding:44px 20px 24px;min-height:100vh}
.logo{width:92px;height:92px;filter:drop-shadow(0 0 14px #3DA9FF88)}
h1{margin:12px 0 24px;font-size:26px;font-weight:700;letter-spacing:1px;color:#3DA9FF;direction:ltr}
form{width:100%;max-width:520px}
input{width:100%;height:50px;border:0;border-radius:25px;background:$pill;color:$fg;font-size:16px;padding:0 22px;outline:none}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin-top:32px;width:100%;max-width:520px}
a{text-decoration:none;color:$fg;text-align:center;font-size:13px}
.ic{width:56px;height:56px;border-radius:18px;margin:0 auto 8px;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:22px;color:#fff}
</style></head><body>
<svg class="logo" viewBox="0 0 108 108"><rect width="108" height="108" rx="26" fill="#0B1220"/><path d="M32 78L54 30L76 78M41 62L67 62" fill="none" stroke="#3DA9FF" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/></svg>
<h1>Agood Browser</h1>
<form action="https://www.google.com/search" method="get"><input name="q" placeholder="جستجو در گوگل" autocomplete="off" enterkeyhint="search"></form>
<div class="grid">
<a href="https://www.google.com"><div class="ic" style="background:#4285F4">G</div>گوگل</a>
<a href="https://m.youtube.com"><div class="ic" style="background:#E53935">Y</div>یوتیوب</a>
<a href="https://www.aparat.com"><div class="ic" style="background:#ED145B">A</div>آپارات</a>
<a href="https://fa.wikipedia.org"><div class="ic" style="background:#636466">W</div>ویکی‌پدیا</a>
<a href="https://www.varzesh3.com"><div class="ic" style="background:#0E8A3E">V</div>ورزش ۳</a>
<a href="https://www.digikala.com"><div class="ic" style="background:#EF394E">D</div>دیجی‌کالا</a>
</div></body></html>"""
    }

    // ---------- Tabs ----------
    private fun newTab(url: String?): WebView {
        val w = makeWebView()
        tabs.add(w)
        switchTab(tabs.size - 1)
        if (url != null) w.loadUrl(url)
        return w
    }

    private fun openHomeTab() { loadHome(newTab(null)) }

    private fun switchTab(i: Int) {
        cur = i
        container.removeAllViews()
        container.addView(tabs[i])
        tabs.forEachIndexed { j, w -> if (j == i) w.onResume() else w.onPause() }
        urlBar.setText(shown(tabs[i].url))
        tabCount.text = tabs.size.toString()
        progress.visibility = View.INVISIBLE
        setLoading(false)
    }

    private fun closeTab(i: Int) {
        if (tabs.size == 1) { loadHome(tabs[0]); return }
        val w = tabs.removeAt(i)
        container.removeView(w)
        w.destroy()
        val n = when { i < cur -> cur - 1; i == cur -> minOf(cur, tabs.size - 1); else -> cur }
        switchTab(n)
    }

    private fun showTabs() {
        val rows = mutableListOf(Row("+  تب جدید", null, { openHomeTab() }))
        tabs.forEachIndexed { i, w ->
            val home = isHome(w.url)
            val name = if (home) "صفحه‌ی اصلی" else (w.title?.takeIf { it.isNotBlank() } ?: w.url ?: "تب خالی")
            rows.add(Row((if (i == cur) "● " else "") + name, if (home) null else w.url, { switchTab(i) }, { closeTab(i); showTabs() }))
        }
        sheet("تب‌ها (${tabs.size})", rows)
    }

    // ---------- Menu ----------
    private fun showMenu() {
        fun onoff(b: Boolean) = if (b) "روشن" else "خاموش"
        sheet("منو", listOf(
            Row("تب جدید", null, { openHomeTab() }),
            Row("افزودن به نشانک‌ها", null, { addBookmark() }),
            Row("نشانک‌ها", null, { showBookmarks() }),
            Row("تاریخچه", null, { showHistory() }),
            Row("حالت دسکتاپ", onoff(desktop), { toggleDesktop() }),
            Row("مسدودکننده تبلیغ", onoff(adblock), {
                adblock = !adblock
                prefs.edit().putBoolean("adblock", adblock).apply()
                tabs[cur].reload()
            }),
            Row("بدون تصویر (سریع‌تر)", onoff(noImages), { toggleNoImages() }),
            Row("اشتراک‌گذاری لینک", null, {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, tabs[cur].url)
                }, "اشتراک‌گذاری"))
            }),
            Row("پاک کردن کوکی، کش و تاریخچه", null, { clearAll() })
        ))
    }

    private fun toggleDesktop() {
        desktop = !desktop
        prefs.edit().putBoolean("desktop", desktop).apply()
        tabs.forEach { it.settings.userAgentString = if (desktop) desktopUA else null }
        tabs[cur].reload()
    }

    private fun toggleNoImages() {
        noImages = !noImages
        prefs.edit().putBoolean("noimg", noImages).apply()
        tabs.forEach { it.settings.blockNetworkImage = noImages }
        tabs[cur].reload()
    }

    private fun clearAll() {
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        tabs.forEach { it.clearCache(true); it.clearHistory() }
        history.clear()
        prefs.edit().remove("hist").apply()
        toast("پاک شد")
    }

    // ---------- Bookmarks & history ----------
    private fun readList(key: String): MutableList<String> =
        prefs.getString(key, "")!!.split("\n").filter { it.contains("\t") }.toMutableList()

    private fun writeList(key: String, l: List<String>) {
        prefs.edit().putString(key, l.joinToString("\n")).apply()
    }

    private fun clean(s: String) = s.replace("\t", " ").replace("\n", " ").trim().ifBlank { "بدون عنوان" }

    private fun addBookmark() {
        val w = tabs[cur]
        val u = w.url
        if (u == null || isHome(u) || !u.startsWith("http")) { toast("این صفحه را نمی‌شود ذخیره کرد"); return }
        val l = readList("bm2")
        if (l.none { it.substringAfter("\t") == u }) l.add(0, clean(w.title ?: u) + "\t" + u)
        writeList("bm2", l)
        toast("به نشانک‌ها اضافه شد")
    }

    private fun showBookmarks() {
        val l = readList("bm2")
        if (l.isEmpty()) { toast("هنوز نشانکی ذخیره نکرده‌ای"); return }
        sheet("نشانک‌ها", l.map { e ->
            val t = e.substringBefore("\t")
            val u = e.substringAfter("\t")
            Row(t, u, { open(u) }, { l.remove(e); writeList("bm2", l); showBookmarks() })
        })
    }

    private fun addHistory(title: String?, url: String) {
        if (history.firstOrNull()?.substringAfter("\t") == url) return
        history.add(0, clean(title ?: url) + "\t" + url)
        while (history.size > 300) history.removeAt(history.size - 1)
        prefs.edit().putString("hist", history.joinToString("\n")).apply()
    }

    private fun showHistory() {
        if (history.isEmpty()) { toast("تاریخچه خالی است"); return }
        val rows = mutableListOf(Row("پاک کردن همه‌ی تاریخچه", null, {
            history.clear(); prefs.edit().remove("hist").apply()
        }))
        history.take(150).forEach { e ->
            val t = e.substringBefore("\t")
            val u = e.substringAfter("\t")
            rows.add(Row(t, u, { open(u) }))
        }
        sheet("تاریخچه", rows)
    }

    // ---------- Bottom sheet ----------
    private fun sheet(title: String, rows: List<Row>) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = GradientDrawable().apply {
                setColor(cBar)
                val r = dp(22).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(0, dp(8), 0, dp(8))
        }
        box.addView(TextView(this).apply {
            text = title; textSize = 17f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(cText); setPadding(dp(20), dp(10), dp(20), dp(10))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { list.addView(rowView(it, d)) }
        val sv = ScrollView(this)
        sv.addView(list)
        val h = if (rows.size > 7) (resources.displayMetrics.heightPixels * 0.6).toInt() else WRAP_CONTENT
        box.addView(sv, LinearLayout.LayoutParams(MATCH_PARENT, h))
        d.setContentView(box)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(MATCH_PARENT, WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setDimAmount(0.4f)
        }
        d.show()
    }

    private fun rowView(r: Row, d: Dialog): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(8), dp(10))
            setBackgroundResource(attrRes(android.R.attr.selectableItemBackground))
            setOnClickListener { d.dismiss(); r.onClick() }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        col.addView(TextView(this).apply {
            text = r.text; textSize = 15f; setTextColor(cText)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        if (r.sub != null) {
            col.addView(TextView(this).apply {
                text = r.sub; textSize = 12f; setTextColor(cSub)
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                textDirection = View.TEXT_DIRECTION_LTR
            })
        }
        row.addView(col)
        val oc = r.onClose
        if (oc != null) {
            val x = icon(P_CLOSE) { oc(); d.dismiss() }
            x.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            x.setPadding(dp(12), dp(12), dp(12), dp(12))
            row.addView(x)
        }
        return row
    }

    // ---------- WebView ----------
    private fun makeWebView(): WebView {
        val w = WebView(this)
        w.setBackgroundColor(cBg)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
            useWideViewPort = true; loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            blockNetworkImage = noImages
            allowFileAccess = false
            if (desktop) userAgentString = desktopUA
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)

        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, url: String?, f: Bitmap?) {
                if (v === tabs.getOrNull(cur)) {
                    urlBar.setText(shown(url))
                    progress.progress = 5
                    progress.visibility = View.VISIBLE
                    setLoading(true)
                }
            }
            override fun onPageFinished(v: WebView, url: String?) {
                if (url != null && url.startsWith("http") && !isHome(url)) addHistory(v.title, url)
                if (v === tabs.getOrNull(cur)) {
                    urlBar.setText(shown(url))
                    progress.visibility = View.INVISIBLE
                    setLoading(false)
                }
            }
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                if (adblock) {
                    val h = r.url.host
                    if (h != null && isBlocked(h)) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }
                return null
            }
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val sc = r.url.scheme ?: return false
                if (sc == "http" || sc == "https") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, r.url)) } catch (_: Exception) {}
                return true
            }
        }

        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                if (v === tabs.getOrNull(cur)) progress.progress = p
            }
            override fun onCreateWindow(v: WebView, d: Boolean, g: Boolean, msg: Message): Boolean {
                val t = newTab(null)
                (msg.obj as WebView.WebViewTransport).webView = t
                msg.sendToTarget()
                return true
            }
            @Suppress("DEPRECATION")
            override fun onShowCustomView(v: View, cb: CustomViewCallback) {
                if (customView != null) { cb.onCustomViewHidden(); return }
                customView = v; customCb = cb
                v.setBackgroundColor(Color.BLACK)
                root.addView(v, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            }
            override fun onHideCustomView() { hideCustom() }
        }

        w.setDownloadListener { url, ua, cd, mime, _ ->
            try {
                val r = DownloadManager.Request(Uri.parse(url))
                r.setMimeType(mime)
                r.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                r.addRequestHeader("User-Agent", ua)
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, cd, mime))
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(r)
                toast("دانلود شروع شد")
            } catch (e: Exception) {
                toast("دانلود ناموفق بود")
            }
        }
        return w
    }

    private fun hideCustom() {
        customView?.let { root.removeView(it) }
        customCb?.onCustomViewHidden()
        customView = null; customCb = null
        applyBars()
    }

    override fun onPause() { super.onPause(); tabs.forEach { it.onPause() } }
    override fun onResume() { super.onResume(); tabs.getOrNull(cur)?.onResume() }
}
