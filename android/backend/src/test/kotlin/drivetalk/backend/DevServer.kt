package drivetalk.backend

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress

// 기기 없이 코틀린 백엔드를 HTTP로 띄운다. 앱에는 들어가지 않는다.
// 러스트 API(backend/)와 주소, 환경변수가 같아 화면 서버가 그대로 붙는다.
//   PORT(기본 8080), BIND(기본 127.0.0.1), DRIVE_TALK_DECK(기본 deck.json), DRIVE_TALK_DATA(기본 data)
fun main() {
    val port = (System.getenv("PORT") ?: "8080").toInt()
    val bind = System.getenv("BIND") ?: "127.0.0.1"
    val deck = loadDeck(File(System.getenv("DRIVE_TALK_DECK") ?: "deck.json").readText())
    val store = Store(File(System.getenv("DRIVE_TALK_DATA") ?: "data"))
    val api = Api(store, deck)

    val server = HttpServer.create(InetSocketAddress(bind, port), 0)
    server.createContext("/") { exchange ->
        exchange.responseHeaders.add("access-control-allow-origin", "*")
        exchange.responseHeaders.add("access-control-allow-methods", "GET, POST, DELETE")
        exchange.responseHeaders.add("access-control-allow-headers", "content-type")
        if (exchange.requestMethod == "OPTIONS") {
            exchange.sendResponseHeaders(204, -1)
        } else {
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val res = api.call(exchange.requestMethod, exchange.requestURI.path, body)
            val bytes = res.body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("content-type", "application/json")
            exchange.sendResponseHeaders(res.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
        }
        exchange.close()
    }
    server.start()
    System.err.println("restored ${store.size} session(s)")
    System.err.println("listening on http://$bind:$port")
}
