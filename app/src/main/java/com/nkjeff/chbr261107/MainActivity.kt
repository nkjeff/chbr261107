package com.nkjeff.chbr261107

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.net.URLEncoder

class MainActivity : Activity() {
    private data class BrowserTab(val web: WebView, var title: String = "新标签页", var url: String = "about:blank")
    private val tabs = mutableListOf<BrowserTab>()
    private var current = 0
    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var webHost: FrameLayout
    private lateinit var tabButton: TextView
    private lateinit var backButton: TextView
    private lateinit var forwardButton: TextView
    private val white = Color.WHITE
    private val black = Color.BLACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = black
        window.navigationBarColor = black
        buildLayout()
        addTab("https://www.google.com")
    }

    private fun buildLayout() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(black)
            setPadding(dp(8), dp(6), dp(8), dp(4))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        address = EditText(this).apply {
            setSingleLine(true)
            textSize = 14f
            setTextColor(white)
            setHintTextColor(0xFF888888.toInt())
            hint = "搜索或输入网址"
            setPadding(dp(12), 0, dp(8), 0)
            background = addressBackground()
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setOnEditorActionListener { _, action, event ->
                if (action == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    navigate(address.text.toString())
                    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
                    true
                } else false
            }
        }
        top.addView(address, LinearLayout.LayoutParams(0, dp(42), 1f))
        tabButton = textButton("1").apply { setOnClickListener { showTabs() } }
        top.addView(tabButton, LinearLayout.LayoutParams(dp(44), dp(42)).apply { leftMargin = dp(6) })
        root.addView(top)
        webHost = FrameLayout(this).apply { setBackgroundColor(black) }
        root.addView(webHost, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        backButton = textButton("‹").apply { setOnClickListener { activeWeb()?.let { if (it.canGoBack()) it.goBack() } } }
        forwardButton = textButton("›").apply { setOnClickListener { activeWeb()?.let { if (it.canGoForward()) it.goForward() } } }
        val refresh = textButton("↻").apply { setOnClickListener { activeWeb()?.reload() } }
        val plus = textButton("+").apply { setOnClickListener { addTab("about:blank") } }
        listOf(backButton, forwardButton, refresh, plus).forEach { bottom.addView(it, LinearLayout.LayoutParams(0, dp(44), 1f)) }
        root.addView(bottom)
        setContentView(root)
    }

    private fun addTab(url: String) {
        val web = WebView(this)
        web.setBackgroundColor(black)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadsImagesAutomatically = true
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        web.settings.javaScriptCanOpenWindowsAutomatically = false
        web.settings.setSupportMultipleWindows(false)
        web.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                val tab = tabs.firstOrNull { it.web === view } ?: return
                tab.title = title?.takeIf { it.isNotBlank() } ?: tab.url
                updateTabButton()
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString() ?: return false
                return !(target.startsWith("http://") || target.startsWith("https://"))
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                val tab = tabs.firstOrNull { it.web === view } ?: return
                tab.url = url ?: tab.url
                if (tabs.getOrNull(current) === tab) {
                    if (!address.hasFocus()) address.setText(tab.url)
                    updateNavigation()
                }
            }
        }
        web.setOnLongClickListener { view ->
            val hit = (view as WebView).hitTestResult
            val target = hit.extra.orEmpty()
            if (target.isBlank()) return@setOnLongClickListener false
            showLongPressMenu(view, hit.type, target)
            true
        }
        tabs.add(BrowserTab(web, url = url))
        selectTab(tabs.lastIndex)
        if (url != "about:blank") web.loadUrl(url)
    }

    private fun showLongPressMenu(web: WebView, type: Int, target: String) {
        val menu = PopupMenu(this, web)
        val image = type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        val link = type == WebView.HitTestResult.ANCHOR_TYPE || type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        if (link) menu.menu.add("在新标签页打开链接").setOnMenuItemClickListener { addTab(target); true }
        if (image) menu.menu.add("Google 搜图").setOnMenuItemClickListener {
            val search = "https://www.google.com/searchbyimage?image_url=" + URLEncoder.encode(target, "UTF-8")
            addTab(search)
            true
        }
        menu.menu.add("复制地址").setOnMenuItemClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("URL", target))
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            true
        }
        menu.show()
    }

    private fun selectTab(index: Int) {
        if (index !in tabs.indices) return
        current = index
        webHost.removeAllViews()
        val web = tabs[index].web
        (web.parent as? ViewGroup)?.removeView(web)
        webHost.addView(web, FrameLayout.LayoutParams(-1, -1))
        if (!address.hasFocus()) address.setText(tabs[index].url.takeUnless { it == "about:blank" } ?: "")
        updateTabButton()
        updateNavigation()
    }

    private fun showTabs() {
        val labels = tabs.mapIndexed { i, tab -> (if (i == current) "● " else "") + (i + 1).toString() + ". " + tab.title.take(32) }.toMutableList()
        labels.add("＋ 新建标签页")
        AlertDialog.Builder(this).setItems(labels.toTypedArray()) { _, which ->
            if (which == tabs.size) addTab("about:blank") else selectTab(which)
        }.setNeutralButton("关闭当前标签") { _, _ -> closeTab(current) }
            .setNegativeButton("取消", null).show()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val removed = tabs.removeAt(index)
        webHost.removeView(removed.web)
        removed.web.stopLoading()
        removed.web.destroy()
        if (tabs.isEmpty()) addTab("about:blank")
        else selectTab(if (index >= tabs.size) tabs.lastIndex else index)
    }

    private fun navigate(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        val target = if (value.startsWith("http://") || value.startsWith("https://")) value
        else if (value.contains(".") && !value.contains(" ")) "https://$value"
        else "https://www.google.com/search?q=" + URLEncoder.encode(value, "UTF-8")
        activeWeb()?.loadUrl(target)
    }

    private fun activeWeb(): WebView? = tabs.getOrNull(current)?.web
    private fun updateTabButton() { if (::tabButton.isInitialized) tabButton.text = tabs.size.toString() }
    private fun updateNavigation() {
        if (!::backButton.isInitialized) return
        backButton.alpha = if (activeWeb()?.canGoBack() == true) 1f else 0.45f
        forwardButton.alpha = if (activeWeb()?.canGoForward() == true) 1f else 0.45f
    }
    private fun textButton(label: String) = TextView(this).apply {
        text = label; textSize = 22f; gravity = Gravity.CENTER; setTextColor(white)
        isClickable = true; isFocusable = true
        background = android.graphics.drawable.ColorDrawable(black)
    }
    private fun addressBackground() = GradientDrawable().apply {
        setColor(0xFF080808.toInt()); setStroke(dp(1), 0xFF555555.toInt()); cornerRadius = dp(2).toFloat()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        val web = activeWeb()
        if (web?.canGoBack() == true) web.goBack()
        else if (tabs.size > 1) closeTab(current)
        else super.onBackPressed()
    }

    override fun onDestroy() {
        tabs.forEach { it.web.stopLoading(); it.web.destroy() }
        tabs.clear()
        super.onDestroy()
    }
}
