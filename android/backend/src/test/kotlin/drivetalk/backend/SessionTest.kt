package drivetalk.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// backend/src/session.rs의 테스트를 그대로 옮겼다.
class SessionTest {
    private fun card(tag: String) = Card(tag, "q-$tag", "a-$tag", "b-$tag")

    private fun opened() = Session.open("abcd1234", (0 until 3).map { card(it.toString()) })

    private fun refused(status: Int, error: String, block: () -> Unit) {
        val err = assertFailsWith<ApiError> { block() }
        assertEquals(status, err.status)
        assertEquals(error, err.error)
    }

    @Test
    fun dealKeepsTen() {
        val cards = (0 until 12).map { card(it.toString()) }
        val dealt = deal(cards)
        assertEquals(10, dealt.size)
        assertTrue(cards.containsAll(dealt))
        assertEquals(10, dealt.toSet().size)
    }

    @Test
    fun viewListsFourSeatsInOrder() {
        val view = opened().view()
        assertEquals(SEATS, view.seats.map { it.id })
        assertTrue(view.seats.all { it.joined && it.choice == null })
        assertEquals(Counts(0, 0), view.counts)
        assertEquals(Screen.SHUFFLE, view.screen)
    }

    @Test
    fun unknownSeatDoesNotLeaveShuffle() {
        val session = opened()
        refused(400, "unknown seat") { session.toggleSeat("trunk") }
        assertEquals(Screen.SHUFFLE, session.screen)
    }

    @Test
    fun beginRejectsEmptySeats() {
        val session = opened()
        SEATS.forEach { session.toggleSeat(it) }
        refused(422, "no seats joined") { session.begin() }
        assertEquals(Screen.SEATS, session.screen)
    }

    @Test
    fun hearWithoutTurnConflicts() {
        refused(409, "no current turn") { opened().hear("a") }
    }

    @Test
    fun badChoiceDoesNotConsumeTurn() {
        val session = opened()
        session.begin()
        refused(400, "bad choice") { session.hear("nope") }
        assertTrue(session.view().seats.all { it.choice == null })
    }

    @Test
    fun turnSkipsUnjoinedAndTaken() {
        val session = opened()
        session.toggleSeat("driver")
        session.toggleSeat("rear_left")
        session.begin()
        session.hear("a")
        session.hear("b-0")
        val view = session.view()
        assertEquals(Choice.A, view.seats[1].choice)
        assertEquals(Choice.B, view.seats[3].choice)
        assertEquals(Counts(1, 1), view.counts)
        refused(409, "no current turn") { session.hear("a") }
    }

    @Test
    fun replayKeepsPicks() {
        val session = opened()
        session.begin()
        session.hear("A")
        session.replay()
        assertEquals(Screen.READ, session.screen)
        assertEquals(Choice.A, session.view().seats[0].choice)
        session.hear("b")
        assertEquals(Choice.B, session.view().seats[1].choice)
    }

    @Test
    fun advanceSkipsRemainingAndFinishes() {
        val session = opened()
        session.begin()
        session.hear("a-0")
        session.advance()
        assertEquals(Screen.READ, session.screen)
        assertEquals(1, session.view().index)
        assertEquals("a-0", session.view().log[0].aLabel)
        assertEquals(1, session.view().log[0].a)
        assertEquals(0, session.view().log[0].b)
        assertTrue(session.view().seats.all { it.choice == null })
        session.advance()
        session.advance()
        assertEquals(Screen.DONE, session.screen)
        assertEquals(3, session.view().log.size)
        refused(409, "bad phase") { session.advance() }
    }

    @Test
    fun readyAdvancesShuffleAndReadOnly() {
        val session = opened()
        session.ready()
        assertEquals(Screen.SEATS, session.screen)
        refused(409, "bad phase") { session.ready() }
        session.begin()
        session.ready()
        assertEquals(Screen.ANSWER, session.screen)
    }

    @Test
    fun deckFileMustHaveCards() {
        assertEquals("산", loadDeck("""[{"tag":"여행","title":"여행을 간다면 어디로?","a":"산","b":"바다"}]""")[0].a)
        assertFailsWith<IllegalArgumentException> { loadDeck("[]") }
        assertFailsWith<IllegalArgumentException> { loadDeck("nope") }
        assertNull(opened().view().seats[0].choice)
    }
}
