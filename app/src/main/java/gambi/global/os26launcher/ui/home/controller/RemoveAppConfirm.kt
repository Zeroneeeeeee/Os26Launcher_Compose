package gambi.global.os26launcher.ui.home.controller

import android.content.Context
import gambi.global.os26launcher.domain.model.ComponentKey
import gambi.global.os26launcher.ui.home.HomeViewModel
import gambi.global.os26launcher.ui.home.IconMenuAction
import androidx.activity.ComponentActivity
import android.content.ContextWrapper
import gambi.global.os26launcher.ui.home.compose.showRemoveAppDialog

/**
 * RA: dấu "−" trên app ở lưới/dock không ẩn ngay nữa mà mở [RemoveAppDialog]. "Xóa khỏi Màn hình chính" đi đúng đường ẩn cũ
 * ([HomeViewModel.onQuickHideTapped]); "Xóa ứng dụng" mở trình gỡ cài đặt. Ô không phải app (hoặc đang kéo) thì giữ hành vi cũ.
 */
internal fun HomeViewModel.confirmQuickRemove(context: Context, itemId: Long) {
    val app = appForQuickRemove(itemId) ?: return onQuickHideTapped(itemId)
    showRemoveAppDialog(
        context.activity() ?: return,
        app.label,
        onRemoveFromHome = { onQuickHideTapped(itemId) },
        onDeleteApp = { uninstallApp(app.key) }
    )
}

/**
 * RA: mục "Gỡ cài đặt" của menu nhấn giữ: đóng menu rồi mở [RemoveAppDialog]. "Xóa khỏi Màn hình chính" đi cùng đường với mục
 * "Ẩn ứng dụng" (app trong thư mục -> [HomeViewModel.hideAppInFolder], còn lại -> [HomeViewModel.onQuickHideTapped]).
 * Không tìm thấy app (trạng thái vừa đổi) thì giữ hành vi cũ: gỡ cài đặt thẳng.
 */
internal fun HomeViewModel.confirmMenuUninstall(context: Context) {
    val menu = uiState.value.iconMenu ?: return
    val inFolder = menu.container > 0L
    val app = (if (inFolder) appInOpenFolder(menu.key) else appForQuickRemove(menu.itemId))
        ?: return onIconMenuAction(IconMenuAction.Uninstall)
    closeIconMenu()
    showRemoveAppDialog(
        context.activity() ?: return,
        app.label,
        onRemoveFromHome = { if (inFolder) hideAppInFolder(menu.key) else onQuickHideTapped(menu.itemId) },
        onDeleteApp = { uninstallApp(menu.key) }
    )
}

/** RA: như [confirmQuickRemove], cho app trong panel thư mục ("Xóa khỏi Màn hình chính" = [HomeViewModel.hideAppInFolder]). */
internal fun HomeViewModel.confirmFolderRemove(context: Context, key: ComponentKey) {
    val app = appInOpenFolder(key) ?: return hideAppInFolder(key)
    showRemoveAppDialog(
        context.activity() ?: return,
        app.label,
        onRemoveFromHome = { hideAppInFolder(key) },
        onDeleteApp = { uninstallApp(key) }
    )
}

/** JS3: hộp thoại Compose cần Activity (host hộp thoại); context của View con là Activity hoặc ContextWrapper bọc nó. */
private fun Context.activity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}
