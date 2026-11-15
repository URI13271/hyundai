package drivetalk.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
private data class SeatBody(val id: String)

@Serializable
private data class HearBody(val choice: String)

@Serializable
private data class ErrorBody(val error: String)

// status는 HTTP 상태 코드와 같다. body는 JSON이고, 204면 비어 있다.
data class Response(val status: Int, val body: String) {
    // 화면이 한 번에 읽는 모양: {"status":200,"body":{...}}
    fun toJson() = """{"status":$status,"body":${body.ifEmpty { "null" }}}"""
}

// BACKEND.md의 API. 앱 안에서는 화면이 call을 바로 부르고, 주소와 응답은 HTTP일 때와 같다.
//
//   POST   /v1/sessions
//   GET    /v1/sessions/:id
//   POST   /v1/sessions/:id/seats     { "id": "driver" }
//   POST   /v1/sessions/:id/begin
//   POST   /v1/sessions/:id/hear      { "choice": "a" }
//   POST   /v1/sessions/:id/next
//   POST   /v1/sessions/:id/replay
//   POST   /v1/sessions/:id/ready
//   DELETE /v1/sessions/:id
class Api(private val store: Store, private val deck: List<Card>) {
    private val json = Json { ignoreUnknownKeys = true }

    fun call(method: String, path: String, body: String = ""): Response = try {
        route(method.uppercase(), path.trim('/').split('/'), body)
    } catch (err: ApiError) {
        Response(err.status, json.encodeToString(ErrorBody.serializer(), ErrorBody(err.error)))
    }

    private fun route(method: String, parts: List<String>, body: String): Response {
        if (parts.size < 2 || parts[0] != "v1" || parts[1] != "sessions") throw ApiError.notFound()
        val id = parts.getOrNull(2)
        val step = parts.getOrNull(3)
        if (parts.size > 4) throw ApiError.notFound()

        return when {
            id == null && method == "POST" -> ok(store.create(deal(deck)))
            id == null -> throw ApiError.notFound()
            step == null && method == "GET" -> ok(store.get(id))
            step == null && method == "DELETE" -> {
                store.delete(id)
                Response(204, "")
            }
            method != "POST" -> throw ApiError.notFound()
            step == "seats" -> {
                val seat = read<SeatBody>(body).id
                ok(store.update(id) { it.toggleSeat(seat) })
            }
            step == "hear" -> {
                val choice = read<HearBody>(body).choice
                ok(store.update(id) { it.hear(choice) })
            }
            step == "begin" -> ok(store.update(id) { it.begin() })
            step == "next" -> ok(store.update(id) { it.advance() })
            step == "replay" -> ok(store.update(id) { it.replay() })
            step == "ready" -> ok(store.update(id) { it.ready() })
            else -> throw ApiError.notFound()
        }
    }

    private fun ok(session: Session) = Response(200, json.encodeToString(View.serializer(), session.view()))

    private inline fun <reified T> read(body: String): T = try {
        json.decodeFromString<T>(body)
    } catch (err: SerializationException) {
        throw ApiError.badRequest("bad body")
    }
}
