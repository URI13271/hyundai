package drivetalk.backend

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// backend/src/store.rs의 테스트와, 화면이 기대하는 응답 모양.
class StoreTest {
    private val dir: File = Files.createTempDirectory("drive-talk").toFile()

    private fun deck() = (0 until 2).map { Card(it.toString(), "q-$it", "산", "바다") }

    private fun gone(block: () -> Unit) = assertEquals(404, assertFailsWith<ApiError> { block() }.status)

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun reloadKeepsOpenRound() {
        val first = Store(dir)
        val id = first.create(deck()).id
        first.update(id) { it.begin() }
        first.update(id) { it.hear("산") }

        val session = Store(dir).get(id)
        assertEquals(Screen.ANSWER, session.screen)
        assertEquals(Choice.A, session.view().seats[0].choice)
        assertEquals("산", session.view().deck[0].a)
    }

    @Test
    fun deleteDoesNotComeBack() {
        val store = Store(dir)
        val id = store.create(deck()).id
        store.delete(id)
        gone { store.get(id) }
        gone { Store(dir).get(id) }
        gone { store.get("../secret") }
        gone { store.delete("../${SEATS[0]}") }
    }

    @Test
    fun refusedChangeIsNotKept() {
        val store = Store(dir)
        val id = store.create(deck()).id
        assertEquals(409, assertFailsWith<ApiError> { store.update(id) { it.hear("a") } }.status)
        assertEquals(Screen.SHUFFLE, store.get(id).screen)
        assertEquals(Screen.SHUFFLE, Store(dir).get(id).screen)
    }

    // 러스트 백엔드가 쓴 판 파일을 그대로 읽는다.
    @Test
    fun readsRustSessionFile() {
        File(dir, "sessions").mkdirs()
        File(dir, "sessions/0a1b2c3d.json").writeText(
            """
            {
              "id": "0a1b2c3d", "screen": "answer", "index": 0,
              "deck": [{ "tag": "여행", "title": "여행을 간다면 어디로?", "a": "산", "b": "바다" }],
              "joined": ["driver", "rear_left"],
              "picks": { "driver": "a" },
              "log": []
            }
            """.trimIndent()
        )
        val view = Store(dir).get("0a1b2c3d").view()
        assertEquals(Choice.A, view.seats[0].choice)
        assertEquals(listOf(true, false, true, false), view.seats.map { it.joined })
    }

    @Test
    fun apiAnswersLikeTheContract() {
        val api = Api(Store(dir), deck())
        val made = api.call("POST", "/v1/sessions")
        assertEquals(200, made.status)
        val view = Json.parseToJsonElement(made.body).jsonObject
        val id = view["id"]!!.jsonPrimitive.content
        assertEquals("shuffle", view["screen"]!!.jsonPrimitive.content)
        assertEquals(setOf("id", "screen", "index", "deck", "seats", "counts", "log"), view.keys)
        assertTrue(view["seats"]!!.jsonArray[0].jsonObject.containsKey("choice"))

        assertEquals(409, api.call("POST", "/v1/sessions/$id/hear", """{"choice":"a"}""").status)
        assertEquals(400, api.call("POST", "/v1/sessions/$id/seats", """{"id":"trunk"}""").status)
        assertEquals(400, api.call("POST", "/v1/sessions/$id/seats", "").status)
        assertEquals(200, api.call("POST", "/v1/sessions/$id/ready").status)
        assertEquals(409, api.call("POST", "/v1/sessions/$id/ready").status)
        SEATS.forEach { api.call("POST", "/v1/sessions/$id/seats", """{"id":"$it"}""") }
        val empty = api.call("POST", "/v1/sessions/$id/begin")
        assertEquals(422, empty.status)
        assertEquals("""{"error":"no seats joined"}""", empty.body)
        api.call("POST", "/v1/sessions/$id/seats", """{"id":"driver"}""")
        assertEquals(200, api.call("POST", "/v1/sessions/$id/begin").status)
        assertEquals(400, api.call("POST", "/v1/sessions/$id/hear", """{"choice":"nope"}""").status)
        val heard = Json.parseToJsonElement(api.call("POST", "/v1/sessions/$id/hear", """{"choice":"바다"}""").body)
        assertEquals(1, heard.jsonObject["counts"]!!.jsonObject["b"]!!.jsonPrimitive.int)
        assertEquals(200, api.call("POST", "/v1/sessions/$id/replay").status)
        assertEquals(200, api.call("POST", "/v1/sessions/$id/next").status)
        assertEquals(200, api.call("GET", "/v1/sessions/$id").status)
        assertEquals(404, api.call("GET", "/v1/sessions/ffffffff").status)
        assertEquals(404, api.call("GET", "/v1/nothing").status)
        assertEquals(Response(204, ""), api.call("DELETE", "/v1/sessions/$id"))
        assertEquals(404, api.call("GET", "/v1/sessions/$id").status)
        assertEquals("""{"status":204,"body":null}""", Response(204, "").toJson())
    }
}
