package gambi.global.os26launcher.domain.model

/** Vùng trên màn hình, toạ độ pixel. Tự định nghĩa thay vì dùng android.graphics.Rect để domain không phụ thuộc framework. */
data class ScreenRect(val left: Int, val top: Int, val right: Int, val bottom: Int)
