# LO — Màn 3.2 Thư viện ứng dụng (TT#11, TT#20) — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: code từng Task, báo cáo, chủ dự án xác nhận mới tick và sang Task sau; cả tính năng kiểm tay một lần ở cuối. Không có test tự động; kiểm tay trên Vsmart Live 4 (Oppo A3x chỉ khi cần kính). Lệnh viết cho CMD/PowerShell.

**Bắt đầu:** 2026-11-18 · **Plan duyệt:** 2026-11-18 (chủ dự án duyệt, làm liền 4 Task rồi kiểm một lần)
**Trạng thái:** Xong 2026-11-18
**Máy:** Live 4 · **Đổi schema Room:** không · **Khóa DataStore mới:** không (4 khóa `home_library_*` có sẵn từ CF; đổi MẶC ĐỊNH của `home_library_suggestions`, xem D7)
**Phiên:** claude.ai qua Linux VM → Claude không build được, chủ dự án build.

**Goal:** Đăng ký màn 3.2 theo Figma (`302:2334`, file `uauorWdd2Ayg9g4PUuhSCX`, đọc qua figma-bridge 2026-11-18) với 4 điều khiển: Bật thư viện, Kiểu hiển thị (Thư mục danh mục / Danh sách A–Z), Tự phân loại, Gợi ý & Mới thêm. Thư viện trên Home làm theo các cài đặt đó.
**Architecture:** Màn `LibrarySettingsScreen` trong khung Cài đặt (công thức G2d), đọc/ghi `HomeScreenConfigRepository`. Phía Home: `GetCategoryGroupsUseCase` ghép thêm cấu hình + lịch sử mở app → nội dung Thư viện (`LibraryContent`); `CategoryPageView` có thêm chế độ danh sách A–Z; `AppLibraryController` chặn mở khi thư viện tắt.
**Tech Stack:** không thêm dependency.
**Spec:** Figma 3.2 (`302:2334`); `Figma-Draft.md` hàng 11; `config-schema.md` mục 3.3.

## Vì sao chọn thiết kế này
- **Vấn đề:** 4 khóa đã có nhưng không ai đọc; Thư viện hiện luôn bật, luôn dạng thư mục 8 nhóm. Figma chỉ vẽ màn cài đặt, không vẽ thư viện dạng A–Z hay ô Gợi ý / Mới thêm.
- **Phương án đã xét cho A–Z:**

| Phương án | Cách làm | Ưu | Nhược | Chọn? |
|---|---|---|---|---|
| A | Trang Thư viện giữ thanh tìm kiếm, thay lưới ô nhóm bằng danh sách RecyclerView (icon + tên, tiêu đề chữ cái) | Một chỗ, đúng nghĩa "kiểu hiển thị" của Thư viện; thanh tìm kiếm, cử chỉ, kính giữ nguyên | Thêm 1 view danh sách | ✓ |
| B | Kiểu A–Z = mở thẳng lớp tìm kiếm chế độ LIBRARY `showAll` | Gần như không code | Vuốt qua trang cuối lại bật lớp phủ tìm kiếm, khác hẳn cử chỉ Thư viện; đóng/mở lẫn với Back của tìm kiếm | |

- **Gợi ý & Mới thêm** làm thành 2 nhóm đặc biệt đứng đầu lưới, dùng lại ô nhóm và màn nhóm toàn màn hình sẵn có (AP), không dựng view mới.
- **Đánh đổi:** A–Z chưa có thanh chữ cái để nhảy nhanh (ghi để sau); "Gợi ý" dựa vào lịch sử mở app của launcher nên rỗng khi người dùng tắt Gợi ý ở Cài đặt tìm kiếm.

## Quyết định đã chốt (Claude đề xuất, chủ dự án giao chốt 2026-11-18)
| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | **Không làm** mục "Ứng dụng mới cài" (Thêm vào màn hình chính / Chỉ trong thư viện): ẩn cả nhóm trên màn 3.2 | Chủ dự án 2026-11-18. Cần dấu "chỉ ở thư viện" lưu bền (đổi schema) — để sau |
| D2 | **Bật thư viện** tắt: vuốt qua trang cuối không mở Thư viện (`Host.canOpen()` trả false), pager dừng ở trang cuối như trang thường. Thư viện đang mở mà tắt (hiếm, Home đang khuất) → đóng không hoạt ảnh khi quay về. Không app nào mất chỗ: mọi app vẫn có trên lưới Home | Màn 3.2: khi tắt thì **ẩn** các nhóm bên dưới (kiểu hiển thị, hai công tắc), chỉ còn card công tắc |
| D3 | **Kiểu hiển thị:** hàng chọn 2 thẻ lớn như Figma (ô vẽ 4 thư mục 2×2 mini / ô vẽ 4 dòng kẻ có chấm màu), thẻ đang chọn có viền nhấn; ảnh minh họa vẽ bằng drawable XML theo Figma, không xuất ảnh | Mặc định Thư mục danh mục (khóa có sẵn, FOLDERS) |
| D4 | **Danh sách A–Z** (phương án A): dưới thanh tìm kiếm là danh sách cuộn mọi app chưa ẩn, sắp theo tên đã bỏ dấu (`AppSearchMatcher.normalize`), chia đoạn theo chữ cái đầu (A…Z; chữ khác, số, ký tự → "#", đứng cuối). Hàng = icon 40 dp + tên, cao ≥ 56 dp, chạm mở app (hoạt ảnh mở từ icon như ô nhóm), không nhấn giữ, không kéo. Nền: **không thẻ** — chữ trắng có bóng trên nền mờ của Thư viện (như danh sách A–Z của iOS); sửa lúc code 2026-11-18, bản plan đầu ghi "thẻ kính cho cả danh sách" (thẻ kính cuộn theo nội dung phải vẽ lại lát cắt nền mỗi khung, tốn mà không thêm gì). Không có thanh chữ cái nhảy nhanh | Ở kiểu A–Z, hai công tắc Tự phân loại và Gợi ý & Mới thêm **ẩn** trên màn 3.2 (chỉ áp cho kiểu thư mục) |
| D5 | **Tự phân loại** tắt: thay 8 nhóm bằng **một nhóm "Tất cả ứng dụng"** chứa mọi app theo tên A–Z; chạm mở màn nhóm toàn màn hình như nhóm thường. Gợi ý & Mới thêm (nếu bật) vẫn đứng trước | Bật (mặc định): 8 nhóm như hiện nay |
| D6 | **Gợi ý & Mới thêm** bật: thêm 2 nhóm đứng đầu, thứ tự Gợi ý → Mới thêm → các nhóm thường. Nhóm rỗng thì không hiện. **Gợi ý** = tối đa 8 app trong lịch sử mở app của launcher (`RecentAppsRepository`), xếp theo số lần mở giảm dần, hòa thì mở gần nhất trước; bỏ app ẩn/đã gỡ. **Mới thêm** = tối đa 8 app có `firstInstallTime` trong 30 ngày gần nhất, mới nhất trước, bỏ app hệ thống và app ẩn. Một app có thể vừa ở nhóm đặc biệt vừa ở nhóm thường | Như iOS. `firstInstallTime` lấy từ `LauncherActivityInfo.getFirstInstallTime()` (API 21, không cần quyền) vào trường mới `AppItem.firstInstallMillis` |
| D7 | Mặc định `home_library_suggestions` đổi **false → true** (Figma vẽ bật). Người đã từng ghi khóa giữ giá trị của họ | Sửa `HomeScreenConfig` và `config-schema.md` cùng commit |
| D8 | Chuỗi mới vào đủ 9 file; tên nhóm "Gợi ý", "Mới thêm", "Tất cả ứng dụng" | |
| D9 | **Tạm ẩn** "Tự phân loại" và "Hiện Gợi ý & Mới thêm" trên màn 3.2; Home **bỏ qua giá trị đã lưu** của hai khóa đó (luôn 8 nhóm, không Gợi ý/Mới thêm), để không có nhóm hiện mà không còn chỗ tắt. Code D5/D6 giữ nguyên, bật lại bằng cờ `LibrarySettingsScreen.SHOW_FOLDER_SWITCHES` + `GetCategoryGroupsUseCase.FOLDER_SWITCHES_ENABLED`. **D7 bỏ:** mặc định `home_library_suggestions` trở lại false | Chủ dự án 2026-11-18: hai mục này chưa ổn |
| D10 | Danh sách A–Z có **cột chữ cái bên phải** như danh bạ: chỉ chữ có đoạn, chạm/kéo dọc nhảy tới đoạn (hàng tiêu đề lên đầu), rung nhẹ mỗi lần đổi chữ, chữ đang chọn màu nhấn; cột rộng 28 dp, mỗi chữ tối đa 18 dp, cụm căn giữa dọc | Chủ dự án 2026-11-18 (thay "không có thanh chữ cái" của D4) |

## Ngoài phạm vi / ghi để sau
- "Ứng dụng mới cài" (D1). Thanh chữ cái nhảy nhanh ở A–Z. Nhấn giữ app trong Thư viện. Thuật toán gợi ý theo giờ/ngữ cảnh.

## Vùng file
| File | Tạo / Sửa | Task |
|---|---|---|
| `ui/settings/LibrarySettingsScreen.kt` | Tạo | 1, 2 |
| `ui/settings/SettingsScreen.kt` (đăng ký 3.2), `SettingsRow.kt`, `SettingsAdapter.kt`, `SettingsCardDecoration.kt`, `SettingsActivity.kt`, `HomeTogglesViewModel.kt` (hàng chọn kiểu, ghi khóa) | Sửa | 1, 2 |
| `res/layout/item_settings_library_style.xml`, `res/drawable/lib_style_folders.xml`, `lib_style_list.xml` | Tạo | 2 |
| `ui/home/controller/AppLibraryController.kt`, `HomeTogglesController.kt`, `ui/home/HomeActivity.kt` | Sửa | 1, 3 |
| `domain/model/AppCategory.kt` (`CategoryGroup` thêm `SUGGESTED`, `RECENTLY_ADDED`, `ALL`; `LibraryContent`) | Sửa | 3, 4 |
| `domain/category/AppCategoryGrouper.kt`, `domain/category/LibraryArranger.kt` (mới, hàm thuần A–Z / Gợi ý / Mới thêm), `domain/usecase/GetCategoryGroupsUseCase.kt`, `ui/home/HomeViewModel.kt` | Sửa / Tạo | 3, 4 |
| `ui/home/view/CategoryPageView.kt` (+ `LibraryListAdapter.kt`, `item_library_list_letter.xml`, `item_library_list_app.xml`, `view_category_page.xml`, dimen `library_list_icon_size`) | Sửa / Tạo | 3, 4 |
| `domain/model/AppItem.kt`, `data/apps/LauncherAppsDataSource.kt` (`firstInstallMillis`) | Sửa | 4 |
| `domain/config/HomeScreenConfig.kt`, `docs/config-schema.md` (D7) | Sửa | 4 |
| `res/values*/strings.xml` (9 file) | Sửa | 1–4 |

## Global Constraints
- `ui → domain ← data`: `LibraryContent` ở `domain/model`; use case đọc `HomeScreenConfigRepository` và `RecentAppsRepository` (đều là interface domain).
- Thêm giá trị enum `CategoryGroup` ở **cuối** khai báo? Không: thứ tự khai báo đang là thứ tự hiển thị (D12 của G2a). Nhóm đặc biệt có thứ tự riêng trong use case, `AppCategoryGrouper.group` chỉ duyệt 8 nhóm thường (liệt kê tường minh, không `values()`).
- `CategoryPageView.render` hủy job nạp icon cũ: chế độ danh sách dùng RecyclerView với job theo holder (mẫu `AppLibraryAdapter`/RN).

## Review Focus
- Thư viện tắt: không còn cách nào khác mở nó (tìm kiếm "showAll" chỉ mở từ thanh tìm kiếm trong Thư viện).
- Đổi kiểu hiển thị / công tắc khi Home khuất: quay về thấy đúng ngay khung đầu (thu cấu hình không gắn STARTED, như HT).
- Danh sách A–Z: icon không nháy/lẫn khi cuộn nhanh (job theo holder).
- `firstInstallMillis` vào `AppItem` làm `equals` đổi: chỉ đổi khi cài lại (đã đổi `versionStamp` trước đó), không gây dựng lại thừa.

---

### Task 1: Màn 3.2 + công tắc Bật thư viện
**Bắt buộc kiểm tay:** có — cử chỉ vuốt qua trang cuối (bài học M7)
- [x] Step 1: `LibrarySettingsScreen` (card công tắc "Bật thư viện ứng dụng" + mô tả), đăng ký `SettingsScreenId.LIBRARY`; mục 3.2 tự hiện trên trang chủ.
- [x] Step 2: Home thu `HomeScreenConfig.libraryEnabled` → `AppLibraryController` (`canOpen` = false khi tắt; đang mở thì đóng ngay).
- [x] Step 3: Kịch bản (Live 4) — (a) tắt → vuốt qua trang cuối không mở Thư viện, pager không kẹt; (b) bật lại → mở được; (c) mở Thư viện, vào Cài đặt tắt, quay về: Home ở trang cuối, Thư viện đóng.

### Task 2: Kiểu hiển thị trên màn 3.2
**Bắt buộc kiểm tay:** không
- [x] Step 1: `SettingsRow.StylePicker` + holder + layout 2 thẻ + 2 drawable minh họa; ghi `libraryStyle`.
- [x] Step 2: Card "Tự phân loại" + "Hiện Gợi ý & Mới thêm" (ẩn khi kiểu A–Z, D4); cả phần dưới ẩn khi thư viện tắt (D2).
- [x] Step 3: Kịch bản — thẻ chọn đổi viền ngay; đổi kiểu thì hai công tắc ẩn/hiện; giá trị giữ sau khi thoát vào lại.

### Task 3: Thư viện dạng A–Z + Tự phân loại tắt
**Bắt buộc kiểm tay:** có — cuộn danh sách trong lớp phủ có cử chỉ vuốt đóng ngang
- [x] Step 1: `LibraryContent` (Folders(groups) / Alphabetical(apps)); use case ghép `libraryStyle`, `libraryAutoCategorize`; nhóm `ALL` khi tắt phân loại (D5).
- [x] Step 2: `CategoryPageView` chế độ danh sách (D4), `LibraryListAdapter` với tiêu đề chữ cái; chạm mở app.
- [x] Step 3: Kịch bản — (a) A–Z: đúng thứ tự, "Đ" về "D", "#" cuối; cuộn dọc không mở/đóng Thư viện, vuốt ngang vẫn đóng; (b) tắt phân loại: một ô "Tất cả ứng dụng", chạm mở màn nhóm đủ app; (c) ẩn một app → biến khỏi cả hai kiểu.

### Task 4: Gợi ý & Mới thêm
**Bắt buộc kiểm tay:** không
- [x] Step 1: `AppItem.firstInstallMillis` từ `LauncherActivityInfo.getFirstInstallTime()`.
- [x] Step 2: Use case ghép `RecentAppsRepository.entries` + `librarySuggestions` → nhóm `SUGGESTED`, `RECENTLY_ADDED` đứng đầu (D6); nhãn nhóm; đổi mặc định (D7).
- [x] Step 3: ~~Kịch bản~~ không kiểm: tính năng tạm ẩn (D9), code giữ chờ bật lại. (a) mở vài app từ Home → "Gợi ý" có chúng, nhiều lần mở đứng trước; (b) cài một app mới → "Mới thêm" đứng đầu nhóm đó; (c) tắt công tắc → hai nhóm biến mất; (d) tắt Gợi ý ở Cài đặt tìm kiếm (xóa lịch sử) → nhóm Gợi ý biến mất.

---

## Bài kiểm cả tính năng
Live 4. Cài đặt → Thư viện ứng dụng: tắt/bật thư viện và thử vuốt qua trang cuối; đổi Thư mục ↔ A–Z và mở Thư viện; ở A–Z cuộn, chạm app, kéo cột chữ cái; ẩn một app rồi xem cả hai kiểu. Hồi quy: lưới ô nhóm, màn nhóm toàn màn hình, thanh tìm kiếm Thư viện.
**Kết quả kiểm chứng (máy, ngày):** Vsmart Live 4, 2026-11-18, chủ dự án kiểm tay, đạt (Task 1–3 + cột chữ cái D10). Task 4 không kiểm (tạm ẩn, D9).

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-18 | Plan | Đọc Figma 3.2 (figma-bridge), viết plan, chốt D1–D8 | Chờ duyệt |
| 2026-11-18 | 1–4 | Code liền 4 Task: `LibrarySettingsScreen` + hàng `LibraryStyle`; Home chặn mở Library khi tắt; `LibraryContent` + `LibraryArranger`; danh sách A–Z trong `CategoryPageView`; nhóm `SUGGESTED`/`RECENTLY_ADDED`/`ALL`; `AppItem.firstInstallMillis`; mặc định `librarySuggestions` = true; 12 chuỗi × 9 ngôn ngữ | Đổi D4: danh sách không có thẻ kính. Chưa build |
| 2026-11-18 | 2–4 | Góp ý chủ dự án: ẩn 2 công tắc (D9, Home bỏ qua giá trị lưu, mặc định suggestions về false); thêm `LetterIndexView` (D10) + `LibraryListAdapter.sectionStarts`, dimen `library_letter_index_width` | Task 4 thành code chờ (không hiện). Chưa build |
| 2026-11-18 | Chốt | Chủ dự án kiểm Live 4 đạt; tick, đóng plan | |

## Tài liệu chung đã cập nhật
- `project_structure.md`: dòng mốc LO ở mục 9 + "Tệp thêm ở LO".
- `Figma-Draft.md`: link file Figma mới nhất; hàng 11 (3.2).
- `config-schema.md`: 4 khóa `home_library_*` (chỗ đọc, D9).
- `lessons/settings-and-data.md`: công tắc ẩn phải vô hiệu cả giá trị đã lưu.
