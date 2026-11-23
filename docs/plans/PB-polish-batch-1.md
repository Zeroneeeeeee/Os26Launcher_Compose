# PB — Polish đợt 1 (5 việc nhỏ) — Implementation Plan

> Ô "Kịch bản kiểm" để trống tới khi chủ dự án kiểm tay; các Step code tick ngay khi xong.
>
> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`. **Chủ dự án yêu cầu 2026-11-18: lên plan và làm ngay, xong Task nào tick Task đó** (thay cho "chờ xác nhận từng Task"); kiểm tay cả đợt một lần ở cuối. Không có test tự động; kiểm tay trên Vsmart Live 4, Oppo A3x cho Task 4 (Android 12+ khác cử chỉ). Lệnh viết cho CMD/PowerShell.

**Bắt đầu:** 2026-11-18 · **Plan duyệt:** 2026-11-18 (chủ dự án giao làm ngay)
**Trạng thái:** Xong 2026-11-18
**Máy:** Live 4 + Oppo A3x (Task 4) · **Đổi schema Room:** không · **Khóa DataStore mới:** không (`general_nav_bar_hidden` đã có từ CF)
**Phiên:** claude.ai qua Linux VM → **Claude không build được**, chủ dự án build `gradlew.bat assembleDebug`.

**Goal:** 5 việc polish: hỏi đặt launcher mặc định khi quay về Home; công tắc tìm lối tắt/danh bạ hỏi quyền ngay; ẩn hàng Cài đặt chưa có chức năng và nhóm Liên hệ; công tắc ẩn thanh điều hướng; widget thời tiết thật theo bố cục card xem trước 1.4, bỏ hướng dẫn `local.properties`.

## Quyết định đã chốt (Claude, 2026-11-18, giả định nêu trong báo cáo, chủ dự án sửa nếu khác)

| # | Quyết định | Cơ sở |
|---|---|---|
| D1 | Hộp thoại "Đặt làm màn hình chính" hiện **mỗi lần Home vào RESUMED** khi chưa là mặc định, trừ: Onboarding chưa xong; lần resume đầu sau khi Onboarding đóng (0.4–0.5 vừa hỏi); lần resume ngay sau khi chính hộp thoại này mở bộ chọn launcher (tránh hỏi lại liền khi người dùng vừa Hủy); hộp đang mở. Nút "Đặt làm mặc định" dùng `DefaultLauncherAction`, nút "Để sau" đóng | Khi chưa mặc định, Home chỉ resume khi người dùng chủ động mở app (icon, Gần đây, quay từ Cài đặt), nên hỏi mỗi lần không dồn dập |
| D2 | Công tắc "Lối tắt ứng dụng" và "Tìm trong danh bạ" hiện **bật = (đã bật trong cài đặt) VÀ (đủ điều kiện)**: lối tắt cần launcher mặc định, danh bạ cần `READ_CONTACTS`. Bật công tắc → ghi bật rồi hỏi ngay (bộ chọn launcher / hộp quyền; đã từ chối vĩnh viễn thì Toast + mở Thông tin ứng dụng). Người dùng từ chối thì công tắc tự về tắt (vì điều kiện chưa đủ), không cần hàng gợi ý. Bỏ 2 hàng gợi ý dưới công tắc | Bỏ thao tác 2 lần; trạng thái hiện luôn đúng sự thật |
| D3 | Trang chủ Cài đặt: mục chưa đăng ký màn **ẩn hẳn** (không mờ "Sắp có"); nhóm rỗng sau khi lọc thì ẩn cả tiêu đề; nhóm **Liên hệ** ẩn cả nhóm (cờ hằng `CONTACT_GROUP_ENABLED = false`, bật lại khi có dữ liệu thật) | Yêu cầu chủ dự án |
| D4 | Ẩn thanh điều hướng: công tắc ở màn Chung (1.2), khóa có sẵn `general_nav_bar_hidden`. Chỉ áp cho cửa sổ Home. `WindowInsetsControllerCompat.hide(navigationBars())` với `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` (cùng behavior với ẩn thanh trạng thái ở edit mode, TL1). Áp lại ở `onResume` và `onWindowFocusChanged(true)`. Bố cục giữ inset "bỏ qua visibility" như TL1 nên dock không nhảy khi thanh hiện tạm | Đọc tài liệu: vuốt từ mép hiện thanh tạm rồi tự ẩn. **Suy luận chưa kiểm chứng:** Android 10–11 cử chỉ có thể cần vuốt 2 lần để về Gần đây (lần đầu chỉ hiện thanh); Android 12+ cải thiện cử chỉ khi ẩn thanh — kiểm trên Live 4 (11) và Oppo (14) |
| D5 | Widget thời tiết thật vẽ **theo bố cục card xem trước 1.4** (`item_settings_weather_preview.xml`): khối trái thành phố + nhiệt độ lớn, khối phải icon + tình trạng + "C x° · T y°", dải dưới 5 cột "Bây giờ" + 4 mốc giờ (nhãn trên, nhiệt độ dưới, canh trái cột). Nhiệt độ viết "24°" như card. 2x2: không có dải giờ. Font Be Vietnam Pro. **Nền giữ thẻ kính của widget** (theo cài đặt Kính), chữ giữ bóng mềm | Yêu cầu chủ dự án; sửa 2026-11-18: chủ dự án chỉ muốn đổi bố cục, không đổi nền (bản đầu có thẻ xanh đêm riêng, đã bỏ) |
| D6 | Không có khóa OWM: widget và bảng thời tiết hiện "Thời tiết tạm thời không khả dụng", **bỏ mọi chữ nhắc `local.properties`/`OWM_API_KEY`** (bản release trên máy khác có thể không có khóa). Xóa chuỗi `weather_no_key_hint` ở 9 file `strings.xml` (không còn ai dùng) | Yêu cầu chủ dự án; C15 (proxy khóa) vẫn để trước phát hành |

## Ngoài phạm vi
- Màn còn thiếu ở trang chủ (Thư viện 3.2, Huy hiệu 3.4, Chuyển cảnh 4.1–4.2, Đổi icon 5.2): chỉ ẩn, không làm.
- Thẻ thời tiết 1.4 dùng số liệu thật (vẫn là mẫu).
- Ẩn thanh điều hướng ở Cài đặt/App Library/Onboarding.

## Vùng file
| File | Sửa | Task |
|---|---|---|
| `ui/settings/SettingsHubScreen.kt` | lọc mục chưa có, ẩn nhóm Liên hệ | 1 |
| `ui/settings/SearchSettingsScreen.kt`, `SearchSettingsBlock.kt`, `SearchSettingsViewModel.kt`, `SettingsRow.kt` | công tắc hỏi quyền, bỏ hàng gợi ý | 2 |
| `ui/home/controller/DefaultLauncherPromptController.kt` (mới), `ui/home/HomeActivity.kt` | hộp thoại khi quay về Home | 3 |
| `ui/settings/GeneralSettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsUiState.kt`, `SettingsRow.kt`, `ui/home/controller/SystemBarsController.kt`, `ui/home/HomeActivity.kt` | công tắc + áp ẩn thanh | 4 |
| `ui/widget/WeatherWidgetRenderers.kt` | bố cục mới (nền kính giữ nguyên) | 5 |
| `res/values*/strings.xml` (9 file) | chuỗi mới / đổi / bỏ | 2–5 |
| `docs/config-schema.md` | `navBarHidden` thành "đã dùng" | 4 |

## Review Focus
- Task 2: hàng `Switch` không được bind lại thành tắt rồi bật nháy khi đang chờ hộp quyền (`rerender` sau kết quả).
- Task 3: không hỏi chồng với hộp áp wallpaper (hộp đó chỉ hiện khi ĐÃ mặc định nên không trùng) và không hỏi trong lúc Onboarding che Home.
- Task 4: edit mode ẩn/hiện thanh trạng thái không làm hiện lại thanh điều hướng (gọi `show(statusBars())` chỉ đúng loại đó).
- Task 5: `draw()` không cấp phát (bài học GL Task 6).

---

### Task 1: Ẩn hàng chưa có chức năng và nhóm Liên hệ
**Bắt buộc kiểm tay:** không
- [x] **Step 1:** `SettingsHubScreen.rows` bỏ mục `!isAvailable`, bỏ nhóm rỗng; `SettingsHubLayout` thêm cờ `CONTACT_GROUP_ENABLED = false` lọc nhóm `contact`.
- [x] **Step 2: Kịch bản kiểm (Live 4)** — (a) trang chủ Cài đặt không còn hàng mờ "Sắp có"; (b) không còn tiêu đề "Liên hệ" và 3 hàng của nó; (c) nhóm "Chuyển cảnh" (cả hai mục chưa có) biến mất cả tiêu đề.

### Task 2: Công tắc tìm lối tắt / danh bạ hỏi quyền ngay
**Bắt buộc kiểm tay:** có — hộp quyền và bộ chọn launcher là màn hệ thống (`Workflow.md` mục 3, giả định máy thật)
- [x] **Step 1:** `SearchSettingsBlock.rows`: công tắc hiện `prefs.shortcuts && isDefault`, `prefs.contacts && granted`; bỏ 2 hàng gợi ý (+ id và nhánh xử lý).
- [x] **Step 2:** `SearchSettingsScreen`: đăng ký `RequestPermission` lúc dựng; bật lối tắt khi chưa mặc định → `ctx.defaultLauncher.request()`; bật danh bạ khi thiếu quyền → hỏi quyền (đã từ chối vĩnh viễn → Toast + Thông tin ứng dụng); kết quả → đọc lại quyền + `rerender(true)`. Bật danh bạ ghi cả `contactsAsked = true` (lớp tìm kiếm dùng cờ này).
- [x] **Step 3: Kịch bản kiểm (Live 4)** — (a) chưa mặc định: bật "Lối tắt ứng dụng" → bộ chọn launcher hiện; chọn OS26 → công tắc bật; Hủy → công tắc về tắt; (b) thu hồi quyền Danh bạ, bật "Tìm trong danh bạ" → hộp quyền; Cho phép → bật, tìm tên trong ô tìm kiếm ra danh bạ; Từ chối → tắt; (c) từ chối lần 2 rồi bật lại → Toast + mở Thông tin ứng dụng; cấp quyền ở đó, Back → công tắc bật; (d) không còn hàng gợi ý dưới hai công tắc.

### Task 3: Hỏi đặt làm màn hình chính khi quay về Home
**Bắt buộc kiểm tay:** có — vòng đời Home với bộ chọn launcher của hệ thống
- [x] **Step 1:** `DefaultLauncherPromptController` (dựng ở `onCreate`): giữ `DefaultLauncherAction`, hộp `DsDialog` (qua `SettingsDialogs.confirm`), các cờ bỏ qua theo D1; `onResume` gọi `maybeShow()`, `onDestroy` dismiss.
- [x] **Step 2:** `HomeActivity`: tiêm `DefaultLauncherRepository`, nối controller; đánh dấu "bỏ qua lần sau" khi mở Onboarding.
- [x] **Step 3:** Chuỗi: dùng lại chuỗi Onboarding đã có đủ 9 ngôn ngữ (`onboarding_default_title`, `onboarding_default_subtitle`, `onboarding_set_default`, `onboarding_later`), không thêm chuỗi mới.
- [x] **Step 4: Kịch bản kiểm (Live 4)** — đặt launcher gốc của máy làm mặc định: (a) mở OS26 từ icon → hộp thoại hiện; "Để sau" → đóng; (b) mở app khác rồi về OS26 qua Gần đây → hộp hiện lại; (c) "Đặt làm mặc định" → Hủy ở bộ chọn → về Home KHÔNG hỏi lại ngay; rời rồi vào lại → hỏi; (d) chọn OS26 → không còn hỏi; (e) cài mới (chưa Onboarding): Onboarding xong mà bỏ qua bước mặc định → Home không hỏi ngay ở lần đầu.

### Task 4: Ẩn thanh điều hướng
**Bắt buộc kiểm tay:** có — hành vi cử chỉ khác theo phiên bản Android (D4)
- [x] **Step 1:** `SettingsUiState.navBarHidden`, `SettingsViewModel.onNavBarHiddenChanged`; hàng công tắc `ID_NAV_BAR_HIDDEN` ở `GeneralSettingsScreen`; chuỗi 9 ngôn ngữ.
- [x] **Step 2:** `SystemBarsController.setNavBarHidden(hidden)` + `reapply()`; `HomeActivity` thu `GeneralConfigRepository.config` (không gắn STARTED, như `HomeTogglesController`), gọi `reapply()` ở `onResume` và `onWindowFocusChanged(true)`.
- [x] **Step 3:** `config-schema.md`: `navBarHidden` có màn và chỗ dùng.
- [x] **Step 4: Kịch bản kiểm (Live 4, Oppo)** — (a) Chung → bật "Ẩn thanh điều hướng" → về Home: không còn thanh/nút điều hướng, dock không dịch; (b) vuốt từ mép dưới: thanh hiện tạm rồi tự ẩn; ghi lại cần mấy lần vuốt để về Gần đây (cử chỉ và 3 nút); (c) vào edit mode rồi thoát: thanh điều hướng vẫn ẩn; (d) mở app khác rồi về Home: vẫn ẩn; (e) tắt công tắc → thanh hiện lại.

### Task 5: Widget thời tiết theo bố cục card xem trước, bỏ hướng dẫn khóa
**Bắt buộc kiểm tay:** không (nhìn)
- [x] **Step 1:** ~~`WidgetRenderer.drawCard`~~ bỏ theo góp ý 2026-11-18: nền vẫn là thẻ kính, `WidgetRenderer`, `IconCellView`, `WidgetPreviewView` về như trước PB.
- [x] **Step 2:** `WeatherRenderer` vẽ lại theo D5 (khối trái/phải, dải 5 cột), font Be Vietnam Pro, màu `widget_text`/`widget_text_dim`, chữ có bóng; giữ mọi trạng thái (không khóa, chưa vị trí, đang tải, lỗi, dữ liệu cũ).
- [x] **Step 3:** D6: chuỗi `weather_no_key` / `weather_sheet_no_key` đổi nghĩa ở 9 ngôn ngữ; bỏ `weather_no_key_hint`.
- [x] **Step 4: Kịch bản kiểm (Live 4)** — (a) widget 4x2 trên Home: bố cục giống card ở Cài đặt → Thời tiết; (b) 2x2: thành phố, nhiệt độ, icon, tình trạng, C/T; (c) bảng chọn widget: bản xem trước cùng kiểu; (d) bản build không có `OWM_API_KEY`: widget và bảng thời tiết ghi "Thời tiết tạm thời không khả dụng", không nhắc `local.properties`; (e) split-screen thấp: chữ không chồng.

---

## Bài kiểm cả tính năng
Live 4 (Android 11), Oppo A3x (Android 14) cho Task 4. Chuẩn bị: launcher gốc làm mặc định; một widget thời tiết 4x2 và một 2x2 trên Home; quyền Danh bạ đang thu hồi.
1. Mở OS26 từ icon → hộp "Đặt làm màn hình chính" (T3 a). "Để sau".
2. Cài đặt (edit mode → Cài đặt): trang chủ không còn "Sắp có", không còn nhóm Liên hệ (T1).
3. Cài đặt → Tìm kiếm: bật Lối tắt (T2 a), bật Danh bạ (T2 b, c).
4. Về Home: sau khi đã chọn OS26 mặc định ở bước 3, không còn hộp thoại (T3 d).
5. Cài đặt → Chung → Ẩn thanh điều hướng (T4 a–e), lặp trên Oppo.
6. Nhìn widget thời tiết (T5 a–c, e); build không khóa (T5 d).
Hồi quy: hộp áp wallpaper sau khi thành mặc định lần đầu vẫn hiện; edit mode ẩn thanh trạng thái như cũ; lớp tìm kiếm vẫn mời cấp quyền danh bạ khi công tắc bật mà thiếu quyền.
**Kết quả kiểm chứng (máy, ngày):** Vsmart Live 4, 2026-11-18, chủ dự án kiểm tay, đạt cả 5 Task. Oppo A3x (Task 4, Android 12+) chưa kiểm.

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-18 | 1 | `SettingsHubLayout.visibleGroups` + cờ `CONTACT_GROUP_ENABLED`; hub không còn hàng mờ | Chuỗi `settings_hub_coming_soon` giờ không ai dùng (giữ, có thể cần lại) |
| 2026-11-18 | 2 | Công tắc Lối tắt/Danh bạ hiện theo điều kiện thật, bật là hỏi; bỏ `ID_SEARCH_SHORTCUTS_HINT`, `ID_SEARCH_CONTACTS_HINT` | Dùng lại chuỗi cũ cho Toast (`settings_search_shortcuts_need_default`, `search_contacts_prompt_settings_subtitle`); `settings_search_contacts_need_permission` giờ không ai dùng |
| 2026-11-18 | 3 | `DefaultLauncherPromptController` + nối `HomeActivity` (onCreate, onResume, onPause mới, onDestroy) | Không thêm chuỗi |
| 2026-11-18 | 4 | `SystemBarsController.setNavBarHidden/reapplyNavBar`, Home thu `GeneralConfig`, `onWindowFocusChanged`; công tắc ở màn Chung; 2 chuỗi × 9 ngôn ngữ | Khóa có sẵn từ CF |
| 2026-11-18 | 5 | `WidgetRenderer.drawCard`; `WeatherRenderer` viết lại bố cục theo card 1.4; đổi `weather_no_key`, `weather_sheet_no_key`, bỏ `weather_no_key_hint` (9 file) | `weather_hi_lo_short/long` giờ không ai dùng. Kiểm khóa chuỗi 9 file khớp bằng script tạm (không có `tools/check_translations.py` trong repo). **Chưa build** (phiên VM) |
| 2026-11-18 | 5 | Góp ý chủ dự án: bỏ thẻ xanh đêm, giữ nền kính; màu chữ về `widget_text`/`widget_text_dim` + bóng | `WidgetRenderer`, `IconCellView`, `WidgetPreviewView` không còn thay đổi |
| 2026-11-18 | Dọn | Xóa 4 chuỗi không còn ai dùng ở 9 file: `settings_hub_coming_soon`, `settings_search_contacts_need_permission`, `weather_hi_lo_short`, `weather_hi_lo_long` (grep mọi source set: 0 chỗ dùng) | Không có file nào cần xóa |
| 2026-11-18 | Chốt | Chủ dự án kiểm tay trên Live 4, đạt; tick đủ, đóng plan | Oppo chưa kiểm Task 4 |

## Bằng chứng Review Focus
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Task 2: công tắc không nháy khi chờ hộp quyền | Hàng trước/sau đều `false` nên DiffUtil không bind lại khi đang chờ; sau kết quả `rerender(true)` (đọc code) + kiểm Live 4 | Đạt |
| 2 | Task 3: không chồng hộp áp wallpaper, không hỏi khi Onboarding che Home | Hộp wallpaper chỉ hiện khi đã mặc định; controller chờ `isOnboardingDone()` và `skipNextResume()` khi mở Onboarding (đọc code) + kiểm Live 4 | Đạt |
| 3 | Task 4: edit mode không làm hiện lại thanh điều hướng | Thoát edit mode chỉ `show(statusBars())` (đọc code) + kiểm Live 4 | Đạt |
| 4 | Task 5: `draw()` không cấp phát | Chuỗi, cỡ chữ, tọa độ dựng ở `prepare`; `draw` chỉ `drawText`/icon (đọc code) | Đạt |

## Tài liệu chung đã cập nhật
- `project_structure.md`: dòng mốc PB ở mục 9 + đoạn "Tệp thêm ở PB".
- `config-schema.md`: `navBarHidden` có chỗ đọc.
- `lessons/settings-and-data.md`: 3 mục PB (công tắc cần quyền, ẩn mục chưa có màn, không để chữ hướng dẫn dev trong chuỗi).
- `lessons/permissions-and-device-testing.md`: ẩn thanh điều hướng (Live 4 đạt, Oppo chưa đo).
- `AGENTS.md`: thiếu `tools/check_translations.py`.
- `Launcher-Features.md`, `room-schema.md`: không đổi.
