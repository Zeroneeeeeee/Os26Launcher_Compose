package gambi.global.os26launcher.domain.model.weather

/** Vị trí thô, đã làm tròn 2 số thập phân (~1 km) ngay khi lấy: tọa độ là dữ liệu cá nhân (D3). */
data class CoarseFix(val lat: Double, val lon: Double)
