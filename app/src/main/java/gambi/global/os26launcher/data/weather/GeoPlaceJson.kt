package gambi.global.os26launcher.data.weather

import gambi.global.os26launcher.domain.model.weather.GeoPlace
import org.json.JSONObject

/** Dạng JSON của một [GeoPlace], dùng chung cho `weather.json` ([WeatherStore]) và `weather_places.json` ([WeatherPlacesStore]). Không log nội dung (có tọa độ). */
internal object GeoPlaceJson {
    fun write(p: GeoPlace): JSONObject = JSONObject().apply {
        put("n", p.displayName)
        put("la", p.lat)
        put("lo", p.lon)
        p.countryCode?.let { put("c", it) }
        p.admin?.let { put("a", it) }
    }

    /** Ném khi thiếu trường bắt buộc: người gọi bắt trong khối `load` của nó. */
    fun read(o: JSONObject): GeoPlace = GeoPlace(
        displayName = o.getString("n"),
        lat = o.getDouble("la"),
        lon = o.getDouble("lo"),
        countryCode = o.optString("c", "").ifBlank { null },
        admin = o.optString("a", "").ifBlank { null }
    )
}
