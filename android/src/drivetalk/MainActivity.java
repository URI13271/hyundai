package drivetalk;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

// 화면 하나에 WebView 하나.
// Config.STANDALONE이면 앱 안에 든 화면을 띄운다. 아니면 적어 둔 화면 서버 주소를 연다.
public class MainActivity extends Activity {
    private static final String BUNDLED = "file:///android_asset/index.html";
    private static final String URL_KEY = "url";

    private WebView web;
    private SharedPreferences prefs;
    private boolean setup;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("drive-talk", MODE_PRIVATE);

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(false);
        settings.setTextZoom(100);
        // 화면 쪽 app.js가 이 표시를 보고 틀 없이 화면을 다 쓴다.
        settings.setUserAgentString(settings.getUserAgentString() + " DriveTalkApp/1.0");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!Config.STANDALONE && request.isForMainFrame()) {
                    showSetup("화면 서버에 연결할 수 없어요. (" + error.getDescription() + ")");
                }
            }
        });

        if (Config.STANDALONE) {
            showWeb(BUNDLED);
        } else {
            String url = prefs.getString(URL_KEY, null);
            if (url == null) showSetup(null);
            else showWeb(url);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) hideBars();
    }

    // 서버에 붙는 앱에서 뒤로는 주소 화면이다. 주소 화면에서 뒤로는 하던 화면이다.
    @Override
    public void onBackPressed() {
        if (Config.STANDALONE) {
            super.onBackPressed();
        } else if (!setup) {
            showSetup(null);
        } else if (prefs.getString(URL_KEY, null) != null) {
            showWeb(null);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        detach();
        web.destroy();
        super.onDestroy();
    }

    private void showWeb(String url) {
        setup = false;
        detach();
        setContentView(web);
        if (url != null) web.loadUrl(url);
    }

    private void detach() {
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
    }

    private void showSetup(String problem) {
        setup = true;
        detach();
        int pad = dp(32);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("화면 서버 주소");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        box.addView(title, row());

        TextView note = new TextView(this);
        note.setText(problem != null ? problem
                : "드라이브 토크 화면 서버(ruby bin/drive_talk_all)가 떠 있는 주소를 적어요.");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        box.addView(note, row());

        final EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setImeOptions(EditorInfo.IME_ACTION_GO);
        field.setHint("http://192.168.0.10:4567");
        field.setText(prefs.getString(URL_KEY, "http://"));
        field.setSelection(field.getText().length());
        box.addView(field, row());

        Button go = new Button(this);
        go.setText("연결");
        box.addView(go, row());

        Runnable connect = () -> {
            String url = field.getText().toString().trim();
            if (url.isEmpty() || url.equals("http://") || url.equals("https://")) return;
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
            prefs.edit().putString(URL_KEY, url).apply();
            showWeb(url);
        };
        go.setOnClickListener(view -> connect.run());
        field.setOnEditorActionListener((view, action, event) -> {
            connect.run();
            return true;
        });
        setContentView(box);
    }

    private LinearLayout.LayoutParams row() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(480), ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(16);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController bars = getWindow().getInsetsController();
            if (bars == null) return;
            bars.hide(WindowInsets.Type.systemBars());
            bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
