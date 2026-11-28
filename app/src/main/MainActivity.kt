package com.agood.browser

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
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
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.speech.RecognizerIntent
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.PathParser
import androidx.core.widget.doAfterTextChanged
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder

private const val P_BACK = "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"
private const val P_FORWARD = "M12,4l-1.41,1.41L16.17,11H4v2h12.17l-5.58,5.59L12,20l8,-8z"
private const val P_HOME = "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z"
private const val P_MENU = "M12,8c1.1,0 2,-0.9 2,-2s-0.9,-2 -2,-2 -2,0.9 -2,2 0.9,2 2,2zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2zM12,16c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"
private const val P_REFRESH = "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z"
private const val P_CLOSE = "M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"
private const val P_UP = "M7.41,15.41L12,10.83l4.59,4.58L18,14l-6,-6 -6,6z"
private const val P_DOWN = "M7.41,8.59L12,13.17l4.59,-4.58L18,10l-6,6 -6,-6z"

private const val HOME_BASE = "https://good.local"

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

    private class Eng(val name: String, val action: String, val param: String)

    private val engines = listOf(
        Eng("Google", "https://www.google.com/search", "q"),
        Eng("Bing", "https://www.bing.com/search", "q"),
        Eng("DuckDuckGo", "https://duckduckgo.com/", "q"),
        Eng("Yandex", "https://yandex.com/search/", "text")
    )
    private val accentNames = listOf("آبی", "سبز", "قرمز", "بنفش", "نارنجی")
    private val accentLight = intArrayOf(0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFE53935.toInt(), 0xFF8E24AA.toInt(), 0xFFFB8C00.toInt())
    private val accentDark = intArrayOf(0xFF4DB6FF.toInt(), 0xFF66BB6A.toInt(), 0xFFEF5350.toInt(), 0xFFBA68C8.toInt(), 0xFFFFA726.toInt())
    private val zoomVals = intArrayOf(85, 100, 120, 150)

    private val tabs = mutableListOf<WebView>()
    private val history = ArrayList<String>()
    private var cur = -1
    @Volatile private var adblock = true
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
    private lateinit var findBar: LinearLayout
    private lateinit var findInput: EditText
    private lateinit var findCount: TextView
    private var settingsView: View? = null
    private var settingsScroll: ScrollView? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private val prefs by lazy { getSharedPreferences("agood_browser", MODE_PRIVATE) }

    private var permCb: ((Boolean) -> Unit)? = null
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val cb = permCb
        permCb = null
        cb?.invoke(res.values.any { it })
    }
    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == RESULT_OK && t != null) go(t)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------- prefs helpers ----------
    private fun pInt(k: String, d: Int) = prefs.getInt(k, d)
    private fun putInt(k: String, v: Int) = prefs.edit().putInt(k, v).apply()
    private fun pBool(k: String, d: Boolean) = prefs.getBoolean(k, d)
    private fun putBool(k: String, v: Boolean) = prefs.edit().putBoolean(k, v).apply()
    private fun pStr(k: String, d: String) = prefs.getString(k, d) ?: d
    private fun putStr(k: String, v: String) = prefs.edit().putString(k, v).apply()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        initColors()
        adblock = pBool("adblock", true)
        history.addAll(readList("hist"))
        buildUi()
        applyBars()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val w = tabs.getOrNull(cur)
                when {
                    customView != null -> hideCustom()
                    settingsView != null -> closeSettings()
                    findBar.visibility == View.VISIBLE -> closeFind()
                    w != null && w.canGoBack() -> w.goBack()
                    tabs.size > 1 -> closeTab(cur)
                    else -> finish()
                }
            }
        })
        val u = intent?.data?.toString()
        if (u != null) newTab(u) else openHomeTab()
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        if (pInt("theme", 0) == 0 && computeDark() != isDark) rebuild()
    }

    private fun computeDark(): Boolean = when (pInt("theme", 0)) {
        1 -> false
        2 -> true
        else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun initColors() {
        isDark = computeDark()
        val ai = pInt("accent", 0).coerceIn(0, accentNames.size - 1)
        if (isDark) {
            cBg = 0xFF121212.toInt(); cBar = 0xFF1E1E1E.toInt(); cPill = 0xFF2D2F31.toInt()
            cText = 0xFFE8EAED.toInt(); cSub = 0xFF9AA0A6.toInt(); cAccent = accentDark[ai]
        } else {
            cBg = 0xFFFFFFFF.toInt(); cBar = 0xFFFFFFFF.toInt(); cPill = 0xFFF1F3F4.toInt()
            cText = 0xFF202124.toInt(); cSub = 0xFF5F6368.toInt(); cAccent = accentLight[ai]
        }
    }

    @Suppress("DEPRECATION")
    private fun applyBars() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = cBar
        window.navigationBarColor = cBar
        window.decorView.systemUiVisibility = if (isDark) 0 else
            (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        if (pBool("keepon", false)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun rebuild() {
        val wasOpen = settingsView != null
        val sy = settingsScroll?.scrollY ?: 0
        container.removeAllViews()
        initColors()
        buildUi()
        applyBars()
        tabs.forEach { it.setBackgroundColor(cBg) }
        switchTab(cur)
        reloadHomes()
        if (wasOpen) openSettings(sy)
    }

    private fun attrRes(a: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(a, tv, true)
        return tv.resourceId
    }

    private fun icon(path: String, pad: Int = 14, onClick: () -> Unit) = ImageView(this).apply {
        setImageDrawable(PathIcon(path, cText))
        scaleType = ImageView.ScaleType.FIT_CENTER
        setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
        setBackgroundResource(attrRes(android.R.attr.selectableItemBackgroundBorderless))
        setOnClickListener { onClick() }
    }

    private fun buildUi() {
        settingsView = null
        settingsScroll = null
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
        reloadBtn = icon(P_REFRESH, 10) {
            val w = tabs[cur]
            if (loading) w.stopLoading() else w.reload()
        }
        reloadBtn.layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        pill.addView(urlBar)
        pill.addView(reloadBtn)
        top.addView(pill)

        // ----- find-in-page bar -----
        findInput = EditText(this).apply {
            setSingleLine(); hint = "جستجو در صفحه"; textSize = 14f
            setTextColor(cText); setHintTextColor(cSub); background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
        }
        findCount = TextView(this).apply { textSize = 12f; setTextColor(cSub) }
        findBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(cBar); setPadding(dp(16), 0, dp(4), 0)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(44))
        }
        findBar.addView(findInput)
        findBar.addView(findCount)
        findBar.addView(icon(P_UP, 10) { tabs[cur].findNext(false) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        findBar.addView(icon(P_DOWN, 10) { tabs[cur].findNext(true) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        findBar.addView(icon(P_CLOSE, 10) { closeFind() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        findInput.doAfterTextChanged {
            val q = it?.toString() ?: ""
            val w = tabs.getOrNull(cur)
            if (w != null) {
                if (q.isEmpty()) { w.clearMatches(); findCount.text = "" } else w.findAllAsync(q)
            }
        }

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
        main.addView(findBar)
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
            else -> {
                val e = engines[pInt("engine", 0).coerceIn(0, engines.size - 1)]
                e.action + "?" + e.param + "=" + URLEncoder.encode(t, "UTF-8")
            }
        }
        tabs[cur].loadUrl(u)
    }

    private fun open(u: String) { tabs[cur].loadUrl(u) }

    private fun isHome(u: String?) = u == null || u.startsWith(HOME_BASE)
    private fun shown(u: String?) = if (u == null || !u.startsWith("http") || isHome(u)) "" else u
    private fun isBlocked(host: String) = blocked.any { host == it || host.endsWith(".$it") }
    private fun isIncognito(w: WebView) = w.tag == "inc"
    private fun normalizeUrl(s: String) = if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"

    // ---------- Find in page ----------
    private fun showFind() {
        findBar.visibility = View.VISIBLE
        findInput.setText("")
        findInput.requestFocus()
        findInput.postDelayed({
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(findInput, InputMethodManager.SHOW_IMPLICIT)
        }, 250)
    }

    private fun closeFind() {
        findBar.visibility = View.GONE
        tabs.getOrNull(cur)?.clearMatches()
        findCount.text = ""
        hideKeyboard(findInput)
    }

    // ---------- Voice search ----------
    private fun startVoice() {
        try {
            voiceLauncher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            )
        } catch (e: Exception) {
            toast("جستجوی صوتی روی این گوشی نیست")
        }
    }

    // ---------- Runtime permissions for web pages ----------
    private fun askRuntime(perms: Array<String>, done: (Boolean) -> Unit) {
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) { done(true); return }
        permCb = done
        permLauncher.launch(missing.toTypedArray())
    }

    private fun confirm(title: String, msg: String, yes: () -> Unit, no: () -> Unit = {}) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg)
            .setPositiveButton("اجازه") { _, _ -> yes() }
            .setNegativeButton("رد") { _, _ -> no() }
            .setOnCancelListener { no() }
            .show()
    }

    private fun prompt(title: String, initial: String, ok: (String) -> Unit) {
        val et = EditText(this).apply {
            setText(initial); setSingleLine(); setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("ذخیره") { _, _ -> ok(et.text.toString().trim()) }
            .setNegativeButton("لغو", null)
            .show()
    }

    // ---------- Home page ----------
    private fun loadHome(w: WebView) {
        val h = pStr("home", "")
        if (h.isNotBlank() && !isIncognito(w)) w.loadUrl(h) else loadBuiltinHome(w)
    }

    private fun loadBuiltinHome(w: WebView) {
        w.loadDataWithBaseURL("$HOME_BASE/", homeHtml(isIncognito(w)), "text/html", "utf-8", "$HOME_BASE/")
    }

    private fun reloadHomes() {
        tabs.filter { it.url?.startsWith(HOME_BASE) == true }.forEach { loadBuiltinHome(it) }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    private fun homeHtml(inc: Boolean): String {
        val dark = isDark || inc
        val bg = if (dark) "#121212" else "#FFFFFF"
        val fg = if (dark) "#E8EAED" else "#202124"
        val sub = if (dark) "#9AA0A6" else "#5F6368"
        val barCss = if (dark) "background:#303134;border:1px solid #3C4043" else "background:#fff;box-shadow:0 1px 6px rgba(32,33,36,.28)"
        val eng = engines[pInt("engine", 0).coerceIn(0, engines.size - 1)]
        val uname = JSONObject.quote(pStr("uname", ""))

        val tiles = StringBuilder()
        if (!inc && pBool("shortcuts", true)) {
            val defs = listOf(
                arrayOf("گوگل", "https://www.google.com", "#4285F4", "G"),
                arrayOf("یوتیوب", "https://m.youtube.com", "#E53935", "Y"),
                arrayOf("آپارات", "https://www.aparat.com", "#ED145B", "A"),
                arrayOf("ویکی‌پدیا", "https://fa.wikipedia.org", "#636466", "W"),
                arrayOf("ورزش ۳", "https://www.varzesh3.com", "#0E8A3E", "V"),
                arrayOf("دیجی‌کالا", "https://www.digikala.com", "#EF394E", "D")
            )
            val pal = listOf("#4285F4", "#E53935", "#0E8A3E", "#8E24AA", "#FB8C00", "#00897B", "#546E7A")
            val items = ArrayList<Array<String>>(defs)
            readList("bm2").take(3).forEach { e ->
                val t = e.substringBefore("\t")
                val u = e.substringAfter("\t")
                items.add(arrayOf(t.take(14), u, pal[(u.hashCode() and 0x7fffffff) % pal.size], t.trim().take(1).uppercase()))
            }
            items.forEach {
                tiles.append("<a href=\"${esc(it[1])}\"><div class=\"ic\" style=\"background:${it[2]}\">${esc(it[3])}</div>${esc(it[0])}</a>")
            }
        }

        val greet = if (inc)
            "<div id=\"g\">تب ناشناس</div><div id=\"d\">تاریخچه‌ی این تب ذخیره نمی‌شود. کوکی‌ها با بقیه‌ی تب‌ها مشترک است.</div>"
        else "<div id=\"g\"></div><div id=\"d\"></div>"

        return """<!DOCTYPE html><html lang="fa" dir="rtl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><style>
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
body{margin:0;background:$bg;color:$fg;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;padding:14px 20px 28px;min-height:100vh}
.top{width:100%;display:flex;justify-content:space-between;font-size:13px}
.top a{color:$sub;text-decoration:none;padding:8px 10px}
.logo{margin:34px 0 4px;font-size:58px;font-weight:700;direction:ltr;letter-spacing:-1px}
.c1{color:#4285F4}.c2{color:#EA4335}.c3{color:#FBBC05}.c4{color:#34A853}
.tag{font-size:13px;color:$sub;margin-bottom:22px}
.bar{width:100%;max-width:560px;height:52px;border-radius:26px;$barCss;display:flex;align-items:center;padding:0 16px 0 8px;gap:10px}
.bar input{flex:1;border:0;background:transparent;color:$fg;font-size:16px;outline:none;min-width:0;height:100%}
.mic{width:40px;height:40px;display:flex;align-items:center;justify-content:center}
.greet{margin-top:30px;text-align:center}
#g{font-size:18px;font-weight:600}#d{font-size:13px;color:$sub;margin-top:4px}
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px;margin-top:28px;width:100%;max-width:560px}
.grid a{text-decoration:none;color:$fg;text-align:center;font-size:13px}
.ic{width:56px;height:56px;border-radius:18px;margin:0 auto 8px;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:22px;color:#fff}
</style></head><body>
<div class="top"><a href="good://settings">تنظیمات</a><a href="good://incognito">تب ناشناس</a></div>
<div class="logo"><span class="c1">G</span><span class="c2">o</span><span class="c3">o</span><span class="c4">d</span></div>
<div class="tag">Good Browser</div>
<form action="${eng.action}" method="get"><div class="bar">
<svg width="22" height="22" viewBox="0 0 24 24" fill="$sub"><path d="M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"/></svg>
<input name="${eng.param}" placeholder="جستجو یا وارد کردن آدرس" autocomplete="off" enterkeyhint="search">
<a class="mic" href="good://voice"><svg width="24" height="24" viewBox="0 0 24 24" fill="#4285F4"><path d="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11H5c0,3.41 2.72,6.23 6,6.72V21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z"/></svg></a>
</div></form>
<div class="greet">$greet</div>
<div class="grid">$tiles</div>
<script>
document.forms[0].onsubmit=function(){var v=this.elements[0].value.trim();if(/^https?:\/\//i.test(v)){location.href=v;return false}if(v.indexOf(' ')<0&&v.indexOf('.')>0){location.href='https://'+v;return false}return true};
if(!${inc}){var NAME=$uname;var h=new Date().getHours();var g=h<5?'شب بخیر':h<12?'صبح بخیر':h<17?'ظهر بخیر':h<20?'عصر بخیر':'شب بخیر';
document.getElementById('g').textContent=g+(NAME?'، '+NAME:'');
try{document.getElementById('d').textContent=new Intl.DateTimeFormat('fa-IR-u-ca-persian',{weekday:'long',day:'numeric',month:'long'}).format(new Date())}catch(e){}}
</script></body></html>"""
    }

    // ---------- Tabs ----------
    private fun newTab(url: String?, inc: Boolean = false): WebView {
        val w = makeWebView(inc)
        tabs.add(w)
        switchTab(tabs.size - 1)
        if (url != null) w.loadUrl(url)
        return w
    }

    private fun openHomeTab(inc: Boolean = false) { loadHome(newTab(null, inc)) }

    private fun switchTab(i: Int) {
        cur = i
        if (findBar.visibility == View.VISIBLE) closeFind()
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
        val wasInc = isIncognito(w)
        container.removeView(w)
        w.destroy()
        if (wasInc && tabs.none { isIncognito(it) }) tabs.getOrNull(0)?.clearCache(true)
        val n = when { i < cur -> cur - 1; i == cur -> minOf(cur, tabs.size - 1); else -> cur }
        switchTab(n)
    }

    private fun showTabs() {
        val rows = mutableListOf(Row("+  تب جدید", null, { openHomeTab() }))
        tabs.forEachIndexed { i, w ->
            val home = isHome(w.url)
            val name = if (home) "صفحه‌ی اصلی" else (w.title?.takeIf { it.isNotBlank() } ?: w.url ?: "تب خالی")
            val prefix = (if (i == cur) "● " else "") + (if (isIncognito(w)) "ناشناس · " else "")
            rows.add(Row(prefix + name, if (home) null else w.url, { switchTab(i) }, { closeTab(i); showTabs() }))
        }
        sheet("تب‌ها (${tabs.size})", rows)
    }

    // ---------- Menu ----------
    private fun showMenu() {
        sheet("منو", listOf(
            Row("تب جدید", null, { openHomeTab() }),
            Row("تب ناشناس", "تاریخچه‌ی این تب ذخیره نمی‌شود", { openHomeTab(true) }),
            Row("افزودن به نشانک‌ها", null, { addBookmark() }),
            Row("نشانک‌ها", null, { showBookmarks() }),
            Row("تاریخچه", null, { showHistory() }),
            Row("دانلودها", null, { showDownloads() }),
            Row("جستجو در صفحه", null, { showFind() }),
            Row("حالت دسکتاپ", if (pBool("desktop", false)) "روشن" else "خاموش", { toggleDesktop() }),
            Row("اشتراک‌گذاری لینک", null, {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, tabs[cur].url)
                }, "اشتراک‌گذاری"))
            }),
            Row("تنظیمات", null, { openSettings() })
        ))
    }

    private fun toggleDesktop() {
        putBool("desktop", !pBool("desktop", false))
        tabs.forEach { applySettings(it) }
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
            Row(t, u, { open(u) }, { l.remove(e); writeList("bm2", l); reloadHomes(); showBookmarks() })
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

    // ---------- Downloads ----------
    private fun showDownloads() {
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val rows = ArrayList<Row>()
        val c = dm.query(DownloadManager.Query())
        c?.use {
            val iId = it.getColumnIndex(DownloadManager.COLUMN_ID)
            val iTitle = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val iStatus = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val iMime = it.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)
            while (it.moveToNext()) {
                val id = it.getLong(iId)
                val title = it.getString(iTitle) ?: "فایل"
                val st = it.getInt(iStatus)
                val mime = it.getString(iMime) ?: "*/*"
                val sub = when (st) {
                    DownloadManager.STATUS_SUCCESSFUL -> "کامل شد"
                    DownloadManager.STATUS_FAILED -> "ناموفق"
                    DownloadManager.STATUS_PAUSED -> "متوقف"
                    else -> "در حال دانلود…"
                }
                rows.add(0, Row(title, sub, { openDownload(dm, id, mime, st) }, { dm.remove(id); showDownloads() }))
            }
        }
        if (rows.isEmpty()) { toast("هنوز دانلودی نداری"); return }
        sheet("دانلودها", rows)
    }

    private fun openDownload(dm: DownloadManager, id: Long, mime: String, st: Int) {
        if (st != DownloadManager.STATUS_SUCCESSFUL) { toast("این دانلود کامل نیست"); return }
        try {
            val uri = dm.getUriForDownloadedFile(id)
            startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            toast("برنامه‌ای برای باز کردن این فایل نیست")
        }
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
            val x = icon(P_CLOSE, 12) { oc(); d.dismiss() }
            x.layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            row.addView(x)
        }
        return row
    }

    // ---------- Settings screen ----------
    private fun openSettings(keepScroll: Int = 0) {
        closeSettings()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(cBg); isClickable = true
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(cBar); setPadding(dp(6), dp(6), dp(16), dp(6))
        }
        head.addView(icon(P_BACK) { closeSettings() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        head.addView(TextView(this).apply {
            text = "تنظیمات"; textSize = 19f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(cText); setPadding(dp(8), 0, 0, 0)
        })
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, 0, 0, dp(24))
        }
        buildSettings(list)
        val sv = ScrollView(this)
        sv.addView(list)
        page.addView(head)
        page.addView(sv, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root.addView(page, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        settingsView = page
        settingsScroll = sv
        if (keepScroll > 0) sv.post { sv.scrollTo(0, keepScroll) }
    }

    private fun closeSettings() {
        settingsView?.let { root.removeView(it) }
        settingsView = null
        settingsScroll = null
    }

    private fun refreshSettings() { openSettings(settingsScroll?.scrollY ?: 0) }

    private fun sec(p: LinearLayout, t: String) {
        p.addView(TextView(this).apply {
            text = t; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(cAccent); setPadding(dp(20), dp(20), dp(20), dp(6))
        })
    }

    private fun rowBase(p: LinearLayout, title: String, sub: String?, onClick: (() -> Unit)?): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setBackgroundResource(attrRes(android.R.attr.selectableItemBackground))
            if (onClick != null) setOnClickListener { onClick() }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        col.addView(TextView(this).apply { text = title; textSize = 15.5f; setTextColor(cText) })
        if (sub != null) {
            col.addView(TextView(this).apply {
                text = sub; textSize = 12.5f; setTextColor(cSub); setPadding(0, dp(2), 0, 0)
            })
        }
        row.addView(col)
        p.addView(row)
        return row
    }

    private fun swRow(p: LinearLayout, title: String, sub: String?, key: String, def: Boolean, after: () -> Unit = {}) {
        val row = rowBase(p, title, sub, null)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val sw = Switch(this).apply {
            isChecked = pBool(key, def)
            thumbTintList = ColorStateList(states, intArrayOf(cAccent, 0xFFBDBDBD.toInt()))
            trackTintList = ColorStateList(states, intArrayOf((cAccent and 0x00FFFFFF) or 0x66000000, 0x66808080))
            setOnCheckedChangeListener { _, c -> putBool(key, c); after() }
        }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
    }

    private fun chRow(p: LinearLayout, title: String, options: List<String>, key: String, def: Int, after: () -> Unit = {}) {
        val curIdx = pInt(key, def).coerceIn(0, options.size - 1)
        rowBase(p, title, options[curIdx]) {
            sheet(title, options.mapIndexed { i, o ->
                Row((if (i == curIdx) "✓  " else "") + o, null, { putInt(key, i); after(); refreshSettings() })
            })
        }
    }

    private fun actRow(p: LinearLayout, title: String, sub: String?, onClick: () -> Unit) {
        rowBase(p, title, sub, onClick)
    }

    private fun buildSettings(p: LinearLayout) {
        sec(p, "ظاهر")
        chRow(p, "تم برنامه", listOf("خودکار (طبق گوشی)", "روشن", "تاریک"), "theme", 0) { rebuild() }
        chRow(p, "رنگ تأکید", accentNames, "accent", 0) { rebuild() }
        chRow(p, "اندازه‌ی متن صفحه‌ها", listOf("کوچک", "معمولی", "بزرگ", "خیلی بزرگ"), "zoom", 1) {
            tabs.forEach { applySettings(it) }
        }
        swRow(p, "تاریک کردن صفحه‌های وب", "روی اندروید ۱۳ به بالا فقط وقتی گوشی در حالت تاریک است", "webdark", false) {
            tabs.forEach { applySettings(it) }
        }

        sec(p, "صفحه‌ی اصلی")
        chRow(p, "موتور جستجو", engines.map { it.name }, "engine", 0) { reloadHomes() }
        actRow(p, "صفحه‌ی خانه", pStr("home", "").ifBlank { "صفحه‌ی Good (پیش‌فرض)" }) { askHome() }
        actRow(p, "نام شما", pStr("uname", "").ifBlank { "تنظیم نشده" }) {
            prompt("نام شما برای سلام صفحه‌ی اصلی", pStr("uname", "")) { v ->
                putStr("uname", v); reloadHomes(); refreshSettings()
            }
        }
        swRow(p, "میانبرهای صفحه‌ی اصلی", "شامل ۳ نشانک آخر", "shortcuts", true) { reloadHomes() }

        sec(p, "مرور وب")
        swRow(p, "حالت دسکتاپ", "نمایش نسخه‌ی کامل سایت‌ها", "desktop", false) {
            tabs.forEach { applySettings(it) }; tabs[cur].reload()
        }
        swRow(p, "بدون تصویر", "صفحه‌ها سریع‌تر و کم‌حجم‌تر باز می‌شوند", "noimg", false) {
            tabs.forEach { applySettings(it) }; tabs[cur].reload()
        }
        swRow(p, "مسدودکننده‌ی تبلیغ و ردیاب", null, "adblock", true) {
            adblock = pBool("adblock", true); tabs[cur].reload()
        }
        swRow(p, "جاوااسکریپت", "خاموش کردنش بعضی سایت‌ها را خراب می‌کند", "js", true) {
            tabs.forEach { applySettings(it) }; tabs[cur].reload()
        }
        swRow(p, "کوکی‌های شخص ثالث", null, "3pc", true) { tabs.forEach { applySettings(it) } }
        swRow(p, "روشن ماندن صفحه", "هنگام مرور، صفحه خاموش نشود", "keepon", false) { applyBars() }

        sec(p, "حریم خصوصی")
        swRow(p, "ذخیره‌ی تاریخچه", null, "savehist", true)
        actRow(p, "پاک کردن اطلاعات مرور", "کوکی، کش و تاریخچه") { clearAll() }

        sec(p, "ابزارها")
        actRow(p, "دانلودها", null) { showDownloads() }
        actRow(p, "نشانک‌ها", null) { showBookmarks() }
        actRow(p, "تاریخچه", null) { showHistory() }

        sec(p, "درباره")
        rowBase(p, "Good Browser", "نسخه‌ی ۳٫۰", null)
        rowBase(p, "سازنده", "امیر محمد محسنی", null)
    }

    private fun askHome() {
        sheet("صفحه‌ی خانه", listOf(
            Row("صفحه‌ی Good (پیش‌فرض)", null, { putStr("home", ""); refreshSettings() }),
            Row("آدرس دلخواه…", null, {
                prompt("آدرس صفحه‌ی خانه", pStr("home", "https://")) { v ->
                    putStr("home", if (v.isBlank()) "" else normalizeUrl(v)); refreshSettings()
                }
            })
        ))
    }

    // ---------- WebView ----------
    @Suppress("DEPRECATION")
    private fun applySettings(w: WebView) {
        val s = w.settings
        s.javaScriptEnabled = pBool("js", true)
        s.textZoom = zoomVals[pInt("zoom", 1).coerceIn(0, zoomVals.size - 1)]
        s.blockNetworkImage = pBool("noimg", false)
        s.userAgentString = if (pBool("desktop", false)) desktopUA else null
        s.cacheMode = if (isIncognito(w)) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, pBool("3pc", true))
        val dark = pBool("webdark", false)
        if (Build.VERSION.SDK_INT >= 33) {
            s.isAlgorithmicDarkeningAllowed = dark
        } else if (Build.VERSION.SDK_INT >= 29) {
            s.forceDark = if (dark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        }
    }

    private fun makeWebView(inc: Boolean): WebView {
        val w = WebView(this)
        if (inc) w.tag = "inc"
        w.setBackgroundColor(cBg)
        w.settings.apply {
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
            useWideViewPort = true; loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
        }
        applySettings(w)

        w.setFindListener { active, total, done ->
            if (done && w === tabs.getOrNull(cur)) {
                findCount.text = if (total == 0) "۰" else "${active + 1}/$total"
            }
        }

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
                if (url != null && url.startsWith("http") && !isHome(url) && !isIncognito(v) && pBool("savehist", true)) {
                    addHistory(v.title, url)
                }
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
                if (sc == "good") {
                    when (r.url.host) {
                        "voice" -> startVoice()
                        "settings" -> openSettings()
                        "incognito" -> openHomeTab(true)
                        else -> {}
                    }
                    return true
                }
                try { startActivity(Intent(Intent.ACTION_VIEW, r.url)) } catch (_: Exception) {}
                return true
            }
        }

        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                if (v === tabs.getOrNull(cur)) progress.progress = p
            }
            override fun onCreateWindow(v: WebView, d: Boolean, g: Boolean, msg: Message): Boolean {
                val t = newTab(null, isIncognito(v))
                (msg.obj as WebView.WebViewTransport).webView = t
                msg.sendToTarget()
                return true
            }
            override fun onPermissionRequest(req: PermissionRequest) {
                val need = ArrayList<String>()
                val labels = ArrayList<String>()
                req.resources.forEach {
                    when (it) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> { need.add(Manifest.permission.CAMERA); labels.add("دوربین") }
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> { need.add(Manifest.permission.RECORD_AUDIO); labels.add("میکروفون") }
                        else -> {}
                    }
                }
                if (need.isEmpty()) { req.deny(); return }
                confirm(
                    "درخواست دسترسی",
                    "${req.origin.host} می‌خواهد به ${labels.joinToString(" و ")} دسترسی داشته باشد.",
                    { askRuntime(need.toTypedArray()) { ok -> if (ok) req.grant(req.resources) else req.deny() } },
                    { req.deny() }
                )
            }
            override fun onGeolocationPermissionsShowPrompt(origin: String, cb: GeolocationPermissions.Callback) {
                confirm(
                    "موقعیت مکانی",
                    "$origin می‌خواهد موقعیت شما را بداند.",
                    {
                        askRuntime(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { ok ->
                            cb.invoke(origin, ok, false)
                        }
                    },
                    { cb.invoke(origin, false, false) }
                )
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
