# G2b — Kiểu icon: tô màu và 3D (TT#12 phần còn lại, TT#20 phần kiểu icon) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ khi cần kính). Lệnh viết cho CMD/PowerShell.

**Nhánh:** `feature/g2b-icon-styles` · **Worktree:** `E:\SourceTree\IosLauncher-wt\G2b-icon-styles` · **PR:** #4 · **Bắt đầu:** 2026-11-04 · **Plan duyệt:** 2026-11-04 (chủ dự án, theo khuyến nghị: hai kiểu loại trừ, không dùng lớp monochrome, năm màu tô)
**Trạng thái:** Chủ dự án kiểm đạt 2026-11-04 (Tô màu v2, 3D); chờ merge PR #4
**Khóa:** không (khóa DataStore, không đổi schema Room)

**Goal:** Thêm hai kiểu icon ngoài kiểu mặc định của TL2: **Tô màu** (icon thành hai tông cùng một màu, nền tối + hình sáng, giống chế độ Tinted của iOS) và **3D** (icon có chiều sâu: vệt sáng phía trên, bóng phía dưới, viền sáng/tối). Người dùng chọn trong Cài đặt, lưu bằng DataStore; đổi xong mọi bề mặt (lưới, dock, thư mục, panel, App Library, tìm kiếm, Ứng dụng đã ẩn) dùng kiểu mới, không nháy icon kiểu cũ, không để icon cũ trong cache. **Hai pha:** Pha A (làm ngay) làm toàn bộ trừ việc làm mới Home đang chạy: kiểu mới hiện ngay ở App Library/tìm kiếm/Ứng dụng đã ẩn và ở Home sau khi Home dựng lại (mở nguội lại app). Pha B (sau khi R1 merge) thêm một hook nhỏ `recreateIfIconStyleChanged` cạnh kiểm tra kính để Home tự dựng lại khi quay từ Cài đặt, giữ nguyên trang và edit mode.

**Architecture:** Mở rộng đường nạp icon duy nhất của TL2. `IconRenderer.render(drawable, sizePx, config)` giữ `flatten → [bước kiểu icon] → mask`; bước kiểu icon: *Tô màu* là một lượt `ColorMatrix` (xám hóa theo độ sáng, ánh xạ hai tông) trên ảnh đầy kín, *3D* là ba lớp gradient vẽ lên bitmap đã cắt (vẽ bằng chính path của `IconShape` nên chống răng cưa, không lòi ra góc trong suốt). Một `IconStyleSource` (`@Singleton`, data) giữ cấu hình kiểu hiện tại trong `StateFlow` lấy từ DataStore; `IconLoader` chờ giá trị thật đầu tiên rồi đọc `.value` (không đọc DataStore mỗi icon) và đưa cấu hình vào khóa cache; `IconStyleSource` tự `IconCache.clear()` khi cấu hình đổi. Màn Cài đặt thêm hàng "Kiểu icon" (+ hàng "Màu tô" khi chọn Tô màu). Làm mới các view của Home (chúng giữ cache bitmap riêng) dùng đúng mẫu `recreateIfGlassOverrideChanged` của `HomeActivity`: `recreate()` xóa cache view, ViewModel giữ trạng thái nên trang và edit mode còn nguyên. Hook đó ở pha B vì `HomeActivity` đang thuộc R1.

**Tech Stack:** Kotlin, `android.graphics` (`ColorMatrixColorFilter`, `LinearGradient`, `Path`), DataStore Preferences có sẵn. Không thêm dependency.
**Spec:** TT#12, TT#20 trong `docs/Launcher-Features.md`; `docs/plans/TL2-icon-shape.md` + `docs/knowledgements/TL2-icon-shape.md` (đường nạp icon); `docs/plans/M8a-settings.md` (hàng Cài đặt, khóa DataStore).

## Vì sao chọn thiết kế này

**Vấn đề cần giải (từ code và tài liệu dự án):**
1. Mọi bề mặt lấy icon qua `AppRepository.icon(key, sizePx)` → `IconLoader` → `IconRenderer` (TL2). Đây là chỗ duy nhất xử lý được mọi bề mặt mà không đụng view.
2. **Nhưng** `IconGridView`, `DockView`, `FolderPanelView` mỗi nơi giữ `HashMap<ComponentKey, Bitmap>` riêng, chỉ xóa khi `iconSize` đổi (`bindAllCells`: `if (c.iconSize != iconCacheSize) iconCache.clear()`) và chỉ gọi lại loader khi thiếu trong map. Xóa `IconCache` ở data **không** làm các view này đổi icon. Đây là điểm khó nhất của tính năng, nằm ở file của R1/AN nên không sửa được.
3. `getMonochrome()` chỉ có từ API 33; Live 4 là API 30 (ghi chú TL2). Ngay trên API 33+, phần lớn app không khai lớp monochrome. Tô màu phải chạy từ ảnh đã `flatten`.
4. Chi phí đo ở TL2 trên Live 4, 180px: `flatten` ~2,0 ms, `mask` ~0,75 ms mỗi icon. Oppo có 344 app nhưng icon nạp lười theo trang. Bước kiểu icon phải cùng bậc với `mask`, không đắt hơn `flatten`.
5. `HomeActivity` đã có mẫu đúng: `recreateIfGlassOverrideChanged` trong `onResume` (M8a Task 4): `recreate()` xóa cache view, ViewModel giữ trang/edit mode. Mẫu này nằm trong file R1 đang tách, nên hook cho kiểu icon chờ R1 merge (pha B).

**Định nghĩa hai kiểu (so với iOS: Mặc định, Tối, Trong suốt, Tô màu):**
- **Mặc định** = kết quả TL2 (không đổi).
- **Tô màu** ≈ Tinted của iOS: mọi icon xám hóa rồi nhuộm bằng một màu người dùng chọn; nền tối, hình sáng. iOS làm vậy với mọi icon (không cần app khai lớp riêng) nên cách xám hóa theo độ sáng là đúng tinh thần, không phải phương án tạm.
- **3D** ≈ không có trong iOS hiện nay (kiểu "bóng bẩy" iOS 6/7): giữ nguyên màu icon, thêm vệt sáng, bóng và viền để nhìn nổi khối.
- *Tối* và *Trong suốt* của iOS: ngoài phạm vi (Trong suốt cần kính, thuộc GS/AN). Thêm *Tối* sau này rẻ vì chỉ là một bước nữa trong cùng chỗ cắm.

**Phương án cho Tô màu:**
| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| T1. Chỉ lớp `getMonochrome()` | Vẽ lớp monochrome của adaptive icon bằng màu tô trên nền tối | Đẹp nhất khi app có lớp này (hình sạch, không dính nền) | Chỉ API 33+: Live 4 (API 30) không có gì để làm. Trên Oppo (API 34) app không khai lớp thì vẫn phải có đường dự phòng, nên vẫn phải viết T2. Hai nhóm icon trông khác nhau trên cùng màn hình | Không (là phần thêm sau) |
| **T2. Hai tông từ độ sáng của ảnh đã `flatten`** | Một `ColorMatrixColorFilter`: L = 0,299R+0,587G+0,114B, kênh ra = tối + L×(sáng−tối); đảo L nếu độ sáng trung bình của icon cao (nền trắng) để nền luôn tối | Chạy mọi API, mọi icon (adaptive, legacy, vector); một lượt vẽ, không `getPixels`; nhìn đồng nhất cả màn hình; khớp ghi chú TL2 | Icon nhiều màu sáng đều (nền sáng, hình sáng) có thể mất tương phản; cần ngưỡng đảo chỉnh bằng mắt | **Chọn** |
| T3. Lai T1 + T2 | 33+ có monochrome thì dùng T1, còn lại T2 | Đẹp hơn trên máy mới | Hai đường code, hai kiểu nhìn trên cùng một màn, phần T1 không ai kiểm tay được trên Live 4 (không có API 33) | Hoãn; chủ dự án quyết ở Q3 |
| T4. `ColorFilter` ở view (`ImageView.setColorFilter`) | Nhuộm lúc vẽ | Đổi kiểu không cần nạp lại | Phải sửa mọi view (file của AN/R1); nhiều chỗ vẽ icon bằng `Canvas` chứ không `ImageView` | Không |

**Phương án cho 3D:**
| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| D1. **Nướng ba lớp gradient vào bitmap** | Sau `mask`, vẽ lên bitmap bằng path của `IconShape`: (1) gradient trắng trong suốt từ đỉnh xuống ~55% chiều cao; (2) gradient đen trong suốt từ ~55% xuống đáy; (3) viền trong (stroke thụt vào) sáng ở trên, tối ở dưới | Một đường nạp, mọi bề mặt tự có; chỉ vẽ trong hình nên góc vẫn alpha 0; chi phí cùng bậc `mask` | Không có bóng đổ ra ngoài icon (bitmap đã bằng đúng hộp, bóng ngoài sẽ bị cắt); nhìn "nổi" vừa phải | **Chọn** |
| D2. Thu nhỏ hình trong bitmap để chừa chỗ cho bóng đổ | Vẽ hình nhỏ ~92%, bóng mờ xung quanh | Nhìn nổi hơn | Làm cỡ icon khác kiểu Mặc định (đã chỉnh `ICON_SCALE` 0,9 ở TL2 theo ý chủ dự án); dock và thư mục tính cỡ theo ô nên lệch; tốn một lần blur | Không; nếu muốn bóng thì xét sau |
| D3. Bóng/độ cao bằng view (`elevation`, `ViewOutlineProvider`) | Hệ thống vẽ bóng | Bóng thật, rẻ | Phải sửa `IconCellView`, các adapter và panel (AN/R1); không phủ icon vẽ bằng `Canvas` (ô thư mục, icon kéo) | Không |
| D4. `RenderEffect`/shader runtime (AGSL) | Hiệu ứng trên GPU | Linh hoạt | Cần API 31–33+; Live 4 (API 30) không có | Không |

**Phương án để đổi kiểu lan tới các view:**
| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| P1. View lắng nghe "phiên bản kiểu" và tự xóa cache riêng | Flow mới trong `AppRepository`; `IconGridView`/`DockView`/`FolderPanelView` thu và `iconCache.clear()` + bind lại | Mượt nhất, không dựng lại Home | Sửa file của R1 và AN | Không (chỉ xét nếu `recreate()` gây nháy) |
| P2. `SettingsActivity` dựng lại Home bằng `NEW_TASK\|CLEAR_TASK` | Settings tự mở lại Home trong task mới | Không cần sửa `HomeActivity` | **Bị loại (review 1/5):** là cách vòng quanh một file chỉ tạm thời chưa sửa được; mất vị trí trang và edit mode; phụ thuộc hành vi cờ task với Home `singleTask` + `clearTaskOnLaunch` (phải kiểm trên máy); Settings cũng bị tạo lại | Không |
| **P3. Pha A + pha B: dữ liệu làm trước, hook `recreate()` sau** | Pha A: mọi thứ trừ làm mới Home. Pha B: `recreateIfIconStyleChanged` cạnh kiểm tra kính trong controller mới của R1 | Dùng đúng mẫu đã chạy trên máy (đổi mức kính); `recreate()` xóa cache view, ViewModel giữ trang/edit mode; hook vài dòng; pha A kiểm được bằng mở nguội lại app | Giữa hai pha, đổi kiểu trong Cài đặt chỉ hiện ở Home sau lần Home dựng lại | **Chọn** |
| P4. Vẽ lại bitmap tại chỗ (cùng object `Bitmap`) | Cache giữ drawable gốc, vẽ đè lên bitmap mà view đang giữ | Không dựng lại gì | View không biết để `invalidate`; đua luồng khi RenderThread đang đọc bitmap; bitmap đã bị LRU bỏ nhưng view còn giữ thì vẫn cũ; phải giữ drawable của 344 app | Không |

**Hook pha B (để R1 đặt đúng chỗ).** Hiện `HomeActivity` có `appliedGlassOverride` (gán lúc dựng dock), `recreateIfGlassOverrideChanged()` gọi trong `onResume`, và `HomeViewModel.currentGlassLevelOverride()` (đọc `settings.first()`). R1 chuyển các thứ này vào controller mới; thêm ngay cạnh chúng, cùng chỗ gán và cùng chỗ gọi:
```kotlin
// ViewModel, cạnh currentGlassLevelOverride: đọc một lần, không collect
suspend fun currentIconStyleKey(): IconStyleKey =
    settingsRepository.settings.first().let { IconStyleKey(it.iconStyle, it.iconTint) }

// Controller của R1, chỗ nó đã gán appliedGlassOverride và đăng ký RecreateGuard cho kính:
// R1 (PR đầu) thêm RecreateGuard; hook của G2b chỉ là một dòng sau khi R1a merge
val appliedIconStyle = viewModel.currentIconStyleKey()          // cùng lúc với giá trị kính đã áp
recreateGuard.add { viewModel.currentIconStyleKey() != appliedIconStyle }
```
(Chữ ký thật của `RecreateGuard.add` theo PR R1a; hình dạng trên là ý định: một lambda trả "cần recreate?".) `IconStyleKey` là data class nhỏ ở domain (`IconStyle` + `IconTint`), nhánh G2b tạo sẵn ở Task 1. `RecreateGuard` của R1 gom mọi điều kiện nên đổi kính và kiểu icon cùng lúc chỉ dựng lại một lần. G2b chỉ cần `currentIconStyleKey()` ở ViewModel (cũng là chỗ R1a đang di chuyển; thêm sau khi R1a merge).

**Phương án cho nơi đọc cấu hình kiểu trong tầng data:**
| Phương án | Ưu | Nhược | Chọn? |
|---|---|---|---|
| S1. `IconLoader` đọc `settings.first()` mỗi lần nạp | Không lệch giá trị | Một lần đọc DataStore cho mỗi icon (review 1/5: tránh) | Không |
| **S2. `IconStyleSource` `@Singleton` giữ `StateFlow<IconRenderConfig?>`, loader chờ giá trị thật đầu tiên** | Đọc `.value` gần như miễn phí; lần nạp đầu sau khi process khởi động chờ giá trị thật nên không bao giờ dùng mặc định; là chỗ duy nhất biết khi nào cấu hình đổi để `clear()` cache | Thêm một singleton; có cửa sổ vài ms giữa lúc DataStore commit và lúc StateFlow cập nhật | **Chọn** |

**Cách S2 bảo đảm lần nạp đầu đúng kiểu.** `IconStyleSource(settingsRepository, iconCache, @ApplicationScope scope)` có `val config: StateFlow<IconRenderConfig?> = settingsRepository.settings.map { IconRenderConfig(it.iconStyle, it.iconTint) }.distinctUntilChanged().onEach { new -> if (previous != null && previous != new) iconCache.clear(); previous = new }.stateIn(scope, SharingStarted.Eagerly, null)`. Giá trị ban đầu là `null`, không phải mặc định. `suspend fun current(): IconRenderConfig = config.value ?: config.filterNotNull().first()`. `IconLoader.load` gọi `current()` trước khi tra cache: lần nạp đầu treo vài ms tới khi DataStore phát giá trị đầu rồi chạy tiếp, các lần sau chỉ đọc biến. Hilt dựng singleton khi `IconLoader` được tiêm, và `Eagerly` bắt đầu đọc DataStore ngay từ lúc đó. Cửa sổ vài ms sau một lần ghi: icon nạp ngay trong cửa sổ mang khóa kiểu cũ (nằm đúng khóa cũ, không bao giờ bị trả cho kiểu mới) và bị `clear()`/LRU dọn. Ở pha B, Activity mới của Home chờ `settingsLoaded` mới nạp icon nên gần như không chạm cửa sổ này.

**Vì sao chọn bộ này:** T2 + D1 + P3 + S2 là bộ duy nhất vừa (a) chạy trên API 30 của máy kiểm chính, (b) pha A nằm trọn trong vùng file được phép của nhánh và pha B chỉ là một hook theo mẫu đã chạy, (c) giữ nguyên cỡ và hình hộp icon để không phá `ICON_SCALE`/lưới/ô thư mục, (d) có chi phí cùng bậc `mask` đã đo ở TL2. Nguồn: code `IconGridView.bindAllCells` (đọc 2026-11-04) cho vấn đề cache view; số đo TL2 (180px, Live 4) cho chi phí; ghi chú `getMonochrome` API 33 trong TL2.

**Đánh đổi chấp nhận:** (1) Giữa pha A và pha B, Home đang chạy chỉ đổi kiểu sau khi dựng lại; sau pha B, Home `recreate()` một lần khi quay từ Cài đặt (giống đổi mức kính, đã chấp nhận ở M8a). (2) 3D không có bóng đổ ngoài icon (D2 bị loại vì lệch cỡ). (3) Tô màu có thể mất tương phản với vài icon sáng đều; ngưỡng đảo là hằng "CHỈNH TAY". (4) Trên máy API 33+ chưa dùng lớp monochrome (T1/T3 hoãn).

## Quyết định đã chốt
| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| D1 | Kiểu là một lựa chọn duy nhất: `DEFAULT`, `TINTED`, `THREE_D` (không kết hợp Tô màu + 3D ở bản đầu) | Plan, 2026-11-04; chỗ cắm cho phép kết hợp sau. Chờ chủ dự án duyệt (Q2) |
| D2 | Tô màu dùng độ sáng của ảnh đã `flatten`, mọi API như nhau; không dùng `getMonochrome()` | Plan, 2026-11-04: Live 4 là API 30, ghi chú TL2 |
| D3 | Khóa DataStore `icon_style` (tên enum) và `icon_tint` (tên enum); giá trị lạ/null rơi về mặc định bằng `fromName`, không `valueOf` | Theo mẫu `GridPreset.fromName` (M8a) |
| D4 | Cấu hình kiểu nằm trong khóa cache (hậu tố vào `cacheKey`), và `IconStyleSource` gọi `cache.clear()` khi cấu hình đổi | Plan, 2026-11-04; chọn "cả hai" mà TL2 để ngỏ: khóa để đúng, `clear()` để giải phóng RAM |
| D5 | Làm mới Home: `recreateIfIconStyleChanged` theo mẫu kính, làm ở pha B sau khi R1 merge; P2 (`CLEAR_TASK`) bị loại | Điều phối, review 1/5, 2026-11-04 |
| D6 | Cấu hình kiểu giữ trong `IconStyleSource` (`StateFlow`, giá trị đầu `null`, loader chờ giá trị thật), không đọc DataStore mỗi icon | Điều phối, review 1/5, 2026-11-04 |

## Ngoài phạm vi / ghi để sau
- Kiểu *Tối*, *Trong suốt* (iOS); chọn màu tô tự do (bảng màu đầy đủ, theo hình nền); dùng `getMonochrome()` (T1/T3); bóng đổ ngoài icon (D2).
- P1 (view tự làm mới không dựng lại Home): chỉ xét nếu `recreate()` của pha B gây nháy khó chịu.
- **Pha B** (hook `recreateIfIconStyleChanged`, mục "Hook pha B"): làm sau khi R1 merge, trong controller mới của R1; không thuộc nhánh này cho tới lúc đó.
- Lỗi "đổi ảnh nền kính chưa vẽ lại" thuộc AN; GS (cài đặt kính theo phần tử) thêm hàng Cài đặt sau nhánh này.

## Vùng file
| File | Tạo / Sửa | Nóng (độc quyền / dùng chung) |
|---|---|---|
| `domain/model/IconStyle.kt` (enum `IconStyle`, `IconTint` + `fromName`) | Tạo | Độc quyền |
| `domain/model/LauncherSettings.kt` (thêm `iconStyle`, `iconTint`) | Sửa | Dùng chung (chỉ thêm cuối) |
| `domain/repository/SettingsRepository.kt` (thêm `setIconStyle`, `setIconTint`) | Sửa | Dùng chung (chỉ thêm cuối) |
| `data/settings/SettingsKeys.kt`, `SettingsRepositoryImpl.kt` | Sửa | Dùng chung (khối riêng ở cuối) |
| `data/apps/IconRenderer.kt` (cấu hình + bước kiểu icon) | Sửa | Độc quyền |
| `data/apps/IconLoader.kt`, `IconCache.kt` (cấu hình vào khóa) | Sửa | Độc quyền |
| `data/apps/IconStyleSource.kt` (`StateFlow` cấu hình, `clear` cache khi đổi) | Tạo | Độc quyền |
| `ui/settings/SettingsRow.kt`, `SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsActivity.kt` | Sửa (thêm) | Dùng chung (khối riêng ở cuối; GS sẽ thêm sau) |
| `res/values/strings.xml` | Sửa (khối G2b ở cuối) | Dùng chung |
| `dimens.xml`, `colors.xml` | Không sửa (màu tô nằm trong enum `IconTint`, hằng 3D nằm trong `IconRenderer`) | — |
| **Không đụng:** `HomeActivity.kt`, `activity_home.xml`, `HomeViewModel.kt`, `IconGridView`, `IconCellView`, `DockView`, `DragLayer`, `DragHelper`, `FolderPanelView`, `CategoryPageView`, `CategoryGroupPanelView`, `BackgroundGlass`, `AppRepository(Impl)`, `IconShape.kt` | — | Của R1/AN |

`AppRepository.icon(key, sizePx)` giữ nguyên chữ ký, nên không bề mặt nào phải sửa.

## Global Constraints
- Luật `ui → domain ← data`: `IconStyle`/`IconTint` ở domain (thuần Kotlin; màu tô lưu bằng `Int` ARGB hoặc HSV, không import `android.graphics`). `IconRenderer` ở data dùng chúng.
- `IconRenderer` gọi song song từ nhiều coroutine: không giữ `Paint`/`Path`/`Bitmap` dùng chung (như TL2); tạo mới mỗi lần. Bitmap tạm phải `recycle()` trên mọi đường.
- Tô màu: một `ColorMatrixColorFilter` dựng mỗi lần từ cấu hình (nhỏ), không `getPixels` toàn ảnh. Độ sáng trung bình lấy bằng ảnh thu nhỏ rất nhỏ (cỡ 8×8, `Bitmap.createScaledBitmap`, `filter = true`) hoặc suy từ ảnh mẫu sẵn có của `flattenLegacy` khi có.
- 3D: vẽ lớp phủ bằng `drawPath(IconShape.DEFAULT.path(...))` với shader gradient, **không** `clipPath` (canvas phần mềm không chống răng cưa khi clip) và không `DST_IN` (bài học TL2: không xóa được góc).
- Ngoài icon (góc) alpha phải còn 0 sau mọi kiểu (Review Focus 1).
- Mọi chuỗi hiển thị trong `strings.xml` tiếng Việt; tên file/định danh tiếng Anh.
- Không thêm test, không đổi schema Room, không đoán version thư viện, không cài APK khi chưa được bảo.

## Review Focus
1. **Góc vẫn trong suốt** với cả ba kiểu (alpha 0 ở bốn góc).
2. **Không icon kiểu cũ nào sau khi đổi và Home dựng lại** (pha A) hoặc ngay khi quay từ Cài đặt (pha B): lưới, dock, ô thư mục (icon mini), panel thư mục, panel nhóm Library, App Library, tìm kiếm, Ứng dụng đã ẩn, icon đang kéo, icon nổi của menu nhấn giữ.
3. **Cấu hình nằm trong khóa cache**; nạp đang bay lúc đổi kiểu không ghi đè icon mới bằng icon cũ.
4. **Không nháy**: lần mở nguội đầu tiên với kiểu đã lưu hiện đúng kiểu ngay từ icon đầu tiên (không qua kiểu Mặc định): `IconStyleSource` có giá trị đầu `null` và loader chờ giá trị thật.
5. **API < 33** chạy bình thường (không gọi `getMonochrome`, không lỗi trên Live 4); không code nào phụ thuộc API 33.
6. **Chi phí/icon** không tăng quá bậc của `mask` (đo ở Task 4); không `getPixels` toàn ảnh trên đường nóng.
7. **Không sửa file ngoài vùng khai**; `git diff --stat` không chạm file của R1/AN.
8. **Cài đặt**: chọn đúng kiểu đang dùng thì không ghi; ghi lỗi thì Toast và hàng về giá trị thật.

---

### Task 1: Nền móng và hàng Cài đặt (pha A)
**Bắt buộc kiểm tay:** không (đã bỏ dấu sau review 1/5: không còn giả định máy thật nào mà Task sau xây trên)
**Files:** `domain/model/IconStyle.kt` (tạo: `IconStyle`, `IconTint`, `IconStyleKey`), `LauncherSettings.kt`, `SettingsRepository.kt`, `SettingsKeys.kt`, `SettingsRepositoryImpl.kt`, `data/apps/IconStyleSource.kt` (tạo), `IconLoader.kt`, `IconCache.kt`, `IconRenderer.kt` (chỉ nhận tham số `config`, chưa đổi hình), `ui/settings/*`, `strings.xml`.
**Interfaces:**
- Consumes: `SettingsRepository.settings`, `IconCache`.
- Produces: `IconStyle`, `IconTint`, `IconStyleKey`, `LauncherSettings.iconStyle/iconTint`; `IconStyleSource.current()`; `IconRenderer.render(drawable, sizePx, config)` (kiểu khác Mặc định tạm thời ra cùng hình Mặc định).
- [x] **Step 1:** `IconStyle` (`DEFAULT, TINTED, THREE_D`), `IconTint` (`BLUE, PINK, GREEN, ORANGE, GRAPHITE`) kèm `fromName`; mặc định `DEFAULT`/`BLUE`. Thêm trường vào `LauncherSettings`, hai khóa DataStore, hai hàm ghi, đọc bằng `fromName`.
- [x] **Step 2:** `IconStyleSource` như mục "Cách S2 bảo đảm lần nạp đầu đúng kiểu". `IconLoader.load`: `val config = styleSource.current()` rồi `cache.get/put(key, sizePx, config)`; `cacheKey` thêm hậu tố cấu hình, giữ tiền tố `package/` để `evict(packageName)` vẫn đúng.
- [x] **Step 3:** Cài đặt: nhóm "Giao diện" thêm hàng giá trị "Kiểu icon" (hộp thoại chọn một, ba lựa chọn, như hộp thoại mức kính) và, chỉ khi đang chọn Tô màu, hàng "Màu tô" (năm lựa chọn). `SettingsViewModel` ghi, lỗi thì Toast `settings_save_failed` như `onGlassOverrideChosen`; chọn đúng giá trị đang dùng thì không ghi. Không dựng lại Home ở nhánh này.
- [x] **Step 4: Kịch bản kiểm (Live 4)** — mở app: `adb shell am start -n gambi.global.os26launcher/.ui.home.HomeActivity` (máy mặc định dùng launcher gốc). Vào edit mode → Cài đặt. (a) Hàng "Kiểu icon" hiện, mặc định "Mặc định"; chọn "Tô màu": hàng "Màu tô" hiện, chọn lại Mặc định: hàng ẩn; (b) `am force-stop gambi.global.os26launcher` rồi mở lại: Cài đặt vẫn nhớ lựa chọn; (c) không crash (`adb logcat`); (d) Home hiện icon như TL2 (hình chưa đổi ở Task này, bình thường).

---

### Task 2: Kiểu Tô màu
**Bắt buộc kiểm tay:** không
**Files:** `data/apps/IconRenderer.kt`, `domain/model/IconStyle.kt` (giá trị màu), `strings.xml`.
**Interfaces:** Consumes `IconRenderConfig`; Produces bước `tint(content, config)` giữa `flatten` và `mask`.
- [x] **Step 1:** Trong `render`: `flatten` → `when (style) { TINTED -> tint(...) }` → `mask`. `tint`: đo độ sáng trung bình bằng ảnh thu nhỏ; chọn tối/sáng từ `IconTint` (HSV: tối = (h, s, V≈0,20), sáng = (h, s×0,35, V=1,0), Graphite s=0); dựng `ColorMatrix` (xám hóa rồi ánh xạ hai tông, đảo độ sáng nếu trung bình > `TINT_INVERT_LUMA`); một `drawBitmap` với `ColorMatrixColorFilter` sang bitmap kết quả. Mọi hằng số gắn nhãn "CHỈNH TAY" như TL2.
- [x] **Step 2:** Bitmap tạm recycle trên mọi đường; không giữ `ColorMatrix` dùng chung.
- [x] **Step 3: Kịch bản kiểm (Live 4)** — (a) chọn Tô màu/Xanh rồi `am force-stop` và mở lại app (pha A chưa có hook làm mới Home): lưới, dock đều nền tối hình sáng cùng tông xanh; icon nền trắng (app legacy) cũng ra nền tối, không còn mảng trắng chói; (b) đổi sang Hồng, Xám than: màu đổi, vẫn nhận ra app; (c) mở thư mục: icon mini và panel cùng kiểu; mở Library và tìm một app: cùng kiểu; (d) bốn góc icon trong suốt (phóng ảnh chụp màn hình); (e) chọn lại Mặc định: về đúng hình TL2.

---

### Task 3: Kiểu 3D
**Bắt buộc kiểm tay:** không
**Files:** `data/apps/IconRenderer.kt`.
**Interfaces:** Produces bước `bevel(masked, sizePx)` sau `mask`.
- [x] **Step 1:** `bevel`: tạo `Paint` mới; (1) vệt sáng: `LinearGradient` dọc từ trắng α≈0,30 ở đỉnh tới trong suốt ~55% chiều cao; (2) bóng: gradient đen từ trong suốt ở ~55% tới α≈0,22 ở đáy; (3) viền trong: `drawPath` kiểu STROKE với `IconShape.DEFAULT.path(size - 2×inset)` đặt lệch `inset`, bề rộng ≈ `size/96`, gradient sáng trên → tối dưới. Tất cả vẽ bằng `drawPath` của hình, không `clipPath`. Hằng "CHỈNH TAY".
- [x] **Step 2: Kịch bản kiểm (Live 4)** — (a) chọn 3D: icon nhìn nổi khối, màu icon còn nguyên, không viền hở ở góc, không răng cưa ở cạnh (phóng 4× ảnh chụp); (b) icon rất sáng (nền trắng) và rất tối đều đọc được; (c) dock và ô thư mục (icon mini nhỏ) không thành mảng đục; (d) đổi qua lại Mặc định, Tô màu, 3D vài lần (mỗi lần force-stop rồi mở lại; App Library và Ứng dụng đã ẩn mở từ Cài đặt thì đổi ngay): mỗi lần đúng kiểu, không lẫn.

---

### Task 4: Đo chi phí, hồi quy và tinh chỉnh
**Bắt buộc kiểm tay:** không (phép đo do điều phối chạy theo quyết định 7 của workflow)
**Files:** `data/apps/IconRenderer.kt` (log tạm, xóa trước khi xong), knowledgement.
- [x] **Step 1:** Log tạm thời gian `flatten` / bước kiểu / `mask` trung bình trên 20 icon đầu cho từng kiểu (cùng cách đo TL2: mở nguội 3 lượt, `adb logcat`), điền vào Nhật ký. Mục tiêu: bước kiểu ≤ ~1,5× `mask` (xem Ước tính bên dưới). Vượt thì tối ưu (giảm số lớp 3D, dùng ảnh thu nhỏ nhỏ hơn) trước khi xong.
- [x] **Step 2:** Xóa log tạm (`git grep` log tag không còn). Chỉnh hằng theo mắt chủ dự án nếu có ý kiến ở bài kiểm cuối.
- [x] **Step 3:** Hoàn thiện knowledgement `docs/knowledgements/G2b-icon-styles.md` (theo mẫu mục 8, sơ đồ Mermaid luồng đổi kiểu).
- [x] **Step 4: Kịch bản kiểm (Live 4)** — cuộn qua nhiều trang kiểu Tô màu và 3D, so với Mặc định: không giật thêm rõ rệt; `gfxinfo` mở nguội không xấu đi đáng kể.

**Ước tính chi phí mỗi icon (ước tính trước khi đo; số đo thật ngay bên dưới, TRƯỢT mục tiêu):** nền Live 4 180px: `flatten` ~2,0 ms, `mask` ~0,75 ms (TL2). Tô màu: một lượt vẽ có `ColorMatrixColorFilter` trên cùng số điểm ảnh như `mask` + ảnh thu nhỏ 8×8, ước tính +0,5–1 ms. 3D: ba lượt `drawPath` gradient, ước tính +1–2 ms. Tức khoảng +15–50% trên ~3 ms hiện tại; nạp lười nên Oppo (344 app) chỉ trả cho icon trên trang đang hiện; đổi kiểu nạp lại ~24–40 icon trên Home + Library đang mở.

**Số đo Task 4 (Live 4, 144 px, `c5bc520`, 3 lượt mở nguội, ms mỗi icon, điều phối đo):** Mặc định bước kiểu 0,01; **Tô màu 4,67 / 4,42 / 3,84**; **3D 3,18 / 2,26 / 3,69**; `mask` 0,8–3,7; `flatten` 1,2–4,2 (chi tiết trong knowledgement mục 7). **Trượt** mục tiêu "bước kiểu <= ~1,5x mask" ở cả hai kiểu và ước tính ban đầu thấp hơn thực tế. Điều phối chấp nhận: luồng nền, một lần mỗi icon rồi vào cache, chỉ khi chọn kiểu đó, Mặc định không ảnh hưởng.

---

### Task 5: Làm lại Tô màu, cải thiện 3D (sau khi chủ dự án xem máy, 2026-11-04)
**Bắt buộc kiểm tay:** không (điều phối cài và xem trên máy trước khi đưa chủ dự án)
**Lý do:** chủ dự án xem bản Task 2 trên Live 4: Tô màu "không đẹp", phải làm lại; 3D chấp nhận được, cải thiện nếu được. Lỗi gốc của Task 2: ma trận màu áp lên CẢ ảnh (nền + hình), nên ô nền mỗi app có tông khác nhau (xanh nhạt, tối, đục) và bộ icon lệch.

**Nghiên cứu (tra 2026-11-04, nguồn là bài báo và diễn đàn, không phải tài liệu chính thức của Apple/Google nên nhãn "đọc tài liệu bên thứ ba"):**
- iOS 18 Tinted: bản tô màu là ảnh xám với "hình và nền xác định rõ", hệ thống tô màu lên mẫu xám đó ([Kuatsu](https://kuatsu.de/en/blog/ios18-app-icons)). Thiếu bản tô màu thì hệ thống tự áp màu lên icon gốc, có thể ra kết quả không như ý ([Create with Swift](https://createwithswift.com/preparing-your-app-icon-for-dark-and-tinted-appearance)); các bài viết khác nói thiếu bản tối thì hệ thống cố tự dựng ([Apple Developer Forums](https://developer.apple.com/forums/thread/758883)). Chưa tìm được câu trả lời chính thức cho trường hợp không có bản tô màu.
- Android themed icons: lớp `<monochrome>` là hình sạch không mặt nạ không bóng, launcher tô bằng bảng màu Material You ([Pro Android Dev](https://proandroiddev.com/implement-themed-icons-android-13-d20b89233681), [Android Central](https://www.androidcentral.com/apps-software/how-to-enable-themed-icons-in-android-13)). Hình trong icon của Google chiếm khoảng một nửa ô, hình tràn viền trông quá to ([ghi chú PR](https://github.com/danielchalmers/JournalApp/pull/104)). Android 16 QPR2 tự tạo themed icon cho app thiếu lớp monochrome bằng bộ lọc màu đơn sắc lên icon gốc (bản beta, [Android Authority](https://www.androidauthority.com/android-icon-colors-3589663/), [Android Central](https://www.androidcentral.com/apps-software/android-16s-next-big-update-brings-auto-themed-icons-and-apps-cant-opt-out)). Đó đúng là cách của Task 2 và chủ dự án thấy chưa đẹp, nên không chép.
- Launcher bên thứ ba: Lawnchair chỉ themed được icon có lớp monochrome hoặc có trong gói Lawnicons, không tự sinh cho app khác; tự sinh nền/mặt nạ cho icon thiếu "chưa hỗ trợ" ([Lawnchair docs](https://docs.lawnchair.app/developers/concepts/icon-packs)). Nova (5.5 beta) chỉ chọn màu nền cho icon legacy từ chính bảng màu của icon ([Android Central](https://androidcentral.com/adaptive-icons-make-jump-nova-launcher-its-latest-beta-update)). Không tìm được tài liệu về Niagara/Smart Launcher. Kết luận: không có công thức công khai nào hơn "tách hình khỏi nền rồi tô một màu trên ô đồng nhất"; chọn cách đó vì khớp cả hướng dẫn iOS lẫn Android và vì dự án đã có sẵn foreground/background tách rời từ `AdaptiveIconDrawable` (khoảng 95% icon trên Live 4, đo ở TL2).
- Lớp `getMonochrome()`: không có lý do đủ mạnh để thêm (vẫn chỉ API 33+, chỉ Oppo kiểm được; phần lớn app không có). Giữ nguyên quyết định D2.

**Thiết kế mới:**
- Adaptive có foreground "tách được hình": BỎ background, vẽ mọi icon trên cùng một ô tối (gradient nhẹ từ trên xuống, tông của màu tô), rồi vẽ riêng foreground qua một `ColorMatrixColorFilter` ánh độ sáng vào khoảng [hình tối nhất, hình sáng nhất] của màu tô, giữ alpha. Hình không bao giờ tối hơn ~62% nên luôn đọc được trên ô tối; đổ bóng mềm theo độ sáng bên trong hình. Hình thu 0,86 để ô còn đọc là ô.
- "Tách được hình" = vẽ thử foreground ở 16×16: có điểm trong suốt và dưới 85% điểm viền đục.
- Dự phòng (đường Task 2, giữ nguyên): legacy, foreground phủ kín (ảnh, icon game, gradient tràn viền) hay rỗng: flatten như thường rồi hai tông theo độ sáng, đảo nếu viền sáng. Trông: các icon này vẫn có ô tối nhưng chi tiết bên trong là xám-nhuộm, có thể lệch với các icon còn lại.
- 3D: gộp vệt sáng và bóng thành MỘT gradient dọc nhiều điểm dừng (sáng ở đỉnh, trong suốt ở giữa, tối ở đáy), thêm một elip bóng loáng (trắng đậm ở đỉnh, nhạt dần, cạnh dưới elip tạo vệt như kính) và giữ viền vát trong. Vẫn ba lượt vẽ như trước (nên chi phí gần như cũ), góc vẫn alpha 0, không bóng đổ ngoài.
- Chi phí: đường mới bỏ ảnh thu nhỏ 8×8 và lượt lọc cả ảnh; thay bằng một lượt vẽ foreground có bộ lọc + một ảnh thử 16×16. Kỳ vọng không chậm hơn Task 2 (ước tính, chưa đo; điều phối đo lại như Task 4).
- [x] **Step 1:** nghiên cứu, ghi nguồn (trên).
- [x] **Step 2:** `tintGlyphTile`, `foregroundIsGlyph` trong `IconRenderer`; đường cũ thành dự phòng. Hằng số trong khối "CHỈNH TAY (Tô màu, đường ô tối + hình một màu)".
- [x] **Step 3:** `bevel` mới (khối + bóng loáng + viền). Hằng trong khối "CHỈNH TAY (kiểu 3D)".
- [x] **Step 4: Kịch bản kiểm (Live 4, điều phối cài)** — (a) Tô màu Xanh: mọi icon cùng ô tối, hình sáng một màu; icon nền trắng (Drive, Dịch, Chrome) cũng ô tối, hình sáng; (b) so với ảnh `styles.png` cũ: không còn ô nhạt/ô đục lẫn lộn; (c) các icon dự phòng (ảnh/game như Dragon Boy, legacy): ghi lại trông thế nào; (d) 5 màu; (e) 3D: nổi khối rõ hơn bản trước, góc trong suốt, icon mini trong thư mục không đục; (f) đo `G2bPerf` lại nếu cần (log đã xóa ở Task 4, thêm lại tạm thời nếu điều phối muốn số).

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)
Máy: Vsmart Live 4 (API 30). Có sẵn ít nhất: một app icon adaptive, một app icon legacy nền trong suốt, một thư mục, vài app trên 2 trang. Mở: `adb shell am start -n gambi.global.os26launcher/.ui.home.HomeActivity`.
1. Cài đặt → Kiểu icon: mặc định "Mặc định"; chọn Tô màu → hàng "Màu tô" hiện (Task 1, 2).
2. Pha A: force-stop, mở lại: mọi icon trên lưới và dock đã Tô màu (Task 2); lặp với Xanh/Hồng/Xám than. Pha B (sau khi hook vào): Back từ Cài đặt thì Home tự `recreate()`, icon đổi ngay, vẫn ở trang cũ và edit mode còn nguyên.
3. Mở thư mục, panel nhóm Library, tìm một app, Ứng dụng đã ẩn: cùng kiểu.
4. Chọn 3D: icon nổi khối, góc trong suốt (Task 3). Kéo một icon: icon đang kéo cùng kiểu.
5. Quay về Mặc định: đúng như TL2.
6. Tắt hẳn app (`am force-stop`), mở lại: icon đầu tiên hiện đã đúng kiểu đã lưu, không nháy qua Mặc định.
7. Hồi quy: gỡ/cài lại một app (icon quay lại đúng kiểu), đổi cỡ lưới và bật/tắt nhãn (không ảnh hưởng kiểu), đổi mức kính (Home tự dựng lại, kiểu giữ nguyên; sau pha B, đổi cả hai cùng lúc chỉ dựng lại một lần), kéo thả/edit mode, Back trong Cài đặt.
**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** chưa.

## Nhật ký
| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-04 | — | Viết plan | `docs: add G2b plan` | Chờ điều phối rà |
| 2026-11-04 | — | Chủ dự án duyệt plan (2026-11-04); "Hook pha B" cập nhật theo `RecreateGuard` của R1: hook còn một dòng `recreateGuard.add { … }` sau khi R1a merge | (cùng commit Task 1) | Duyệt |
| 2026-11-04 | — | Sửa plan theo review vòng 1/5: bỏ P2 (`CLEAR_TASK`) và must-test Task 1, chia pha A/B với hook `recreateIfIconStyleChanged`, thay `settings.first()` mỗi icon bằng `IconStyleSource`, thêm khuyến nghị cho câu hỏi chủ dự án, xóa Q1/Q5 | `docs: revise G2b plan after review` | Review vòng 1/5 (plan) |
| 2026-11-04 | 1 | Nền móng: IconStyle/IconTint/IconStyleKey, khóa DataStore icon_style/icon_tint, IconStyleSource (StateFlow giá trị đầu null, loader chờ giá trị thật, clear cache khi đổi), kiểu vào khóa cache và vào render, hàng Cài đặt Kiểu icon + Màu tô. Dùng IconStyleKey làm config (không thêm IconRenderConfig). Build xanh. Viết knowledgement nháp | (xem git log) | Chờ review |
| 2026-11-04 | 2 | Kiểu Tô màu: bước tint giữa flatten và mask trong IconRenderer (ColorMatrixColorFilter một lượt, đo độ sáng trên ảnh 8x8, đảo nếu icon sáng), tông từ IconTint. Build xanh. Cập nhật knowledgement (flowchart) | (xem git log) | Chờ review; kịch bản kiểm gộp vào bài kiểm cuối |
| 2026-11-04 | 3 | Kiểu 3D: bevel sau mask trong IconRenderer (vệt sáng, bóng, viền trong; ba drawPath gradient, không clipPath). Build xanh. Cập nhật knowledgement (sequenceDiagram) | (xem git log) | Chờ review; kịch bản kiểm gộp vào bài kiểm cuối |
| 2026-11-04 | 4 | Thêm log đo tạm G2bPerf trong IconRenderer.render (trung bình mỗi 20 icon: flatten, bước kiểu, mask; đánh dấu G2B_PERF). Build xanh. Điều phối chạy: mở nguội 3 lượt cho mỗi kiểu, adb logcat -s G2bPerf. Log sẽ xóa sau khi có số đo | (xem git log) | Chờ số đo |
| 2026-11-04 | 4 | Số đo Live 4 vào plan/knowledgement, ghi trượt mục tiêu (Tô màu ~4,3 ms, 3D ~3,0 ms mỗi icon). Một lượt chỉnh Tô màu theo ảnh chụp: đo độ sáng VIỀN (không phải cả ảnh), ngưỡng 0,35, để phần lớn icon ra ô tối. Xóa log G2B_PERF. Tick Task 1-4. Pha B vẫn mở (chờ R1a) | (xem git log) | Task 1-3 qua review; Task 4 điều phối chấp nhận trượt |
| 2026-11-04 | B | Pha B: R1a đã merge (`7030768`), merge origin/main. Thêm `HomeViewModel.currentIconStyleKey()` và ba dòng trong khối "Dòng nối cho AN/G2b" của `HomeActivity.onCreate` (biến `appliedIconStyle` null tới khi đọc xong, như `DockController.appliedGlassOverride`; `recreateGuard.add { … }`). Build xanh | (xem git log) | Chờ kiểm máy: đổi kiểu rồi Back, Home làm mới, giữ trang và edit mode |
| 2026-11-04 | 5 | Chủ dự án xem máy: Tô màu không đẹp, 3D được. Nghiên cứu (nguồn trong Task 5), làm lại Tô màu thành "ô tối đồng nhất + foreground một màu" cho adaptive (dự phòng đường Task 2 cho legacy/foreground phủ kín), 3D gộp thành một gradient nhiều điểm dừng + elip bóng loáng + viền. Build xanh | (xem git log) | Chờ điều phối cài và xem trên máy |
| 2026-11-04 | Task 5 đóng | Điều phối xem Tô màu v2 trên Live 4 (`715a780`): đa số icon cùng ô tối, hình một màu; yếu: Dịch, TFlat, Viettel Tammi, My VNPT ô sáng hơn, icon ảnh giữ chi tiết. Chủ dự án kiểm và duyệt | (docs) | Xong |

## Bằng chứng Review Focus (điền khi đóng)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1–8 | (điền khi đóng) | | |

## Câu hỏi chỉ chủ dự án trả lời được (kèm khuyến nghị)
- **Q2:** Tô màu và 3D loại trừ nhau? **Khuyến nghị: có.** Một lựa chọn duy nhất, hàng Cài đặt đơn giản; vẫn kết hợp được sau vì hai bước tách riêng trong `IconRenderer`.
- **Q3:** Dùng lớp monochrome trên API 33+? **Khuyến nghị: không ở bản này.** Hai máy trông giống nhau và chỉ Oppo mới kiểm được phần đó; làm sau nếu muốn.
- **Q4:** Năm màu tô? **Khuyến nghị: giữ năm.** Chi phí chỉ là một enum, năm chuỗi và một hộp thoại; không thêm thuật toán hay cột lưu trữ. Muốn cắt thì bỏ hàng "Màu tô" và cố định màu xanh.

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)
- `Launcher-Features.md`: TT#12 → xong (Tô màu, 3D, lựa chọn trong Cài đặt); TT#20 bỏ "kiểu icon" khỏi phần còn lại.
- `project_structure.md`: thêm `domain/model/IconStyle.kt`; ghi `IconRenderer` có bước kiểu icon; hai khóa DataStore mới.
- `room-schema.md`: không đổi.
- Controller của R1: pha B thêm hook `recreateIfIconStyleChanged` (mục "Hook pha B").
- `AGENTS.md`: xem mục "Bài học cho AGENTS.md" trong knowledgement (đặc biệt: cache bitmap riêng của từng view không bị `IconCache.clear()` làm mới).
