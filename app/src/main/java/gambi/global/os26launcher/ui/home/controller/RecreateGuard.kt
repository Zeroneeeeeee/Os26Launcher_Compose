package gambi.global.os26launcher.ui.home.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * R1: chỗ chung cho các kiểm tra "thứ gắn lúc dựng View vừa đổi ở Cài đặt thì dựng lại Activity" (M8a Task 4). Cài đặt chỉ GHI;
 * `HomeActivity.onResume` gọi [onResume], guard chạy các check đã [add] theo thứ tự, gặp check đầu tiên báo đổi thì `recreate()`
 * đúng một lần. Hôm nay có một check (mức kính của dock, `DockController.glassOverrideChanged`); G2b thêm check kiểu icon bằng một dòng.
 */
internal class RecreateGuard(
    private val scope: CoroutineScope,
    private val recreate: () -> Unit
) {
    private val checks = mutableListOf<suspend () -> Boolean>()

    fun add(check: suspend () -> Boolean) {
        checks += check
    }

    fun onResume() {
        scope.launch {
            for (check in checks) {
                if (check()) {
                    recreate()
                    return@launch
                }
            }
        }
    }
}
