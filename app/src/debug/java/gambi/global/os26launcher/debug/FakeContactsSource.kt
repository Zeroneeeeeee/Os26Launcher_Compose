package gambi.global.os26launcher.debug

import gambi.global.os26launcher.domain.model.ContactEntry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CP — CHỈ BẢN DEBUG. Danh bạ giả sinh trong RAM, xác định (không ngẫu nhiên): bộ `small` (16 người, ca định dạng số) và bộ
 * `load` (2000 người, 0 đến 6 số mỗi người). Không chạm ContactsContract, không cần quyền, không ghi gì ra đĩa.
 * Số trong bộ giả là dữ liệu thử, vẫn không in ra log.
 */
@Singleton
class FakeContactsSource @Inject constructor() {

    enum class Mode {
        OFF, SMALL, LOAD;

        companion object {
            fun parse(s: String?): Mode = entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: OFF
        }
    }

    /** [phones]: số đầu tiên là số mặc định. */
    class Person(val entry: ContactEntry, val phones: List<String>)

    fun people(mode: Mode): List<Person> = when (mode) {
        Mode.OFF -> emptyList()
        Mode.SMALL -> small
        Mode.LOAD -> load
    }

    private fun p(id: Long, name: String, vararg phones: String) =
        Person(ContactEntry(id, "cp-$id", name, null), phones.toList())

    private val small: List<Person> = listOf(
        p(1, "ZZTEST An A", "0912345678"),
        p(2, "ZZTEST Bình B", "+84 912 345 678"),
        p(3, "ZZTEST Chi C", "091-234-5678", "0987 654 321"),
        p(4, "ZZTEST Dũng D", "(091) 2345678"),
        p(5, "ZZTEST Đức E", "0912.345.600"),
        p(6, "ZZTEST Em F", "0084 912 345 999"),
        p(7, "ZZTEST Giang G", "+1 (650) 555-0134"),
        p(8, "ZZTEST Hà H", "024 3825 1234"),
        p(9, "ZZTEST Khoa K"),
        p(10, "ZZTEST Lan L", "0901 000 003", "0901 000 004"),
        p(11, "ZZTEST Mai M", "84912345678"),
        p(12, "ZZTEST Nam N", "+84 (0) 912 000 111"),
        p(13, "ZZTEST Oanh O", "0905 111 222"),
        p(14, "ZZTEST Phúc P", "0905 111 222"),
        p(15, "ZZTEST Quân Q", "0912 345 678"),
        p(16, "ZZTEST Sơn S", "0933 555 777", "+84 944 555 888")
    )

    private val load: List<Person> by lazy {
        val ho = listOf("Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Đặng", "Đỗ", "Bùi", "Vũ", "Dương", "Ngô", "Lý")
        val hoPlain = listOf("Nguyen", "Tran", "Le", "Pham", "Hoang", "Dang", "Do", "Bui", "Vu", "Duong", "Ngo", "Ly")
        val dem = listOf("Văn", "Thị", "Minh", "Đức", "Quốc", "Hữu")
        val demPlain = listOf("Van", "Thi", "Minh", "Duc", "Quoc", "Huu")
        val ten = listOf("An", "Bình", "Cường", "Dũng", "Hà", "Khánh", "Linh", "Mai", "Nam", "Phương")
        val tenPlain = listOf("An", "Binh", "Cuong", "Dung", "Ha", "Khanh", "Linh", "Mai", "Nam", "Phuong")
        List(2000) { i ->
            val id = i + 1L
            val accent = id % 2 == 1L
            val name = "ZZTEST " + (if (accent) ho else hoPlain)[i % ho.size] + " " +
                (if (accent) dem else demPlain)[(i / 12) % dem.size] + " " +
                (if (accent) ten else tenPlain)[(i / 7) % ten.size] + " " + id.toString().padStart(4, '0')
            val count = (i * 7 + 3) % 7 // 0..6, trung bình 3
            Person(ContactEntry(id, "cp-$id", name, null), List(count) { k -> phone(id, k) })
        }
    }

    /** 10 định dạng quay vòng; số xác định theo (id, k). */
    private fun phone(id: Long, k: Int): String {
        val body = (10_000_000L + id * 7 + k * 1_300_001L).toString().padStart(8, '0').takeLast(8)
        val a = "9" + body.substring(0, 2)
        val b = body.substring(2, 5)
        val c = body.substring(5, 8)
        return when ((id + k).toInt() % 10) {
            0 -> "0$a$b$c"
            1 -> "0$a $b $c"
            2 -> "0$a-$b-$c"
            3 -> "0$a.$b.$c"
            4 -> "+84 $a $b $c"
            5 -> "+84$a$b$c"
            6 -> "(0$a) $b$c"
            7 -> "0084 $a $b $c"
            8 -> "(024) 3825 ${body.substring(4, 8)}"
            else -> "+1 (650) 555-${body.substring(4, 8)}"
        }
    }
}
