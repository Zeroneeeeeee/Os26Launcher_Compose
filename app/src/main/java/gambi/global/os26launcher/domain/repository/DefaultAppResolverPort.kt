package gambi.global.os26launcher.domain.repository

import gambi.global.os26launcher.domain.model.DockRole

/**
 * Cổng ở domain để [gambi.global.os26launcher.domain.usecase.PickDefaultDockUseCase] không phụ
 * thuộc trực tiếp vào data (luật phụ thuộc, project_structure.md mục 2). (M3 Task 4, DD Task 2)
 */
interface DefaultAppResolverPort {
    /**
     * Tên package ứng viên cho [role], thứ tự từ chắc nhất tới kém nhất, không trùng, có thể rỗng.
     * KHÔNG phải component để mở: use case tự lọc theo danh sách app và mở bằng khóa LAUNCHER.
     */
    fun candidates(role: DockRole): List<String>
}
