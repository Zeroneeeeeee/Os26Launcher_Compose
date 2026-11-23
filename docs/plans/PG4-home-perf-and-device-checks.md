# PG4 — Hiệu năng lướt trang Home (G3) và danh sách kiểm trên máy (K1) — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: plan duyệt rồi mới code; code từng mốc nhỏ, báo cáo, chủ dự án build và kiểm; tick khi chủ dự án báo đạt. Phiên claude.ai qua VM **không build được, không có `adb`**: chủ dự án chạy lệnh build và lệnh đo trên máy mình rồi gửi kết quả. **G3 đo trước rồi mới sửa.**

**Bắt đầu:** 2026-11-23 · **Plan duyệt:** 2026-11-23 (Q1, Q2, Q4–Q6; Q3 quyết sau khi có số đo)
**Trạng thái:** G3 (Task 1–3) xong đo 2026-11-23: kính trong ngưỡng trên cả hai máy, nguyên nhân giật là bản debug; chờ chủ dự án xác nhận đóng G3. K1 (Task 4) chủ dự án đang chạy
**Nhánh:** `feature/pg1-parity` (chủ dự án 2026-11-23: PR #1 bàn giao G4 đã merge vào `master`; G3, K1 làm tiếp trên nhánh này, xong merge vào `master` rồi xoá nhánh) · **Máy:** Oppo PKD130 (Android 14), Vsmart Live 4 (Android 11) · **Đổi schema Room:** không

**Goal:** (G3) biết chính xác vì sao lướt trang Home giật trên Oppo PKD130 (kể cả khi chưa bật kính icon), sửa theo số đo, và chốt ngân sách số bề mặt kính (thật và mờ tĩnh của PG3); (K1) chạy hết danh sách kiểm còn mở của các plan Compose, ghi kết quả, lỗi nào gặp thì tách việc sửa.
**Spec:** `PG-parity-gaps.md` G3, K1; `lessons/glass.md` (cách đo `gfxinfo`, "đo xen kẽ cùng phiên", "debug APK 80 % mẫu ở trình thông dịch"); `lessons/startup-performance.md` (bản debug so với release); JD D9.

## Hiện trạng (đọc code 2026-11-23)

| # | Nghi vấn | Bằng chứng (đọc code) | Cách kiểm |
|---|---|---|---|
| H1 | **Kính thật của dock và widget vẽ lại mỗi khung khi lướt trang** | Comment trong `HomeScreen.kt` (có từ commit đầu `2615d5d`) ghi đã đo "87 % khung giật khi vuốt trang" và "tắt kính thật toàn vùng Home trong lúc pager cuộn hoặc đang kéo", nhưng code ngay dưới luôn `LocalDsBackdrop provides backdrop`: **việc tắt chưa từng có trong code** | Đo lướt trang với / không kính thật (công tắc đo, Task 1) |
| H2 | Không có ngân sách kính trên Home | `GlassBudget` không nơi nào gọi; Today có `glassIds`, Thư viện có `GLASS_TILE_BUDGET`, Home thì widget nào cũng kính thật | Đo trang nhiều widget so với trang ít widget |
| H3 | Kính mờ tĩnh của PG3 | Mỗi bề mặt `drawPlainBackdrop` là một lớp `CompositingStrategy.Offscreen` + ghi lại lớp nền (ô thư mục, pill; dưới API 31 cả dock, widget, card) | Đo với / không kính mờ tĩnh |
| H4 | Bản debug chậm hơn nhiều | Bài học GL: APK debug ~80 % mẫu ở trình thông dịch; bản release bật R8 + baseline profile | Đo trên bản tối ưu (Q1) |
| H5 | Phần còn lại của trang (icon, nhãn, huy hiệu, dựng trang kế khi lướt) | Chưa đọc kỹ; chỉ xét sau khi H1–H4 đã loại | Đo với kính tắt hết; nếu vẫn giật thì lấy trace Perfetto |

## Hướng làm

- **Công tắc đo** chỉ có trong bản đo: đọc một thuộc tính hệ thống (`adb shell setprop debug.os26.perf <cờ>`, đọc lúc `HomeActivity.onCreate`, đổi rồi `am force-stop`) để tắt từng thứ: kính thật, kính mờ tĩnh, widget. Không đổi gì ở bản phát hành.
- **Script đo** `tools/perf/measure-home.ps1` (PowerShell, chạy trên máy chủ dự án): `dumpsys gfxinfo ... reset` → kịch bản bằng `adb shell input swipe` (lướt trang N lần) / nhấn giữ vào edit mode / mở-đóng thư mục → đọc `gfxinfo`, in Janky %, p50 / p90 / p95 / p99, số khung; lặp xen kẽ các cấu hình trong cùng phiên, lặp cấu hình gốc ở cuối (bài học GL).
- **Sửa theo số đo** (Task 3), ví dụ: làm đúng việc comment H1 mô tả (mặt kính về tĩnh / kính mờ tĩnh trong lúc lướt hoặc kéo, kính thật trở lại khi đứng yên); ngân sách theo `GlassBudget`; bỏ bớt lớp offscreen.

## Câu hỏi cho chủ dự án

| # | Câu hỏi | Khuyến nghị | Trả lời |
|---|---|---|---|
| Q1 | Đo trên bản nào? | Thêm build type `perf` (`initWith(release)`, R8 + baseline profile như release, ký bằng khoá debug để cài được, `debuggable = false`); chỉ bản này đọc công tắc đo | Đồng ý |
| Q2 | Máy đo? | Oppo PKD130 (máy giật) + Vsmart Live 4; Samsung SM-A176B nếu còn (JH ghi p90 +5 ms so với bản View) | Oppo + Live 4 (SM-A176B là máy của anh dvha, chủ dự án không dùng) |
| Q3 | Nếu H1 đúng: trong lúc lướt / kéo, kính thật của dock và widget tạm về kính mờ tĩnh (PG3), đứng yên thì kính thật trở lại? | Có: đúng ý đã ghi từ trước, mắt khó thấy khi đang lướt | Quyết sau khi có số đo → **không cần** (kính trong ngưỡng Q4 trên Oppo và Live 4) |
| Q4 | Ngưỡng đạt cho lướt trang? | Như GL D10: so với cấu hình "không kính" cùng phiên, Janky tăng ≤ 2 điểm và p95 tăng ≤ 3 ms | Đồng ý |
| Q5 | K1: anh tự chạy danh sách (em soạn bước từng mục, anh ghi đạt / lỗi), hay chỉ chạy các mục anh chọn? | Chạy hết; mục nào lỗi thì em tách việc sửa | Đồng ý |
| Q6 | Thứ tự: G3 trước rồi K1, hay song song (K1 không cần code)? | Song song: anh chạy K1 trong lúc em làm công cụ đo | Đồng ý |

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `app/build.gradle.kts` (build type `perf`, theo Q1) | Sửa | 1 |
| `core/perf/PerfSwitches.kt` (đọc `debug.os26.perf`, chỉ bản `perf`) | Tạo | 1 |
| `.gitignore` (`tools/perf/out/`) | Sửa | 1 |
| `app/src/perf/java/.../di/ContactsModule.kt` (bản sao của release) | Tạo | 1 |
| `ui/home/HomeActivity.kt`, `ui/home/compose/HomeScreen.kt` (áp công tắc) | Sửa | 1 |
| `tools/perf/measure-home.ps1` | Tạo | 1 |
| Tuỳ kết quả: `HomeScreen.kt`, `DsGlass.kt`, `DsFrosted.kt`, `GlassBudget.kt` | Sửa | 3 |
| `docs/plans/PG-parity-gaps.md`, `docs/lessons/glass.md`, `docs/lessons/startup-performance.md` | Sửa | 5 |

## Global Constraints

- `ui → domain ← data`; không khóa config mới, không dependency mới, không chuỗi mới.
- Công tắc đo không có tác dụng ở `debug` / `release`.
- API ≥ 31 và dưới 31: không đổi giao diện lúc đứng yên (chỉ đổi trong lúc lướt / kéo nếu Q3 = có).
- Giữ CRLF; không viết `/*` trong comment Kotlin.

## Review Focus

1. Số đo đáng tin: cùng phiên, xen kẽ, lặp cấu hình gốc cuối phiên, ghi máy / bản build / nhiệt độ nếu đọc được.
2. Sửa xong: Home lúc đứng yên trông như trước (so ảnh chụp); kính trở lại đúng khi dừng lướt, không nháy.
3. Công tắc đo không lọt vào bản phát hành.

---

### Task 1: Công cụ đo
**Bắt buộc kiểm tay:** có (chạy thử script).
- [x] Step 1: Build type `perf` (Q1); `PerfSwitches` đọc `debug.os26.perf` (cờ: `noRealGlass`, `noFrosted`, `noWidgets`); `HomeScreen` áp cờ (không cung cấp `LocalDsBackdrop` / `LocalDsFrostedBackdrop`, ẩn widget).
- [x] Step 2: `tools/perf/measure-home.ps1`: tham số cấu hình / kịch bản / số lượt; in bảng kết quả; ghi CSV vào `tools/perf/out/` (không commit).
- [x] Step 3: Chủ dự án chạy thử một lượt trên Oppo, gửi kết quả.

### Task 2: Đo gốc và tìm nguyên nhân
**Bắt buộc kiểm tay:** có.
- [x] Step 1: Oppo, bản `perf`: lướt trang (trang nhiều widget và trang nhiều icon / thư mục) với 4 cấu hình: đủ, `noRealGlass`, `noRealGlass+noFrosted`, `noRealGlass+noFrosted+noWidgets`; xen kẽ, 3 lượt mỗi cấu hình.
- [x] Step 2: Như Step 1 trên Vsmart Live 4 (kính thật không chạy ở đó: so `noFrosted`).
- [x] Step 3: Claude đọc số, chốt nguyên nhân (H1–H5) và cách sửa, ghi vào plan; chủ dự án duyệt trước khi sửa.

### Task 3: Sửa theo số đo
**Bắt buộc kiểm tay:** có.
- [x] Step 1: Không sửa kính (số đo trong ngưỡng Q4). Sửa comment cũ trong `HomeScreen.kt` (ghi đã tắt kính thật khi lướt nhưng code chưa từng tắt, và số 87 % là của bản debug) cho khớp số đo.
- [x] Step 2: Không cần đo lại (không đổi code chạy).

### Task 4: K1 — danh sách kiểm trên máy
**Bắt buộc kiểm tay:** có (chủ dự án chạy).
- [ ] Step 1: JH2: kéo icon ra khỏi trang tạm (RF2); kéo bằng hai ngón (RF3).
- [ ] Step 2: JH4: vẫn là launcher mặc định sau khi cài bản Compose đè lên (RF1); thử lại hai hiện tượng từng gặp một lần: Home mở thẳng vào Thư viện sau khi bỏ qua Onboarding rồi bấm "Trải nghiệm"; pager đứng sai trang sau khi áp theme.
- [ ] Step 3: JH3d: widget có màn cấu hình riêng; thoát chế độ sửa khi bảng chọn widget đang mở (bảng chọn giờ là lớp phủ PG2).
- [ ] Step 4: JH3a: tổng huy hiệu trên ô thư mục, tắt huy hiệu, quyền danh bạ.
- [ ] Step 5: JC/JD: Vsmart Live 4 (Android 11) đi một vòng mọi màn; cỡ chữ lớn nhất + chữ đậm; TalkBack (Home, Thư viện, Cài đặt); xoay màn hình.
- [ ] Step 6: Ghi kết quả từng mục vào bảng "Kết quả K1"; lỗi thì thêm Task sửa (hoặc plan riêng nếu lớn).

### Task 5: Docs
- [ ] Step 1: Tick G3, K1 ở `PG-parity-gaps.md`; số đo và bài học vào `lessons/glass.md` / `startup-performance.md`; `README.md` số mục.

---

## Kết quả đo (điền ở Task 2, 3)
| Máy | Bản | Kịch bản | Cấu hình | Lượt | Janky % | p50 | p90 | p95 | p99 | Ghi chú |
|---|---|---|---|---|---|---|---|---|---|---|
| Oppo PKD130 | perf | swipe, 2 trang, 5 lượt đi-về | full | 1 | 0,70 | 8 | 11 | 14 | 20 | thử; vướng hộp launcher mặc định; 32,9 °C |
| Oppo PKD130 | perf | như trên | noRealGlass | 1 | 0,43 | 9 | 11 | 12 | 17 | 33,5 °C |
| Oppo PKD130 | perf | như trên | noRealGlass+noFrosted | 1 | 0,41 | 8 | 10 | 12 | 16 | 33,1 °C |
| Oppo PKD130 | perf | như trên | noRealGlass+noFrosted+noWidgets | 1 | 0,40 | 8 | 10 | 12 | 16 | 33,6 °C |
| Oppo PKD130 | perf | như trên | full (lặp cuối) | cuối | 1,05 | 8 | 11 | 13 | 19 | 33,6 °C |
| Oppo PKD130 | perf | swipe 250 ms, 2 trang, 5 lượt đi-về | full | TB 3 vòng | 0,93 | 8 | 11 | 13 | 19,7 | lặp cuối 0,93 %; 32,8–33,9 °C |
| Oppo PKD130 | perf | như trên | noRealGlass | TB 3 vòng | 0,56 | 8 | 11 | 12 | 16,7 | |
| Oppo PKD130 | perf | như trên | noRealGlass+noFrosted | TB 3 vòng | 0,46 | 8 | 10 | 11,7 | 15,3 | |
| Oppo PKD130 | perf | như trên | noRealGlass+noFrosted+noWidgets | TB 3 vòng | 0,55 | 8 | 10 | 12 | 15,7 | |
| Oppo PKD130 | perf | swipe 120 ms, 2 trang | full | TB 3 vòng | 1,27 | 8 | 10 | 12,3 | 19,7 | lặp cuối 1,27 %; 33,6–34,4 °C |
| Oppo PKD130 | perf | như trên | noRealGlass | TB 3 vòng | 0,73 | 8 | 10 | 11 | 17,3 | |
| Oppo PKD130 | perf | như trên | noRealGlass+noFrosted | TB 3 vòng | 0,46 | 7,7 | 9 | 10 | 16,7 | |
| Oppo PKD130 | perf | như trên | noRealGlass+noFrosted+noWidgets | TB 3 vòng | 0,76 | 8 | 9 | 10 | 17,7 | |
| Oppo PKD130 | **debug** | swipe 250 ms, 2 trang | full (công tắc không có tác dụng) | TB 3 vòng | **36** | **26** | 65 | **106,3** | **183,3** | lặp cuối 33,3 %; 34,3–35 °C |
| Vsmart Live 4 | perf | swipe 250 ms, 2 trang | noFrosted+noWidgets (do lỗi tách `-Configs`) | TB 3 vòng | 1,52 | 6 | 9–10 | 10 | 19,7 | lặp cuối 1,13 %; 33,3–33,7 °C |
| Vsmart Live 4 | perf | swipe 250 ms, 2 trang | full | TB 3 vòng | 1,22 | 6 | 8 | 9 | 18 | ~600 khung; lặp cuối 1,16 %; 33,4–33,6 °C |
| Vsmart Live 4 | perf | như trên | noFrosted | TB 3 vòng | 1,32 | 6 | 9 | 10 | 18,7 | ~525 khung |
| Vsmart Live 4 | perf | như trên | noFrosted+noWidgets | TB 3 vòng | 1,33 | 6 | 9 | 10 | 18,3 | |

## Kết quả K1 (điền ở Task 4)
| Mục | Máy | Kết quả | Ghi chú |
|---|---|---|---|

## Bài kiểm cả tính năng (Claude gộp ở bước 5)
_(điền khi xong)_
**Kết quả kiểm chứng (máy, ngày):**

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú |
|---|---|---|---|
| 2026-11-23 | — | Đọc G3, K1, `HomeScreen` (comment "tắt kính thật khi cuộn" có từ `2615d5d` nhưng code chưa từng tắt: H1), `GlassBudget` (không nơi gọi), ngân sách của Today / Thư viện, `drawPlainBackdrop` (lớp offscreen); viết plan | Chờ duyệt Q1–Q6 |
| 2026-11-23 | — | Chủ dự án trả lời: Q1 đồng ý; Q2 chỉ Oppo + Live 4; Q3 quyết sau; Q4, Q5 đồng ý; Q6 K1 chạy song song | Bắt đầu Task 1 |
| 2026-11-23 | 1 | `app/build.gradle.kts`: build type `perf` (`initWith(release)`, `optimization.enable`, ký khoá debug, không debuggable, `matchingFallbacks`), `BuildConfig.PERF_SWITCHES` (false mặc định, true ở `perf`). `core/perf/PerfSwitches.kt`: đọc `debug.os26.perf` bằng lệnh `getprop` (không API ẩn), một lần; cờ `noRealGlass`, `noFrosted`, `noWidgets` (nối bằng `+`). `HomeScreen`: `noWidgets` lọc `PlacedWidgetBase` khỏi trang vẽ; hai khối `CompositionLocalProvider` cung cấp `glassBackdrop` / `frostedBackdrop` theo cờ; bảng thư mục bỏ nền mờ khi `noFrosted` (kính thật của bảng KHÔNG theo `noRealGlass`: nền truyền thẳng). `HomeActivity`: ảnh kéo theo `noFrosted`. `tools/perf/measure-home.ps1` (UTF-8 BOM cho PowerShell 5.1): kịch bản swipe / jiggle / folder / idle, xen kẽ cấu hình × vòng + lặp cấu hình đầu cuối phiên, cảnh báo nếu app đang cài là bản debug, in bảng + trung bình, CSV vào `tools/perf/out/` (gitignore); ghi model, API, nhiệt độ pin | Chưa build |
| 2026-11-23 | 1 | Build lỗi `hiltJavaCompilePerf`: `ContactsRepository` `MissingBinding` (module gắn nằm ở `src/release` / `src/debug`, `initWith(release)` không chép thư mục nguồn). Chủ dự án chọn chép: `src/perf/.../di/ContactsModule.kt` (bản sao của release, ghi chú giữ khớp; release ghi chú ngược lại) | Chờ build lại |
| 2026-11-23 | 1 | Chủ dự án build `perf` đạt, chạy thử `swipe -Pages 2 -Rounds 1` trên Oppo: Janky 0,4–1,05 %, p95 12–14 ms (bảng dưới). Số bị nhiễu: hộp "đặt làm launcher mặc định" (PB) hiện mỗi lần mở Home sau `force-stop`. Script thêm `-SetHome` (mặc định bật): `cmd package set-home-activity` trước khi đo | Chờ chạy lại |
| 2026-11-23 | 2 | Oppo, 3 vòng xen kẽ + lặp cuối (bảng dưới). `perf`: đủ kính Janky 0,93 % / 1,27 % (vuốt 250 / 120 ms), p95 13 / 12,3 ms; bỏ kính thật + kính mờ: 0,46 % / 0,46 %, p95 11,7 / 10 ms → kính tốn Janky +0,5–0,8 điểm, p95 +1,3–2,3 ms: **trong ngưỡng Q4**; kính mờ tĩnh và widget gần như không tốn. Bản `debug` cùng kịch bản: Janky 36 %, p50 26 ms, p95 106 ms, p99 183 ms. **Kết luận Oppo: giật chủ dự án thấy là do bản debug (H4), không phải kính (H1–H3)** | Chờ đo Live 4 |
| 2026-11-23 | 2 | Live 4 lượt 1 hỏng cấu hình: `powershell -File ... -Configs a,b,c` truyền một chuỗi "a,b,c" nên cả 4 lượt chạy cùng cờ `noFrosted+noWidgets` (Janky 1,52 %, p50 6, p95 10, p99 19,7 ms). Script tự tách `-Configs` theo dấu phẩy | Chờ đo lại |
| 2026-11-23 | 2, 3 | Live 4, 3 vòng xen kẽ + lặp cuối: full Janky 1,22 %, p95 9, p99 18 ms; `noFrosted` 1,32 %, p95 10, p99 18,7; `noFrosted+noWidgets` 1,33 %, p95 10, p99 18,3 → kính mờ tĩnh (gồm dock, widget, card dưới API 31 của PG3) không tốn đo được (full còn hơi tốt hơn, trong nhiễu; full ~600 khung so với ~525: kính mờ có vẽ thêm khung, chưa rõ chỗ nào, không gây giật). **Chốt nguyên nhân G3: H4 (bản debug)**; H1–H3 trong ngưỡng; Q3 không cần. Task 3: chỉ sửa comment `HomeScreen.kt`. Bài học vào `lessons/startup-performance.md`, quy ước đo vào `AGENTS.md` | Chờ chủ dự án xác nhận đóng G3 |

## Bằng chứng Review Focus (điền khi chốt)
| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|

## Tài liệu chung đã cập nhật (điền ở bước 7)
