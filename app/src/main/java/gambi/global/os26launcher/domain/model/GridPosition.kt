package gambi.global.os26launcher.domain.model

/** Ô trong lưới, tính từ góc trên-trái. spanX/spanY để sẵn cho widget và thư mục (B8). */
data class GridPosition(val x: Int, val y: Int, val spanX: Int = 1, val spanY: Int = 1)
