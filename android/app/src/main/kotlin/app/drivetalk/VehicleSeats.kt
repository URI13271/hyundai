package app.drivetalk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "DriveTalkVehicle"

// 화면의 좌석 이름과 Pleos 차량 SDK의 좌석 자리. 왼쪽 운전석 차량 기준이다.
private val AREAS = linkedMapOf(
    "driver" to SeatValue.ROW_1_LEFT,
    "passenger" to SeatValue.ROW_1_RIGHT,
    "rear_left" to SeatValue.ROW_2_LEFT,
    "rear_right" to SeatValue.ROW_2_RIGHT,
)

// Pleos Connect의 착석 센서를 화면에 잇는다. 화면(assets/game.js)은 DriveTalkVehicle.occupancy로 읽고,
// 착석이 바뀌면 window.onSeatOccupancy()로 알림을 받는다.
// 차량 서비스가 없는 기기에서는 아무 자리도 모른다고 답하고, 화면은 손으로 고르는 그대로 돈다.
class VehicleSeats(context: Context, private val web: WebView) {
    private val vehicle = Vehicle(context.applicationContext)
    private val occupied = ConcurrentHashMap<String, Boolean>()
    private val listeners = mutableMapOf<String, SeatOccupancyListener>()
    private var started = false

    fun start() {
        try {
            vehicle.initialize()
            started = true
        } catch (err: Exception) {
            Log.w(TAG, "vehicle initialize failed", err)
            return
        }
        val seat = vehicle.getSeat()
        AREAS.forEach { (id, value) ->
            val area = SeatArea(value)
            seat.getCurrentSeatOccupancy(
                area,
                { now -> update(id, now) },
                { err -> Log.w(TAG, "occupancy $id failed", err) },
            )
            val listener = object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(now: Boolean?) = update(id, now)
                override fun onFailed(e: Exception) {
                    Log.w(TAG, "occupancy listener $id failed", e)
                }
            }
            try {
                seat.registerSeatOccupancy(area, listener)
                listeners[id] = listener
            } catch (err: Exception) {
                Log.w(TAG, "register $id failed", err)
            }
        }
    }

    fun stop() {
        if (!started) return
        val seat = vehicle.getSeat()
        listeners.forEach { (id, listener) ->
            try {
                seat.unregisterSeatOccupancy(SeatArea(AREAS.getValue(id)), listener)
            } catch (err: Exception) {
                Log.w(TAG, "unregister $id failed", err)
            }
        }
        listeners.clear()
        vehicle.release()
        started = false
    }

    private fun update(id: String, now: Boolean?) {
        val before = occupied[id]
        if (now == null) occupied.remove(id) else occupied[id] = now
        Log.i(TAG, "seat $id occupied=$now")
        if (before != now) {
            web.post { web.evaluateJavascript("window.onSeatOccupancy && window.onSeatOccupancy()", null) }
        }
    }

    // {"driver":true,"passenger":false} 꼴이다. 모르는 자리는 빠진다.
    @JavascriptInterface
    fun occupancy(): String = AREAS.keys
        .mapNotNull { id -> occupied[id]?.let { "\"$id\":$it" } }
        .joinToString(",", "{", "}")
}
