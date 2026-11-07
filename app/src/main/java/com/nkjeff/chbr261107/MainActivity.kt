package com.nkjeff.chbr261107

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.MotionEvent
import org.json.JSONObject
import org.json.JSONTokener
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
        window.decorView.setBackgroundColor(black)
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
            setHintTextColor(0x99FFFFFF.toInt())
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

    private fun addTab(url: String, suppliedWebView: WebView? = null) {
        val web = suppliedWebView ?: WebView(this)
        web.setBackgroundColor(black)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadsImagesAutomatically = true
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        web.settings.javaScriptCanOpenWindowsAutomatically = true
        web.settings.setSupportMultipleWindows(true)
        web.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                val tab = tabs.firstOrNull { it.web === view } ?: return
                tab.title = title?.takeIf { it.isNotBlank() } ?: tab.url
                updateTabButton()
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val popup = WebView(this@MainActivity)
                addTab("about:blank", popup)
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
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
        var lastTouchX = 0f
        var lastTouchY = 0f
        web.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            false
        }
        web.setOnLongClickListener { view ->
            val targetWeb = view as WebView
            val density = resources.displayMetrics.density * targetWeb.scale
            val cssX = (lastTouchX / density).toInt()
            val cssY = (lastTouchY / density).toInt()
            val script = """(function(){
                var el=document.elementFromPoint(${cssX},${cssY});
                if(!el) return null;
                var node=el, link=null, image=null, depth=0;
                while(node && depth++<12){
                    if(!link && node.tagName==='A' && node.href) link=node.href;
                    if(!link && node.getAttribute){
                        var candidate=node.getAttribute('data-href')||node.getAttribute('data-url')||node.getAttribute('formaction');
                        if(candidate){try{link=new URL(candidate,document.baseURI).href;}catch(e){}}
                    }
                    if(!image && node.tagName==='IMG') image=node.currentSrc||node.src;
                    node=node.parentElement;
                }
                return (link||image) ? JSON.stringify({link:link||'',image:image||''}) : null;
            })()""".trimIndent()
            targetWeb.evaluateJavascript(script) { value ->
                val payload = try {
                    val decoded = JSONTokener(value ?: "null").nextValue() as? String
                    decoded?.let { JSONObject(it) }
                } catch (_: Exception) { null }
                val link = payload?.optString("link").orEmpty()
                val image = payload?.optString("image").orEmpty()
                if (link.isNotBlank() || image.isNotBlank()) {
                    runOnUiThread { showLongPressActions(link, image) }
                }
            }
            true
        }
        tabs.add(BrowserTab(web, url = url))
        selectTab(tabs.lastIndex)
        if (url != "about:blank") web.loadUrl(url)
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
        val dialog = Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = ColorDrawable(0xE6000000.toInt())
        }
        val heading = TextView(this).apply {
            text = "标签页"
            textSize = 17f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        }
        panel.addView(heading, LinearLayout.LayoutParams(-1, -2))

        val list = ScrollView(this)
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabs.forEachIndexed { index, tab ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(5), dp(4), dp(5))
                setBackgroundColor(if (index == current) 0x22FFFFFF else Color.TRANSPARENT)
            }
            val label = TextView(this).apply {
                text = (index + 1).toString() + "  " + tab.title.take(28)
                textSize = 14f
                setTextColor(white)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                isClickable = true
                setOnClickListener { dialog.dismiss(); selectTab(index) }
            }
            row.addView(label, LinearLayout.LayoutParams(0, dp(42), 1f))
            val close = TextView(this).apply {
                text = "×"
                textSize = 23f
                setTextColor(white)
                gravity = Gravity.CENTER
                setOnClickListener { dialog.dismiss(); closeTab(index) }
            }
            row.addView(close, LinearLayout.LayoutParams(dp(40), dp(42)))
            rows.addView(row)
        }
        list.addView(rows)
        panel.addView(list, LinearLayout.LayoutParams(-1, dp( minOf(260, maxOf(64, tabs.size * 52)) )))
        val newTab = menuAction("新建标签页") {
            dialog.dismiss()
            addTab("about:blank")
        }
        panel.addView(newTab, LinearLayout.LayoutParams(-1, dp(44)))
        val closeCurrent = menuAction("关闭当前标签") {
            dialog.dismiss()
            closeTab(current)
        }
        panel.addView(closeCurrent, LinearLayout.LayoutParams(-1, dp(40)))
        val cancel = menuAction("取消") { dialog.dismiss() }
        panel.addView(cancel, LinearLayout.LayoutParams(-1, dp(38)))

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(panel)
        dialog.setCancelable(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.62f)
            setLayout((resources.displayMetrics.widthPixels * 0.84f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.84f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun menuAction(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(white)
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
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
        text = label
        textSize = 22f
        gravity = Gravity.CENTER
        setTextColor(white)
        isClickable = true
        isFocusable = true
        background = ColorDrawable(black)
    }
    private fun addressBackground() = GradientDrawable().apply {
        setColor(black)
        setStroke(dp(1), 0x66FFFFFF)
        cornerRadius = 0f
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


    private fun showLongPressActions(link: String, image: String) {
        val actions = mutableListOf<String>()
        val urls = mutableListOf<String>()
        if (link.isNotBlank()) { actions.add("在新标签页打开链接"); urls.add(link) }
        if (image.isNotBlank()) { actions.add("Google 搜图"); urls.add("https://lens.google.com/uploadbyurl?url=" + URLEncoder.encode(image, "UTF-8")) }
        val copyTarget = image.ifBlank { link }
        if (copyTarget.isNotBlank()) { actions.add("复制地址"); urls.add(copyTarget) }
        if (actions.isEmpty()) return
        android.app.AlertDialog.Builder(this)
            .setItems(actions.toTypedArray()) { _, which ->
                when (actions[which]) {
                    "在新标签页打开链接" -> addTab(urls[which])
                    "Google 搜图" -> addTab(urls[which])
                    "复制地址" -> {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("URL", urls[which]))
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    
}
}
