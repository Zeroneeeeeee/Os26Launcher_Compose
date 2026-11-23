# HC — Hero Card 2 trạng thái, Cài đặt làm điểm vào app, polish tiêu đề — Implementation Plan

> Làm theo `docs/AGENTS.md`. Chủ dự án 2026-11-18: "lên plan và làm luôn"; plan cũ RS đã xong, plan này thay vì thêm Task 9 vào RS (đổi điểm vào app không phải polish). Không có test tự động; kiểm tay trên Vsmart Live 4 (thêm Oppo A3x cho Task 2 nếu có). Lệnh viết cho PowerShell.

**Bắt đầu:** 2026-11-18 · **Plan duyệt:** 2026-11-18
**Trạng thái:** Task 1–4 code xong, chờ build + kiểm
**Máy:** Live 4 (+ Oppo A3x Task 2) · **Đổi schema Room:** không · **Khóa config mới:** không · **Dependency mới:** không
**Chuỗi:** mọi chuỗi thêm/sửa làm đủ 9 file `values*/strings.xml` (AGENTS.md mục LG), kiểm `lintDebug` (`MissingTranslation`).

**Goal:** (1) Card đầu trang chủ Cài đặt theo component Hero Card mới (2 trạng thái, Figma `464:2199`, ghi chú `464:2648`); (2) mở app từ icon vào thẳng Cài đặt sau splash, nút "Trải nghiệm"/"Khám phá" mở lưới Home; (3) tiêu đề các màn đúng chữ và đậm như Figma; (4) bóng ô icon và icon Huy hiệu theo bản Figma mới.

**Spec:** Chủ dự án 2026-11-18 (tin nhắn 16:05). Figma "Laucher App (Copy)" trang `302:1490`: Hero Card `464:2199` (Chưa mặc định / Đã mặc định), Home `302:1492`, Home · Đã mặc định `464:2224`, ghi chú `464:2648`; tiêu đề màn: chữ 20 Bold trong từng frame 1.1–6.3.

## Đọc Figma bản mới (2026-11-18, so với bản chụp Home cũ)

| Đổi | Chi tiết |
|---|---|
| Hero Card thành component 2 trạng thái | **Chưa mặc định:** như card RS (ảnh, "Màn hình chính đang chọn", tên theme, badge "Chưa đặt làm mặc định", nút). Nút ở màn Home ghi **"Trải nghiệm"** (component gốc ghi "Đặt làm mặc định"). **Đã mặc định:** "Chào mừng bạn" + chip "v1.0.0", mô tả 3 dòng, "Khám phá ›", vòng tròn kính có số "27" bên phải. Cao cố định 140 cả hai |
| Ghi chú chuyển trạng thái | Chưa → Đã: cross-fade 300 ms ease-out + toast "Đã đặt làm mặc định". Đổi sang launcher khác → lần mở sau về "Chưa". Số trong vòng tròn theo theme (Liquid Glass 27 → 27, Classic 26 → 26); theme không có số → ảnh xem trước thu nhỏ trong vòng tròn. Chỉ versionName |
| Bóng ô icon (20 ô) | Bóng ngoài màu ô 0.10 → bóng đen 0.45 (y 4, mờ 10) + bóng đen 0.30 (y 1, mờ 3); 2 bóng trong giữ |
| Icon Huy hiệu (`302:1649`) | Vẽ lại trong khung 16 × 16 |

Tiêu đề màn (Figma, 20 Bold) khác app (`values-vi`): trang chủ "Launcher OS" (app "Cài đặt"); 1.2 "Chung" ("Cài đặt chung"); 5.1 "Ứng dụng ẩn" ("Ứng dụng đã ẩn"); 6.2 "Ứng dụng khác" ("Tải ứng dụng khác", hàng trên trang chủ Figma vẫn ghi "Tải ứng dụng khác"). Các màn khác khớp.

## Vì sao chữ tiêu đề không đậm (suy luận chưa kiểm chứng, Task 3 đo)

`Theme.Os26Launcher.Ds` đặt `android:fontFamily` / `fontFamily` = `@font/be_vietnam_pro` ở cấp theme. TextView đọc thuộc tính trực tiếp (gồm giá trị theme) SAU `textAppearance`, nên họ font của theme (độ đậm 400, `textStyle normal`) đè `be_vietnam_pro_bold` của `TextAppearance.Os26.Ds.Title`/`HeadingLarge`/`Card`/`SectionHeader`/`BodyStrong`. Hệ quả: mọi chữ v3 ra Regular. Sửa: bỏ `fontFamily` khỏi theme, đặt font mặc định qua `android:textViewStyle` → style có `android:textAppearance = TextAppearance.Os26.Ds.Body` (TextView có `textAppearance` riêng thì dùng của nó).

## Quyết định

| # | Quyết định | Nguồn |
|---|---|---|
| 1 | Plan mới HC, RS đóng (Task 1–8 xong, đã commit) | Chủ dự án 2026-11-18 |
| 2 | Mở app từ icon (MAIN/LAUNCHER) vào trang chủ Cài đặt sau splash; nút Home của máy (HOME, khi là mặc định) vẫn mở lưới Home | Chủ dự án; Claude giữ HOME cho lưới vì launcher bắt buộc phải có màn Home |
| 3 | "Trải nghiệm" (trạng thái 1) và "Khám phá ›" (trạng thái 2) mở lưới Home (thay ghi chú Figma: nút mở hộp thoại hệ thống, "Khám phá" mở 1.1) | Chủ dự án |
| 4 | Đặt mặc định khi chưa là mặc định: qua hộp hỏi khi Home resume (PB D1, đã có) và màn 1.1 | Claude, theo quyết định 3 |

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Tiêu đề trang chủ Cài đặt: "Launcher OS" như Figma, hay tên app "OS26 Launcher"? | Dùng `app_name` ("OS26 Launcher"): đổi tên app sau không phải sửa 2 chỗ | Theo khuyến nghị (2026-11-18) |
| Q2 | Mở lưới Home từ Cài đặt khi app CHƯA là mặc định: Back ở lưới Home quay về Cài đặt? | Có: chưa mặc định thì Back ở Home đóng Home (về Cài đặt); đã mặc định giữ như cũ (Back không làm gì) | Theo khuyến nghị (2026-11-18) |
| Q3 | Onboarding lần đầu: mở từ Cài đặt (điểm vào mới), xong 0.5 thì về Cài đặt hay lưới Home? | Về lưới Home (nút 0.5 "Bắt đầu" đang dẫn về Home, giữ nguyên ý nghĩa) | Theo khuyến nghị (2026-11-18) |

## Vùng file

| File | Sửa | Task |
|---|---|---|
| `SettingsRow.LauncherCard` → thêm `versionName`; `item_settings_launcher_card.xml` (2 khối chồng, cao 140); `SettingsAdapter.LauncherCardHolder`; `SettingsHubScreen` (toast khi chuyển); `ThemeBadge` (số / ảnh theo theme, mới) | Sửa / tạo | 1 |
| `AndroidManifest.xml` (LAUNCHER sang `SettingsActivity`, theme khởi động, `taskAffinity`), `SettingsActivity` (splash, mở Onboarding lần đầu), `HomeActivity` (`removeStaleAppTasks` chỉ dọn task Onboarding/Home, Back khi chưa mặc định), `OnboardingActivity`/`ViewModel` (đóng về đâu) | Sửa | 2 |
| `res/values/ds_themes.xml` (font), 9 `strings.xml` (tiêu đề), `SettingsHubScreen`/`MoreAppsSettingsScreen` (tiêu đề riêng) | Sửa | 3 |
| `DsTileDrawable` (bóng), `res/drawable/ds_ic_badge.xml` | Sửa | 4 |

---

### Task 1: Hero Card 2 trạng thái

**Bắt buộc kiểm tay:** có.

- [x] **Step 1:** Layout card cao cố định 140, hai khối chồng trong `FrameLayout`: khối "Chưa" (như RS, nút đổi chữ "Trải nghiệm") và khối "Đã" ("Chào mừng bạn" `Card` + chip `DsBadgeView` "v{versionName}", mô tả `Caption` 3 dòng, "Khám phá ›" `BodyStrong` `ds_accent`, vòng tròn kính 72 bên phải: số theo theme hoặc ảnh xem trước cắt tròn).
- [x] **Step 2:** `ThemeBadge.numberOf(ThemeId): String?` (LIQUID_GLASS_27 → "27", CLASSIC_26 → "26", còn lại null → ảnh `ThemeCatalog.preview`). Chưa chọn theme: "27" (mặc định Liquid Glass 27 như 0.2).
- [x] **Step 3:** Holder nhớ trạng thái đã vẽ; đổi Chưa → Đã khi đang hiện thì cross-fade 300 ms `DecelerateInterpolator`, lần đầu bind không hoạt ảnh. Hub hiện toast "Đã đặt làm mặc định" đúng một lần khi trạng thái đổi Chưa → Đã lúc màn đang mở (`onResume` so trạng thái trước).
- [x] **Step 4:** "Trải nghiệm" và "Khám phá ›" mở `HomeActivity` (Task 2 lo task/Back). Badge "Chưa đặt làm mặc định" không bấm.
- [x] **Step 5:** Chuỗi mới 9 ngôn ngữ: `hero_explore`, `hero_try`, `hero_welcome`, `hero_welcome_desc`, `hero_version`, `hero_default_set_toast`.

### Task 2: Cài đặt là điểm vào app

**Bắt buộc kiểm tay:** có (rủi ro cao: task, Back, Onboarding, launcher mặc định).

- [x] **Step 1:** Manifest: bỏ `LAUNCHER` khỏi `HomeActivity` (giữ `MAIN` + `HOME` + `DEFAULT`). Icon app trỏ `LauncherEntryActivity` (mới: theme khởi động = splash, `taskAffinity` riêng, mở `SettingsActivity` với `EXTRA_FROM_LAUNCHER` rồi đóng). Không gắn LAUNCHER thẳng vào `SettingsActivity`: theme khởi động sẽ lóe splash mỗi lần mở Cài đặt từ Home.
- [x] **Step 2:** `SettingsActivity` mở từ icon và Onboarding chưa xong → mở `OnboardingActivity` (dùng lại cơ chế che + `afterFirstFrame` của Home nếu cần chống nháy).
- [x] **Step 3:** `HomeActivity.removeStaleAppTasks`: chỉ dọn task có gốc là `OnboardingActivity`/`HomeActivity` cũ, không dọn task Cài đặt.
- [x] **Step 4:** Back ở lưới Home khi chưa là mặc định (Q2).
- [ ] **Step 5: Kiểm:** mở từ icon (chưa mặc định / đã mặc định), "Trải nghiệm" → lưới → Back, nút Home của máy, Gần đây, lần đầu cài (Onboarding 0.1 → 0.5, đặt mặc định ở 0.4), Live 4 và Oppo.

### Task 3: Tiêu đề màn đúng chữ, đúng độ đậm

**Bắt buộc kiểm tay:** có.

- [x] **Step 1:** Sửa theme như mục "Vì sao chữ tiêu đề không đậm" (không đo trước được: Claude không chạy máy). Kiểm: tiêu đề, tên theme, tiêu đề nhóm đậm; Onboarding, catalog DS3, hộp thoại, CitySearch vẫn Be Vietnam Pro.
- [x] **Step 2:** Chữ (9 ngôn ngữ): trang chủ (Q1), "Chung", "Ứng dụng ẩn" (tiêu đề + hàng), tiêu đề 6.2 "Ứng dụng khác" (chuỗi mới, hàng giữ "Tải ứng dụng khác").

### Task 4: Bóng ô icon, icon Huy hiệu

- [x] **Step 1:** `DsTileDrawable`: bóng ngoài đen 0.45 y 4 mờ 10 + đen 0.30 y 1 mờ 3 (xấp xỉ bằng gradient như DS3), bỏ bóng màu.
- [x] **Step 2:** Xuất lại `ds_ic_badge` từ `302:1649` (SVG → VectorDrawable như DS3 Task 3).
- [ ] **Step 3: Kiểm:** trang chủ + catalog DS3.

### Task 5: Tài liệu

- [ ] `Figma-Draft.md` (hàng 3 Home cài đặt, ghi chú bản Figma mới), `project_structure.md`, bài học, tick plan.

---

## Nhật ký

| Ngày | Task | Việc | Ghi chú |
|---|---|---|---|
| 2026-11-18 | — | Đọc Figma bản mới (Hero Card, ghi chú, bóng ô icon), code sau PB/LG (`main` @ `17c51fc`), viết plan | Chờ duyệt + Q1–Q3; chờ duyệt đóng RS |
| 2026-11-18 | — | Chủ dự án duyệt plan, Q1–Q3 theo khuyến nghị; RS đóng | Bắt đầu Task 1 |
| 2026-11-18 | 1–4 | Code xong: Hero Card 2 trạng thái + `ThemeBadge`; `LauncherEntryActivity` + Onboarding từ Cài đặt + Back ở Home khi chưa mặc định + `removeStaleAppTasks` thu hẹp; font theme v3 qua `textViewStyle`, tiêu đề (app_name, Chung, Ứng dụng ẩn, Ứng dụng khác) 9 ngôn ngữ; bóng ô icon bằng elevation, `ds_ic_badge` mới | Chờ build + kiểm |
| 2026-11-18 | 1, 2 | Chủ dự án báo: card cao cố định 140 cắt mất nút; bấm "Trải nghiệm" lóe splash. Sửa: card tối thiểu 140, khối ẩn INVISIBLE (cao bằng khối cao hơn); `HomeActivity` về `Theme.Os26Launcher` (theme khởi động chỉ còn ở `LauncherEntryActivity`). Giữ màn trung gian (chủ dự án đồng ý) | Chờ build + kiểm |
| 2026-11-18 | 1 | Card vẫn xén (ảnh chụp: cao đúng 140). Nguyên nhân: cột trong card `match_parent` trong `LinearLayout` ngang wrap_content không được tính chiều cao, bị ép bằng ảnh 116 / vòng tròn 72. Sửa: hai cột wrap_content, bỏ Space co giãn (nút cách badge 12, "Khám phá" cách mô tả 8) | Chờ build + kiểm |
