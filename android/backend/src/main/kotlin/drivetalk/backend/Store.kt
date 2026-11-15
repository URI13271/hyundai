package drivetalk.backend

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val ID = Regex("^[0-9a-f]{8}$")
private val pretty = Json { prettyPrint = true }

// 판을 파일로 남긴다. root/sessions/<id>.json.
// 앱이나 헤드유닛을 다시 켜도 하던 판이 그대로다.
class Store(private val root: File) {
    private val dir = File(root, "sessions")
    private val sessions = HashMap<String, Session>()

    init {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        for (file in dir.listFiles().orEmpty()) {
            val id = file.name.removeSuffix(".json")
            if (id == file.name || !ID.matches(id)) continue
            try {
                val session = Json.decodeFromString<Session>(file.readText())
                session.id = id
                sessions[id] = session
            } catch (err: IOException) {
                System.err.println("skip session $id: ${err.message}")
            } catch (err: SerializationException) {
                System.err.println("skip session $id: ${err.message}")
            }
        }
    }

    val size: Int
        @Synchronized get() = sessions.size

    @Synchronized
    fun create(deck: List<Card>): Session {
        var id = newId()
        while (id in sessions) id = newId()
        val session = Session.open(id, deck)
        write(session)
        sessions[id] = session
        return session.copy()
    }

    @Synchronized
    fun get(id: String): Session = find(id).copy()

    // 거절된 변경은 판에 남지 않는다.
    @Synchronized
    fun update(id: String, change: (Session) -> Unit): Session {
        val session = find(id).copy()
        change(session)
        write(session)
        sessions[id] = session
        return session.copy()
    }

    @Synchronized
    fun delete(id: String) {
        find(id)
        val file = fileFor(id)
        if (file.exists() && !file.delete()) throw ApiError.store()
        sessions.remove(id)
    }

    private fun find(id: String): Session {
        if (!ID.matches(id)) throw ApiError.notFound()
        return sessions[id] ?: throw ApiError.notFound()
    }

    private fun fileFor(id: String) = File(dir, "$id.json")

    // 쓰다가 꺼져도 반쯤 쓴 파일이 남지 않게 옆에 쓰고 이름을 바꾼다.
    private fun write(session: Session) {
        try {
            val tmp = File(dir, "${session.id}.json.tmp")
            tmp.writeText(pretty.encodeToString(Session.serializer(), session))
            Files.move(tmp.toPath(), fileFor(session.id).toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (err: IOException) {
            throw ApiError.store()
        }
    }
}
