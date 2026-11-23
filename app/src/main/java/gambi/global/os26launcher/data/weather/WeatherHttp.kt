package gambi.global.os26launcher.data.weather

import gambi.global.os26launcher.domain.model.weather.WeatherError
import gambi.global.os26launcher.domain.model.weather.WeatherException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL

/**
 * G3e — một lệnh GET bằng `HttpURLConnection` (D4: không thêm thư viện). Chỉ https. Chạy trên luồng nền (người gọi chịu trách nhiệm).
 *
 * QUY TẮC BÍ MẬT: URL chứa `appid` nên lớp này KHÔNG BAO GIỜ log URL, thân phản hồi hay thông điệp ngoại lệ gốc; lỗi chỉ ra ngoài
 * dưới dạng [WeatherException] mang loại lỗi.
 */
internal object WeatherHttp {
    /** CHỈNH TAY. */
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_BODY_BYTES = 512 * 1024

    /** [spec] là chuỗi URL: `URL(...)` dựng trong try (B1) để chuỗi hỏng thành [WeatherException] thay vì thoát ra ngoài. */
    fun get(spec: String): String {
        val url = try {
            URL(spec)
        } catch (e: MalformedURLException) {
            throw WeatherException(WeatherError.SERVER) // cấu hình hỏng, không phải lỗi mạng
        }
        if (url.protocol != "https") throw WeatherException(WeatherError.NETWORK)
        val conn = try {
            url.openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw WeatherException(WeatherError.NETWORK)
        }
        try {
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) return readBody(conn.inputStream)
            // Chỉ cần mã; thân lỗi có thể nhắc khóa nên không đọc ra ngoài.
            throw WeatherException(
                when (code) {
                    401 -> WeatherError.INVALID_KEY
                    404 -> WeatherError.NOT_FOUND
                    429 -> WeatherError.RATE_LIMITED
                    else -> WeatherError.SERVER
                }
            )
        } catch (e: WeatherException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw WeatherException(WeatherError.NETWORK)
        } catch (e: IOException) {
            throw WeatherException(WeatherError.NETWORK)
        } catch (e: RuntimeException) {
            // HTTP stack của hãng máy có thể ném lỗi lạ (B1); mã chặn nên không có hủy coroutine ở đây.
            throw WeatherException(WeatherError.SERVER)
        } finally {
            conn.disconnect()
        }
    }

    private fun readBody(stream: InputStream): String {
        stream.use {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_BODY_BYTES) throw WeatherException(WeatherError.SERVER)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}
