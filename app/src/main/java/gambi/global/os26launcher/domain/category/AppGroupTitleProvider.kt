package gambi.global.os26launcher.domain.category

import gambi.global.os26launcher.domain.model.AppGroupKind

/**
 * AG mở lại (2026-11-26, QĐ 16) — tên thư mục của mỗi [AppGroupKind], theo ngôn ngữ app lúc gọi. Một nguồn chung cho bố cục lần đầu
 * (`LayoutReconciler.seedIfEmpty` ghi tên thư mục) và Cài đặt → Chung (so tên để biết nhóm đã gom), nên hai nơi luôn khớp chữ.
 */
interface AppGroupTitleProvider {
    fun title(kind: AppGroupKind): String
}
