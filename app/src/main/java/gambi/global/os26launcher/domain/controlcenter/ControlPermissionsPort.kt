package gambi.global.os26launcher.domain.controlcenter

import gambi.global.os26launcher.domain.model.controlcenter.ControlPermission

/** G3d: kiểm tra quyền đặc biệt. Không có callback hệ thống cho phần lớn quyền này, nên người gọi đọc lại ở onResume. */
interface ControlPermissionsPort {
    fun isGranted(permission: ControlPermission): Boolean

    /** Quyền cần hỏi trên máy này (vd [ControlPermission.BLUETOOTH_CONNECT] chỉ ở API 31-32). */
    fun applicable(): List<ControlPermission>

    fun missingRequired(): List<ControlPermission> =
        applicable().filter { it.required && !isGranted(it) }
}
