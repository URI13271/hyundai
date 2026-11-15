package drivetalk.backend

import java.security.SecureRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val random = SecureRandom()

// 덱 파일을 읽는다. 질문은 코드에 넣지 않는다.
fun loadDeck(text: String): List<Card> {
    val deck = try {
        Json.decodeFromString<List<Card>>(text)
    } catch (err: IllegalArgumentException) {
        throw IllegalArgumentException("deck: ${err.message}")
    }
    require(deck.isNotEmpty()) { "deck: empty" }
    return deck
}

// 한 판은 10장이고 시작마다 섞는다. 섞인 순서는 그 판이 끝날 때까지 그대로다.
fun deal(deck: List<Card>): List<Card> = deck.shuffled(random).take(ROUND)

internal fun newId(): String {
    val bytes = ByteArray(4)
    random.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

// 한 판. 파일에 남는 모양도 이것이다.
@Serializable
class Session(
    var id: String,
    var screen: Screen,
    var index: Int,
    val deck: List<Card>,
    val joined: MutableList<String>,
    val picks: MutableMap<String, Choice>,
    val log: MutableList<LogEntry>,
) {
    companion object {
        fun open(id: String, deck: List<Card>) = Session(
            id = id,
            screen = Screen.SHUFFLE,
            index = 0,
            deck = deck,
            joined = SEATS.toMutableList(),
            picks = mutableMapOf(),
            log = mutableListOf(),
        )
    }

    fun copy() = Session(id, screen, index, deck, joined.toMutableList(), picks.toMutableMap(), log.toMutableList())

    fun view() = View(
        id = id,
        screen = screen,
        index = index,
        deck = deck,
        seats = SEATS.map { SeatOut(it, isJoined(it), picks[it]) },
        counts = counts(),
        log = log.toList(),
    )

    fun toggleSeat(seat: String) {
        if (screen != Screen.SHUFFLE && screen != Screen.SEATS) throw ApiError.conflict("bad phase")
        if (seat !in SEATS) throw ApiError.badRequest("unknown seat")
        screen = Screen.SEATS
        if (!joined.remove(seat)) joined.add(seat)
    }

    fun begin() {
        if (screen != Screen.SHUFFLE && screen != Screen.SEATS) throw ApiError.conflict("bad phase")
        if (SEATS.none { isJoined(it) }) throw ApiError.unprocessable("no seats joined")
        picks.clear()
        index = 0
        screen = Screen.READ
    }

    // 섞기 또는 낭독이 끝났다.
    fun ready() {
        screen = when (screen) {
            Screen.SHUFFLE -> Screen.SEATS
            Screen.READ -> Screen.ANSWER
            else -> throw ApiError.conflict("bad phase")
        }
    }

    // token은 a, b, 또는 현재 장의 보기 문장이다.
    fun hear(token: String) {
        val seat = currentSeat() ?: throw ApiError.conflict("no current turn")
        val card = deck.getOrNull(index) ?: throw ApiError.conflict("no current turn")
        val choice = parseChoice(token, card) ?: throw ApiError.badRequest("bad choice")
        picks[seat] = choice
        screen = Screen.ANSWER
    }

    // 다음. 아직 고르지 않은 참여 좌석은 건너뛴다. 마지막 장이면 결과다.
    fun advance() {
        if (screen != Screen.READ && screen != Screen.ANSWER) throw ApiError.conflict("bad phase")
        val card = deck.getOrNull(index) ?: throw ApiError.conflict("bad phase")
        val counts = counts()
        log.add(LogEntry(card.tag, card.title, card.a, card.b, counts.a, counts.b))
        picks.clear()
        if (index + 1 >= deck.size) {
            screen = Screen.DONE
        } else {
            index += 1
            screen = Screen.READ
        }
    }

    fun replay() {
        if (screen != Screen.READ && screen != Screen.ANSWER) throw ApiError.conflict("bad phase")
        screen = Screen.READ
    }

    private fun isJoined(seat: String) = seat in joined

    private fun counts(): Counts {
        val chosen = SEATS.filter { isJoined(it) }.mapNotNull { picks[it] }
        return Counts(chosen.count { it == Choice.A }, chosen.count { it == Choice.B })
    }

    // 차례는 운전석, 조수석, 뒷좌석 왼쪽, 뒷좌석 오른쪽 중 아직 고르지 않은 첫 참여 자리다.
    private fun currentSeat(): String? {
        if (screen != Screen.READ && screen != Screen.ANSWER) return null
        return SEATS.firstOrNull { isJoined(it) && it !in picks }
    }
}

private fun parseChoice(token: String, card: Card): Choice? = when (token.trim()) {
    "a", "A" -> Choice.A
    "b", "B" -> Choice.B
    card.a -> Choice.A
    card.b -> Choice.B
    else -> null
}
