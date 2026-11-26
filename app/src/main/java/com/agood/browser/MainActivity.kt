package com.agood.browser

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {
    private val home = "https://www.google.com"
    private val desktopUA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val tabs = mutableListOf<WebView>()
    private var cur = -1
    private var desktop = false
    private lateinit var root: FrameLayout
    private lateinit var container: FrameLayout
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var tabBtn: Button
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private val prefs by lazy { getSharedPreferences("b", MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        buildUi()
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
        newTab(intent?.data?.toString() ?: home)
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; minWidth = 0; minimumWidth = 0
        setPadding(0, 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        setOnClickListener { f() }
    }

    private fun buildUi() {
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        bar.addView(btn("◀") { tabs[cur].let { if (it.canGoBack()) it.goBack() } })
        bar.addView(btn("▶") { tabs[cur].let { if (it.canGoForward()) it.goForward() } })
        bar.addView(btn("⟳") { tabs[cur].reload() })
        urlBar = EditText(this).apply {
            setSingleLine(); hint = "جستجو یا آدرس سایت"; textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setSelectAllOnFocus(true)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
            setOnEditorActionListener { v, _, _ -> go(v.text.toString()); v.clearFocus(); true }
        }
        bar.addView(urlBar)
        tabBtn = btn("1") { showTabs() }
        bar.addView(tabBtn)
        bar.addView(btn("⋮") { showMenu() })
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(3))
        }
        container = FrameLayout(this)
        main.addView(bar)
        main.addView(progress)
        main.addView(container, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        root = FrameLayout(this)
        root.addView(main, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
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

    // ---------- Tabs ----------
    private fun newTab(url: String?): WebView {
        val w = makeWebView()
        tabs.add(w)
        switchTab(tabs.size - 1)
        if (url != null) w.loadUrl(url)
        return w
    }

    private fun switchTab(i: Int) {
        cur = i
        container.removeAllViews()
        container.addView(tabs[i])
        urlBar.setText(tabs[i].url ?: "")
        tabBtn.text = tabs.size.toString()
        progress.visibility = View.GONE
    }

    private fun closeTab(i: Int) {
        if (tabs.size == 1) { tabs[0].loadUrl(home); return }
        val w = tabs.removeAt(i)
        container.removeView(w)
        w.destroy()
        val n = when { i < cur -> cur - 1; i == cur -> minOf(cur, tabs.size - 1); else -> cur }
        switchTab(n)
    }

    private fun showTabs() {
        val names = tabs.mapIndexed { i, w ->
            (if (i == cur) "● " else "○ ") + (w.title?.takeIf { it.isNotBlank() } ?: w.url ?: "تب خالی")
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("تب‌ها")
            .setItems(names) { _, i -> switchTab(i) }
            .setPositiveButton("تب جدید") { _, _ -> newTab(home) }
            .setNegativeButton("بستن تب فعلی") { _, _ -> closeTab(cur) }
            .show()
    }

    // ---------- Menu / bookmarks ----------
    private fun showMenu() {
        val items = arrayOf(
            "تب جدید", "افزودن به نشانک‌ها", "نشانک‌ها",
            if (desktop) "حالت دسکتاپ: روشن ✓" else "حالت دسکتاپ: خاموش",
            "اشتراک‌گذاری لینک", "پاک کردن کوکی و کش"
        )
        AlertDialog.Builder(this).setItems(items) { _, i ->
            val w = tabs[cur]
            when (i) {
                0 -> newTab(home)
                1 -> {
                    val set = prefs.getStringSet("bm", emptySet())!!.toMutableSet()
                    set.add((w.title ?: "") + "\t" + (w.url ?: ""))
                    prefs.edit().putStringSet("bm", set).apply()
                    Toast.makeText(this, "ذخیره شد", Toast.LENGTH_SHORT).show()
                }
                2 -> showBookmarks()
                3 -> {
                    desktop = !desktop
                    tabs.forEach {
                        it.settings.userAgentString = if (desktop) desktopUA else null
                        it.settings.useWideViewPort = true
                    }
                    w.reload()
                }
                4 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, w.url)
                }, "اشتراک‌گذاری"))
                5 -> {
                    CookieManager.getInstance().removeAllCookies(null)
                    tabs.forEach { it.clearCache(true) }
                    Toast.makeText(this, "پاک شد", Toast.LENGTH_SHORT).show()
                }
            }
        }.show()
    }

    private fun showBookmarks() {
        val list = prefs.getStringSet("bm", emptySet())!!.toList()
        if (list.isEmpty()) { Toast.makeText(this, "نشانکی نیست", Toast.LENGTH_SHORT).show(); return }
        val names = list.map { it.substringBefore("\t").ifBlank { it.substringAfter("\t") } }.toTypedArray()
        AlertDialog.Builder(this).setTitle("نشانک‌ها")
            .setItems(names) { _, i -> tabs[cur].loadUrl(list[i].substringAfter("\t")) }
            .setNegativeButton("حذف همه") { _, _ -> prefs.edit().remove("bm").apply() }
            .show()
    }

    // ---------- WebView ----------
    private fun makeWebView(): WebView {
        val w = WebView(this)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
            loadWithOverviewMode = true; useWideViewPort = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (desktop) userAgentString = desktopUA
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)

        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, url: String?, f: android.graphics.Bitmap?) {
                if (v === tabs.getOrNull(cur)) { urlBar.setText(url); progress.visibility = View.VISIBLE }
            }
            override fun onPageFinished(v: WebView, url: String?) {
                if (v === tabs.getOrNull(cur)) { urlBar.setText(url); progress.visibility = View.GONE }
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
            override fun onShowCustomView(v: View, cb: CustomViewCallback) {
                if (customView != null) { cb.onCustomViewHidden(); return }
                customView = v; customCb = cb
                v.setBackgroundColor(Color.BLACK)
                root.addView(v, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                @Suppress("DEPRECATION")
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
                Toast.makeText(this, "دانلود شروع شد", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "دانلود ناموفق بود", Toast.LENGTH_SHORT).show()
            }
        }
        return w
    }

    private fun hideCustom() {
        customView?.let { root.removeView(it) }
        customCb?.onCustomViewHidden()
        customView = null; customCb = null
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    override fun onPause() { super.onPause(); tabs.forEach { it.onPause() } }
    override fun onResume() { super.onResume(); tabs.forEach { it.onResume() } }
}
