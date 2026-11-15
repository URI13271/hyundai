package app.drivetalk

import android.app.Activity
import android.webkit.JavascriptInterface
import android.webkit.WebView
import drivetalk.backend.Api
import drivetalk.backend.Store
import drivetalk.backend.loadDeck
import java.io.File

// 코틀린 백엔드를 화면에 잇는다. 화면(assets/game.js)은 DriveTalkBackend.call로 부른다.
// 덱은 앱에 든 deck.json이고, 판은 앱 전용 저장 공간의 data/sessions에 남는다.
fun attachBackend(activity: Activity, web: WebView) {
    val deck = activity.assets.open("deck.json").bufferedReader().use { it.readText() }
    val api = Api(Store(File(activity.filesDir, "data")), loadDeck(deck))
    web.addJavascriptInterface(Bridge(api), "DriveTalkBackend")
}

private class Bridge(private val api: Api) {
    // 돌려주는 것은 {"status":200,"body":{...}} 한 줄이다.
    @JavascriptInterface
    fun call(method: String, path: String, body: String): String = api.call(method, path, body).toJson()
}
