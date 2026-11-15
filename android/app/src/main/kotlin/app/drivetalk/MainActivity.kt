package app.drivetalk

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

private const val BUNDLED = "file:///android_asset/index.html"
private const val URL_KEY = "url"

// 화면 하나에 WebView 하나.
// standalone은 앱 안에 든 화면을 띄우고 코틀린 백엔드를 잇는다.
// client는 적어 둔 화면 서버 주소를 연다.
class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var prefs: SharedPreferences
    private var seats: VehicleSeats? = null
    private var setup = false

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("drive-talk", MODE_PRIVATE)

        web = WebView(this)
        web.setBackgroundColor(Color.TRANSPARENT)
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportZoom(false)
            textZoom = 100
            // 화면 쪽 app.js가 이 표시를 보고 틀 없이 화면을 다 쓴다.
            userAgentString = "$userAgentString DriveTalkApp/2.0"
        }
        web.webViewClient = object : WebViewClient() {
            // 앱 안에 든 화면은 밖으로 나가지 않는다. 백엔드는 그 화면에만 열려 있다.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                BuildConfig.STANDALONE && !request.url.toString().startsWith("file:///android_asset/")

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!BuildConfig.STANDALONE && request.isForMainFrame) {
                    showSetup("화면 서버에 연결할 수 없어요. (${error.description})")
                }
            }
        }
        attachBackend(this, web)
        // 차량 착석 정보는 앱 안에 든 화면에만 준다. 밖의 화면 서버에는 열지 않는다.
        if (BuildConfig.STANDALONE) {
            seats = VehicleSeats(this, web).also {
                web.addJavascriptInterface(it, "DriveTalkVehicle")
                it.start()
            }
        }

        if (BuildConfig.STANDALONE) {
            showWeb(BUNDLED)
        } else {
            val url = prefs.getString(URL_KEY, null)
            if (url == null) showSetup(null) else showWeb(url)
        }
    }

    override fun onWindowFocusChanged(focused: Boolean) {
        super.onWindowFocusChanged(focused)
        if (focused) hideBars()
    }

    // 서버에 붙는 앱에서 뒤로는 주소 화면이다. 주소 화면에서 뒤로는 하던 화면이다.
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        when {
            BuildConfig.STANDALONE -> super.onBackPressed()
            !setup -> showSetup(null)
            prefs.getString(URL_KEY, null) != null -> showWeb(null)
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        seats?.stop()
        detach()
        web.destroy()
        super.onDestroy()
    }

    private fun showWeb(url: String?) {
        setup = false
        detach()
        setContentView(web)
        if (url != null) web.loadUrl(url)
    }

    private fun detach() {
        (web.parent as? ViewGroup)?.removeView(web)
    }

    private fun showSetup(problem: String?) {
        setup = true
        detach()
        val pad = dp(32)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }

        box.addView(TextView(this).apply {
            text = "화면 서버 주소"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        }, row())

        box.addView(TextView(this).apply {
            text = problem ?: "드라이브 토크 화면 서버(ruby bin/drive_talk_all)가 떠 있는 주소를 적어요."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }, row())

        val field = EditText(this).apply {
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            hint = "http://192.168.0.10:4567"
            setText(prefs.getString(URL_KEY, "http://"))
            setSelection(text.length)
        }
        box.addView(field, row())

        val connect = {
            var url = field.text.toString().trim()
            if (url.isNotEmpty() && url != "http://" && url != "https://") {
                if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
                prefs.edit().putString(URL_KEY, url).apply()
                showWeb(url)
            }
        }
        box.addView(Button(this).apply {
            text = "연결"
            setOnClickListener { connect() }
        }, row())
        field.setOnEditorActionListener { _, _, _ ->
            connect()
            true
        }
        setContentView(box)
    }

    private fun row() = LinearLayout.LayoutParams(dp(480), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(16)
    }

    private fun dp(value: Int) = Math.round(value * resources.displayMetrics.density)

    @Suppress("DEPRECATION")
    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = window.insetsController ?: return
            bars.hide(WindowInsets.Type.systemBars())
            bars.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }
}
