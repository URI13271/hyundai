package drivetalk.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// BACKEND.md의 JSON 모양 그대로다. 러스트 백엔드(backend/src/session.rs)와 이름이 같다.

val SEATS = listOf("driver", "passenger", "rear_left", "rear_right")
const val ROUND = 10

@Serializable
enum class Screen {
    @SerialName("home") HOME,
    @SerialName("shuffle") SHUFFLE,
    @SerialName("seats") SEATS,
    @SerialName("read") READ,
    @SerialName("answer") ANSWER,
    @SerialName("done") DONE,
}

@Serializable
enum class Choice {
    @SerialName("a") A,
    @SerialName("b") B,
}

@Serializable
data class Card(val tag: String, val title: String, val a: String, val b: String)

@Serializable
data class LogEntry(
    val tag: String,
    val title: String,
    @SerialName("a_label") val aLabel: String,
    @SerialName("b_label") val bLabel: String,
    val a: Int,
    val b: Int,
)

@Serializable
data class SeatOut(val id: String, val joined: Boolean, val choice: Choice?)

@Serializable
data class Counts(val a: Int, val b: Int)

@Serializable
data class View(
    val id: String,
    val screen: Screen,
    val index: Int,
    val deck: List<Card>,
    val seats: List<SeatOut>,
    val counts: Counts,
    val log: List<LogEntry>,
)

// 거절. status는 HTTP 상태 코드와 같은 뜻이다.
class ApiError(val status: Int, val error: String) : Exception(error) {
    companion object {
        fun notFound() = ApiError(404, "not found")
        fun conflict(error: String) = ApiError(409, error)
        fun unprocessable(error: String) = ApiError(422, error)
        fun badRequest(error: String) = ApiError(400, error)
        fun store() = ApiError(500, "store failed")
    }
}
