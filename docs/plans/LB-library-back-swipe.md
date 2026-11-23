# LB — Vuốt ngược từ App Library về các trang Home — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md` — code từng Task, báo cáo điều phối, qua review mới tick và sang Task sau; Task "bắt buộc kiểm tay" thì chờ điều phối/chủ dự án kiểm; cả tính năng kiểm tay một lần ở cuối. Tối đa 5 vòng mỗi Task. Không có test tự động; kiểm chứng trên Vsmart Live 4. Lệnh viết cho CMD/PowerShell (phần chạy trên máy là `sh`).

**Nhánh:** `fix/lb-library-back-swipe` · **Worktree:** `E:\SourceTree\IosLauncher-wt\LB-library-back-swipe` · **PR:** #44 (draft) · **Bắt đầu:** 2026-11-12 (trên `main` 73bbad3) · **Plan duyệt:** 2026-11-12 (điều phối, theo ủy quyền của chủ dự án)
**Trạng thái:** Task 0-3 xong, chờ điều phối review (PR draft); chủ dự án thử tay ở bài kiểm cuối
**Khóa:** không (không đổi schema Room, không cần Oppo)

**Goal:** Ở App Library, vuốt phải một lần về trang Home cuối rồi vuốt phải tiếp 2+ lần thật nhanh (nhịp chủ dự án: vuốt 80-200 ms, cách 150-250 ms) thì **mỗi cú vuốt lùi đúng một trang**, không cú nào bị nuốt, như chiều thuận (trang 1 → Library) đã đạt ở FX7.
**Architecture:** Sửa một luật trong `LibraryOverlayController`: khi chạm xuống lúc spring **đóng** Library còn chạy (ứng viên `GRAB`, FX7 vòng 4) mà ngón vuốt **sang phải** (cùng chiều đóng) thì không bắt Library nữa, trả cử chỉ cho pager; spring đóng chạy nốt. Vuốt trái (mở lại) lúc spring đóng vẫn `GRAB` như FX7.
**Tech Stack:** không thêm thư viện.
**Spec:** báo lỗi của chủ dự án (2026-11-12, sau khi FX7 merge); `docs/report/FX7-home-gestures.md` Task 7d; `docs/knowledgements/FX7-home-gestures.md` mục 3.4; mục D2 của `docs/report/Audit-Report-2026-11-10.md`.

## Lỗi (chủ dự án, 2026-11-12)

Chiều thuận (trang 1 → … → trang cuối → App Library, dưới 2 giây) đã mượt sau FX7. Chiều ngược thì không: ở App Library vuốt phải một lần → trang cuối, rồi vuốt phải nhanh thêm 2+ lần → trang bên trái trang cuối (và xa hơn): chuyển cảnh "không mượt, giật". Cần xác định là mất cú vuốt, trễ cú vuốt, căn sai trang, hay khung hình bị khựng.

## Baseline đo được

**Máy và bản:** Vsmart Live 4 (BGB00153427, Android 11, 1080x2340), launcher mặc định, kính Ép cao, bản **debug** đang cài = `main` đã merge (md5 `eb053b8b35b88c9bc1f2159e0be0fdcb`, cài 2026-11-12 09:49), dữ liệu thật của chủ dự án: **4 trang Home** + App Library. Đo 2026-11-12 chiều, agent LB. Không cài gì, không đổi cài đặt.

**Công cụ (đo được):**
- Bơm cử chỉ bằng `E:\SourceTree\IosLauncher-wt\tools\Swiper.java` (`/data/local/tmp/sw.dex`, giờ sự kiện `down + k·8 ms`, ASYNC). Vuốt ngang 750 px ở `y = 1300`: vuốt phải `150 → 900`, vuốt trái `900 → 150`. `D` = thời lượng một cú vuốt, `G` = nghỉ sau `UP` tới `DOWN` kế.
- Trạng thái cuối đọc bằng `dumpsys activity top`: trang đang xem = `IconGridView` ở `left = 0` trong pager, nhận dạng bằng chữ ký bố cục ô (4 trang khác nhau: 18, 24, 16, 5 ô); Library mở hay đóng = cờ hiển thị của `library_view` (`V`/`G`). Đọc 1,6 s sau cú vuốt cuối.
- Khung hình: `dumpsys gfxinfo gambi.global.os26launcher reset` ngay trước chuỗi vuốt, `framestats` ngay sau (khử trùng theo `IntendedVsync`, `FrameCompleted − IntendedVsync`).
- Script và log (không commit): `C:\Users\admin\AppData\Local\Temp\claude\c961cbf225c5fe34\b47273c5-8a01-475e-b6a4-88afc916d975\scratchpad\lb\` (`run.py` điều hướng + đọc trạng thái, `matrix.py` các ca B1-B7, `base.jsonl` kết quả từng lượt, `rec.py` + `motion.py` quay màn hình và độ dời từng khung, `lb_k3.mp4`).

**Kết quả (đo được):**

| # | Ca | Chuỗi | Kỳ vọng | Kết quả `main` |
|---|---|---|---|---|
| B1 | **Chiều ngược, nhịp chủ dự án** | Library, 4 × vuốt phải, `D` ∈ {80, 120, 200} × `G` ∈ {150, 250} × 3 lần | trang 1, Library đóng | **3/18**. Cả 15 lần hỏng đều dừng ở **trang 2** (mất đúng một cú vuốt); 3 lần đạt đều là `D = 80, G = 250` |
| B2 | Chỉ hai cú | Library, 2 × vuốt phải `120/200` | trang 3 | **0/6**, cả 6 lần dừng ở **trang 4** (trang cuối): cú vuốt bị mất là **cú thứ hai** |
| B3 | Quét khoảng nghỉ sau cú đóng | Library, vuốt phải `120` rồi nghỉ `G1`, tiếp 3 × `120/200` | trang 1 | `G1 = 150`: 0/3 · `250`: 0/3 · `350`: 3/3 · `450`: 3/3 · `600`: 3/3 |
| B4 | Đối chứng: chỉ pager, cùng nhịp | trang 4, 3 × vuốt phải `120/200` | trang 1 | 6/6 |
| B5 | Chiều thuận (hồi quy FX7) | trang 1, 4 × vuốt trái `120/200` | Library mở | 6/6 |
| B6 | Đóng rồi mở lại ngay (GRAB ngược chiều, FX7 7d) | Library, vuốt phải `120/150`, vuốt trái `120` | Library mở | 6/6 |
| B7 | Chiều ngược chậm | Library, 4 × vuốt phải `400/700` | trang 1 | 3/3 |

**Khung hình (đo được, B1, 18 lượt):** 1675 khung, Janky 42 (2,5 %; từng lượt 0-6,1 %), p90 8-15 ms, p99 15-23 ms, **0 khung > 33 ms**, dài nhất 28,5 ms. Cả 60 lượt đo (B1-B7): 0 khung > 33 ms, dài nhất 33,3 ms. Mọi trạng thái cuối đều thẳng lưới (`IconGridView` ở `left = 0`).

**Quay màn hình (đo được, một lượt 3 × vuốt phải `120/200` từ Library, `screenrecord` 540x1170, độ dời dải trang theo từng khung):** cú 1 (khung 38-63 ở 60 khung/s): Library trượt ra và Home trượt vào; cú 2 (bắt đầu ~khung 57): **không có chuyển động nào** sau khi spring đóng tắt (khung 64-76 đứng yên trong khi ngón đang vuốt); cú 3 (khung 77-102): trang lùi. Kết thúc trang 3 thay vì trang 2.

**Kết luận đo:** lỗi là **mất cú vuốt thứ hai** (cú đầu tiên sau cú đóng Library), không phải căn sai trang hay khung hình khựng. Cú bị mất phụ thuộc đúng vào việc `DOWN` của nó rơi vào lúc spring đóng Library còn chạy (B3: hỏng khi nghỉ ≤ 250 ms, đạt khi ≥ 350 ms); các cú sau đó chạy đúng (luôn chỉ mất một cú, B1). Pager một mình ở cùng nhịp không mất cú nào (B4). Ở nhịp chủ dự án, ngón tay "vuốt mà trang không đi", rồi cú kế tiếp mới đi: đó là cảm giác "giật".

## Nguyên nhân gốc (đọc code, khớp số đo)

Nhãn: chuỗi dưới đây là **đọc code**; nó khớp mọi số đo ở trên (cú bị mất là cú 2, chỉ một cú, phụ thuộc khoảng nghỉ đúng như thời gian spring tính được) nhưng **chưa có log trong app xác nhận trực tiếp** (pha này không được cài bản có log). Nếu điều phối muốn chắc trước khi code: dựng bản `main` + log tạm `LBTMP` (dòng `DOWN cand settling settleOpen p`, `TAKEOVER cand p dx`) và chạy lại B2; kỳ vọng thấy `TAKEOVER cand=GRAB` ở cú 2.

Đường đi của cú vuốt thứ hai (đường dẫn từ `app/src/main/java/gambi/global/os26launcher/`):

1. Cú 1 (đóng): `ACTION_UP` → `animateTo(open = false, startVelocity)` (`ui/home/LibraryOverlayController.kt:185`, `:229-236`), `settling = true` tới khi spring dừng hẳn (`:65-77`).
2. Cú 2 chạm xuống lúc `settling`: `ACTION_DOWN` chọn ứng viên **`GRAB`** chỉ cần `canClose()` (`LibraryOverlayController.kt:91-96`, luật của FX7 vòng 4, commit `951d96d`). Library trả `PASS`, nên pager vẫn nhận `DOWN`.
3. `ACTION_MOVE` vượt slop sang phải: `GRAB` nhận **cả hai chiều** (`:128-132`, `else -> true`); chiều phải chỉ kiểm `canClose()` (`:135`), vẫn đúng. Library tiếp quản: `spring.cancel()`, `baseProgress = progress` (lúc này chỉ còn vài phần trăm), `setProgress(baseProgress - dx / width)` kẹp về `0` (`:144-157`), trả `TAKEOVER`.
4. `HomeTouchRouter` gửi `ACTION_CANCEL` giả xuống cây view (`ui/home/controller/HomeTouchRouter.kt:73-80`): **pager mất cú vuốt**, ngón tay kéo một Library đã gần như đóng (p kẹp ở 0, không thấy gì chuyển động).
5. `ACTION_UP`: `animateTo(false)` từ `p = 0` (`LibraryOverlayController.kt:185`) → `SpringProgress.animateTo` thấy đã ở đích nên báo xong ngay, không chạy spring (`core/ui/SpringProgress.kt:97-103`): `settling` về `false`, nên cú 3 trở đi vào pager bình thường. Khớp B1 "luôn mất đúng một cú".

**Cửa sổ nuốt cú vuốt = thời gian spring đóng còn chạy** (suy từ thông số, khớp số đo): `SWIPE_SETTLE_SPRING` = độ cứng 600, giảm chấn 1 (`ui/home/view/AppLibraryMotion.kt:65`), dừng khi `|p| < 0,0015` và vận tốc < 0,094/s (`minVisibleChange` mặc định 1/500 của DynamicAnimation, ngưỡng giá trị × 0,75, ngưỡng vận tốc × 62,5). Giải tới hạn với `p` và vận tốc lúc thả của cú 750 px:

| Cú đóng | `p` lúc thả | Vận tốc thả | Spring dừng sau `UP` | `p < 0,02` (≤ 22 px) sau |
|---|---|---|---|---|
| 80 ms, 750 px | 0,31 | 8,7 p/s | ~170 ms | ~95 ms |
| 120 ms, 750 px | 0,31 | 5,8 p/s | ~255 ms | ~135 ms |
| 200 ms, 750 px | 0,31 | 3,5 p/s | ~280 ms | ~155 ms |
| 120 ms, 450 px (vuốt ngắn hơn) | 0,58 | 3,5 p/s | ~325 ms | ~200 ms |

Khớp B1 (chỉ `80/250` đạt: 250 > 170; `120/250` và `200/250` hỏng: 250 < 255-280) và B3 (`G1 = 350` trở lên đạt). Với ngón thật vuốt ngắn hơn 750 px, cửa sổ còn dài hơn (~320 ms) trong khi Library đã gần như ra khỏi màn hình từ ~200 ms: người dùng thấy Home đã về chỗ mà cú vuốt vẫn bị Library bắt.

Vì sao FX7 không thấy: kịch bản 7d đo **xen kẽ trái/phải** (đóng rồi mở lại), tức `GRAB` ngược chiều spring, đúng là ca cần bắt. Ca **cùng chiều** (đóng rồi vuốt phải tiếp) không có trong ma trận FX7.

Đã loại (đo được): khung hình khựng (B1: 0 khung > 33 ms trong 1675 khung, dài nhất 28,5 ms), căn sai trang hay bật ngược (mọi lần hỏng đều dừng đúng lưới ở trang 2, không lệch giữa hai trang; đối chứng pager B4 không mất cú nào), lỗi bộ bơm (B4 và B5 cùng công cụ, cùng nhịp đều đạt).

## Vì sao chọn thiết kế này

**Vấn đề cần giải:** `GRAB` (FX7 vòng 4) phải giữ cho ca đảo chiều (đóng rồi mở lại, đóng giữa lúc mở): FX7 đo 18/18 nhờ nó. Nhưng khi spring đang **đóng** và ngón vuốt **tiếp sang phải**, ý định là lùi trang, không phải kéo Library. Library là lớp phủ ngoài pager (G2a D17, bài học `lessons/home-pager-animation.md`: không đưa Library vào pager), nên phải chọn một trong hai lớp nhận cử chỉ ngay lúc vượt slop. Ràng buộc: không đổi thứ tự nhận chạm của `HomeTouchRouter`; `p` vẫn là nguồn duy nhất của vị trí Library/Home; không đụng `HomeActivity.kt` (nhánh RR đang giữ); không thêm `setCurrentItem` lúc pager không rảnh (B6).

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A. `GRAB` không nhận chiều đang đóng** | Ở `MOVE` vượt slop: ứng viên `GRAB`, spring đang về đóng (`settleOpen == false`) và `dx > 0` → `cancelCandidate()`, trả `PASS`. Pager đã có `DOWN` và các `MOVE` trước slop nên tự kéo; spring đóng chạy nốt | ~5 dòng trong một file; không đụng router, pager, `HomeActivity`; ca đảo chiều của FX7 giữ nguyên; spring không bị cắt nên không có bước nhảy | Trong ~100-250 ms đầu của cú 2, `home_content` còn dịch trái vài chục px và đang trượt về 0 trong khi pager cũng kéo: trang đi nhanh hơn ngón một chút (cùng chiều, không giật ngược). `RecyclerView` đọc toạ độ đã trừ `translationX` của cha, nên vận tốc pager thấy thấp hơn ngón chừng 5-20 % (suy luận từ thông số: tốc độ spring lúc đó ~340-1400 px/s so với ngón ~6000-9000 px/s), vẫn xa trên ngưỡng fling | **Chọn** |
| B. Như A nhưng chốt Library đóng ngay | `spring.cancel()` + `p = 0` + `onSettled(false)` rồi trả `PASS` | Home thẳng hàng ngay khi pager bắt đầu kéo; `libraryOpen = false` sớm | Nhảy một khung bằng phần còn lại của spring (~16 px ở 150 ms, ~70 px ở 80 ms sau `UP`): chính là "giật" cần tránh; `onSettled` → `refreshHomeGlass` + phát `uiState` đúng khung pager bắt đầu kéo | Không |
| C. Chuyển phần kéo còn lại sang pager | Giữ `GRAB`, khi `p` chạm 0 thì chuyển phần `dx` dư cho pager bằng `fakeDrag` | Liền mạch nhất về lý thuyết (giống một scroll view của iOS) | `fakeDrag` song song với chạm thật, tự tính fling, đụng `PagerFlipAnimator` và quy tắc B6; nhiều mã, nhiều ca biên; lớn hơn nhiều so với lỗi | Không |
| D. Thu ngắn cửa sổ spring | Spring cứng hơn hoặc ngưỡng dừng lớn hơn | Không đổi luật chạm | Chỉ thu hẹp cửa sổ (vẫn mất cú khi nghỉ ngắn); đổi cảm giác Library đã được chủ dự án chấp nhận ở FX7 | Không |
| E. `GRAB` chiều phải chỉ khi `p` còn lớn | Như A nhưng chỉ trả cho pager khi `p` < ngưỡng | Vuốt phải rất sớm (Library còn thấy rõ) vẫn kéo Library | Ngưỡng là hằng chỉnh tay; vùng trên ngưỡng vẫn nuốt cú vuốt (p kẹp 0, phần dư mất), tức vẫn là lỗi này ở nhịp nhanh hơn | Không |

**Vì sao chọn A:** số đo cho thấy toàn bộ lỗi nằm ở một nhánh (`GRAB` cùng chiều đóng) và cú vuốt bị nuốt là cú pager lẽ ra nhận được (đo được: B1, B3, B4); A trả đúng cú đó về pager mà không cắt spring (không có bước nhảy, khác B), không đụng các ca FX7 đã nghiệm thu 18/18 (xen kẽ, chiều thuận), và nằm trong một file không phải file nóng. Bài học FX7 7c (`lessons/home-pager-animation.md`): đừng giả lập fling cho pager (C) khi pager tự xử lý được cú vuốt thật.

**Đánh đổi chấp nhận:** ở cú vuốt thứ hai rất sớm (< ~150 ms sau `UP` của cú đóng) trang đi nhanh hơn ngón tay một chút do Home còn trượt nốt; `libraryOpen` về `false` khi spring dừng (giữa lúc pager kéo, `settlePageSelection` của B6 tự hoãn tới `IDLE`). Xét lại nếu Task 2 thấy khung > 33 ms ở đúng lúc spring dừng (nghi `refreshHomeGlass` chạy giữa lúc pager kéo), hoặc chủ dự án thấy trang "vọt" ở cú thứ hai.

## Quyết định đã chốt

| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |
|---|---|---|
| 1 | Vuốt phải lúc spring đóng Library còn chạy = lùi trang (pager), không kéo Library | Agent đề xuất; **chủ dự án chốt 2026-11-12 (Q1: có)**: vuốt phải nhanh lúc Library đang đóng luôn lùi đúng một trang. Cơ sở: B1/B3 |
| 2 | Vuốt trái lúc spring đóng còn chạy (mở lại) và mọi chiều lúc spring **mở** còn chạy: giữ `GRAB` như FX7 | Agent đề xuất, điều phối duyệt 2026-11-12; giữ hành vi đã nghiệm thu 18/18 (FX7 7d). Spring mở + vuốt trái: bên phải Library không còn gì, bắt Library là vô hại |
| 3 | Tiêu chí đạt: B1 18/18 và không hồi quy (Task 2) | Agent đề xuất, điều phối duyệt 2026-11-12; cùng mức FX7 |
| 4 | Trước khi sửa, dựng bản `main` + log tạm `LBTMP` để xác nhận `TAKEOVER cand=GRAB` ở cú 2 (Task 0) | Điều phối chốt 2026-11-12 |
| 5 | Agent được tự cài bản debug của nhánh lên Live 4 để đo, giữ khóa `E:\SourceTree\IosLauncher-wt\live4.lock` (một phiên cài + đo liên tục; kiểm md5 APK đã cài trước khi đo; `install -r`, không gỡ, không xóa dữ liệu; kết thúc ở trang 1, Library đóng) | Điều phối chốt 2026-11-12 (máy dùng chung với nhánh RR) |

**Câu hỏi cho chủ dự án (Q1):** quyết định 1 có đúng ý không: vuốt phải nhanh liên tiếp từ Library luôn lùi trang, kể cả khi Library chưa trượt ra hết? **Trả lời (chủ dự án, 2026-11-12): CÓ.**

## Ngoài phạm vi / ghi để sau

- Chiều thuận và ca xen kẽ trái/phải: đã đạt ở FX7, chỉ chạy lại làm hồi quy.
- Hiệu ứng khi vuốt trang thường (khung hình, `PageMotion`): không đổi.
- Phần kính khi Home về chỗ (`refreshHomeGlass`): chỉ đo; nếu Task 2 thấy khung dài thì báo điều phối, mở việc riêng.
- Xoay màn hình (nhánh RR): không liên quan, không đụng `HomeActivity.kt`, Manifest, layout.

## Vùng file

| File (từ `app/src/main/java/gambi/global/os26launcher/`) | Tạo / Sửa | Nóng |
|---|---|---|
| `ui/home/LibraryOverlayController.kt` | Sửa: nhánh `ACTION_MOVE` vượt slop (`:123-158`), chỗ quyết `GRAB`; KDoc lớp (`:18-19`) và chú thích `Candidate.GRAB` (`:52`) | Không (không nhánh nào khác giữ) |
| `docs/plans/LB-library-back-swipe.md` | Tạo (plan này), sửa Nhật ký | Không |
| `docs/knowledgements/LB-library-back-swipe.md` | Tạo (Task 3) | Không |

**Không đụng:** `ui/home/HomeActivity.kt` (**không cần**: nhánh RR giữ file này để thêm `onConfigurationChanged`), `ui/home/controller/HomeTouchRouter.kt`, `ui/home/controller/AppLibraryController.kt`, `ui/home/controller/HomePagerController.kt`, `core/ui/SpringProgress.kt`, `ui/home/view/AppLibraryMotion.kt`, `AndroidManifest.xml`, `res/*`, `core/glass/*`. Log tạm để đo (nếu điều phối cần) nằm trong patch ngoài repo, nhãn `LBTMP`, không commit. Cần file ngoài vùng thì dừng và báo.

## Global Constraints

- Không code trước khi plan được duyệt. Build `gradlew.bat assembleDebug` xanh trong worktree trước khi báo xong Task; commit tiếng Anh không dấu (`fix:`); stage theo đường dẫn (không `git add docs`, không `-A`); không merge `main` vào nhánh.
- Không thêm test, không thêm thư viện. Agent cài và đo trên Live 4 theo quyết định 5 (khóa `live4.lock`, kiểm md5 APK trước khi đo, `install -r`, không gỡ, không xóa dữ liệu).
- Giữ thứ tự nhận chạm của `HomeTouchRouter` (DOWN → Library → tìm kiếm → cây view) và **một** `ACTION_CANCEL` giả khi Library tiếp quản. Nhánh mới chỉ trả `PASS` (không tiếp quản), nên pager nhận chuỗi chạm thật liền mạch từ `DOWN`.
- `p` vẫn là nguồn duy nhất của vị trí Library/Home; không cắt spring đóng ở nhánh mới (khác phương án B).
- Đo bằng `Swiper` (giờ phần cứng, ASYNC), không dùng `adb shell input swipe` cho ca nhanh. Luôn ghi bản nào (debug), md5 hoặc commit của APK.

## Review Focus

1. Nhánh mới chỉ áp khi **cả ba**: ứng viên là `GRAB`, spring đang chạy về **đóng** (`settling && !settleOpen`), và `dx > 0` (vuốt phải). Không đổi thứ tự kiểm: nhánh "spring đã tự dừng → `CLOSE`/`OPEN` thường" (`:124-127`) vẫn đứng trước.
2. Nhánh mới chỉ `cancelCandidate()` + trả `PASS`: không `spring.cancel()`, không `setProgress`, không `onSettled`; spring đóng chạy nốt và `onSettled(false)` gọi đúng một lần.
3. `GRAB` vuốt trái lúc spring đóng (mở lại) và `GRAB` mọi chiều lúc spring mở: hành vi y như trước (diff không chạm các nhánh đó).
4. `CLOSE` thường (Library đứng yên ở trạng thái mở) và `OPEN` thường: không đổi.
5. Chạm nhẹ hay vuốt dọc lúc spring chạy: không đổi (Library về đích cũ, không kẹt).
6. Không file nào ngoài vùng; không còn `LBTMP`.

---

### Task 0: Xác nhận nguyên nhân bằng log tạm (trên `main`, chưa sửa)

**Bắt buộc kiểm tay:** **có** — phép đo xác nhận giả thuyết trước khi sửa (mục 4.2).
**Files:** chỉ log tạm `LBTMP` (tag `LBD`) trong `ui/home/LibraryOverlayController.kt`, không commit; patch ngoài repo.

- [x] **Step 1:** Log `DOWN` (`cand`, `settling`, `settleOpen`, `p`), `TAKEOVER` (`cand` trước khi xóa, `p`, `dx`), `CANCEL_CAND` (lý do), `UP` (`p`, `vx`, `open`), `SETTLED`. Build, ghi md5.
- [x] **Step 2 (Live 4):** cài, kiểm md5, chạy B2 (6 lượt) và B1 rút gọn (`120/150`, `120/250`, `80/250` × 2), `logcat -s LBD`. Kỳ vọng: cú 2 có `DOWN cand=GRAB settling=true settleOpen=false`, rồi `TAKEOVER cand=GRAB` với `dx > 0`, `p` nhỏ; lượt `80/250` đạt thì `DOWN` cú 2 có `settling=false`.
- [x] **Step 3:** Ghi kết quả vào Nhật ký; gỡ log (`git diff` rỗng ở `app/`).

**Kết quả Task 0 (đo được, Live 4, 2026-11-12 ~14:20, `LB-t0-log.apk` = `main` 73bbad3 + log tạm, md5 `a0ee678f…` khớp bản đã cài; patch `scratchpad\lb\lb-logs.patch`, log `scratchpad\lb\t0.jsonl`): giả thuyết XÁC NHẬN.**
- B2 (6 lượt, `120/200`): 6/6 dừng ở trang 4 (như baseline). Mọi lượt: cú 1 `TAKEOVER cand=CLOSE`, `UP p=0.306 vx=6250`; cú 2 `DOWN cand=GRAB settling=true settleOpen=false p=0.005-0.007` (~205 ms sau `UP`), rồi `TAKEOVER cand=GRAB p=0.003-0.007 dx=62-175`, `UP p=0.0`, `SETTLED open=false` ngay sau đó (spring không chạy lại).
- B1 `120/150` và `120/250` (2 + 2 lượt): 4/4 dừng ở trang 2; cú 2 `DOWN cand=GRAB settling=true` với `p = 0,020` (150 ms sau `UP`) và `p = 0,0023` (250 ms, tức 2,5 px trên màn), rồi `TAKEOVER cand=GRAB`. Cú 3 `DOWN cand=OPEN` (trang cuối, đứng yên) bị bỏ vì sai chiều (`CANCEL_CAND at=136`), cú 4 `cand=NONE`: hai cú này vào pager, đúng.
- B1 `80/250` (2 lượt): 2/2 đạt; `SETTLED` 191 và 196 ms sau `UP` của cú 1, trước `DOWN` cú 2 (253 ms), nên cú 2 là `cand=OPEN` → sai chiều → pager. Khớp bảng cửa sổ spring (~170 ms tính được).

### Task 1: `GRAB` không bắt cú vuốt phải khi spring đang đóng Library

**Bắt buộc kiểm tay:** **có** — cử chỉ chạm (mục 4.2 của `MultiAgent-Workflow.md`); điều phối đo bằng `Swiper` trên Live 4 (Task 2).
**Files:** `ui/home/LibraryOverlayController.kt`.
**Interfaces:** Consumes: `settling`, `settleOpen` (đã có, private). Produces: không đổi chữ ký công khai (`onTouch`, `Result`, `Host` giữ nguyên).

- [ ] **Step 1:** Trong `ACTION_MOVE`, nhánh vượt slop ngang (`:123`), ngay sau khối đổi `GRAB` → `CLOSE`/`OPEN` khi spring đã dừng (`:124-127`): nếu `candidate == Candidate.GRAB && settling && !settleOpen && dx > 0` thì `cancelCandidate()` và `return Result.PASS` (chú thích: spring đang đóng Library mà ngón vuốt tiếp sang phải = lùi trang; để pager nhận cú vuốt, spring đóng chạy nốt; LB, số đo B1/B3).
- [ ] **Step 2:** Sửa KDoc lớp (`:18-19`) và chú thích `Candidate.GRAB` (`:52`): `GRAB` bắt Library ở cả hai chiều, **trừ** vuốt phải khi spring đang đóng (chiều đó thuộc pager).
- [ ] **Step 3:** Build `gradlew.bat assembleDebug` xanh. Commit `fix: let a right swipe during the Library close spring reach the pager`. Dựng `apk/LB-r1.apk` (sạch) ra `E:\SourceTree\IosLauncher-wt\apk\` cho điều phối.
- [ ] **Step 4: Kịch bản kiểm (Live 4)** — gộp vào Task 2.

### Task 2: Đo sau sửa (nghiệm thu + hồi quy)

**Bắt buộc kiểm tay:** **có** — phép đo cử chỉ quyết đạt/không đạt (mục 4.2). Agent chạy theo quyết định 5 (khóa `live4.lock`).
**Files:** không (chỉ đo; script ở scratchpad).

Cùng công cụ, cùng nhịp, cùng dữ liệu như Baseline; xen kẽ `LB-r1.apk` với bản `main` nếu cần so lại trong cùng phiên.

- [ ] **Step 1: Nghiệm thu.**
  - (N1) B1: Library, 4 × vuốt phải, `D` ∈ {80, 120, 200} × `G` ∈ {150, 250} × 3 lần: **18/18** dừng ở trang 1, Library đóng (`main`: 3/18).
  - (N2) B2: 2 × vuốt phải `120/200` từ Library: 6/6 dừng ở trang 3.
  - (N3) B3: quét `G1` ∈ {150, 250, 350, 450, 600} × 3: 15/15.
  - (N4) Khung hình của N1: không khung nào > 50 ms; số khung > 33 ms và Janky % không tệ hơn baseline quá nhiễu (baseline B1: 0 khung > 33 ms trong 1675 khung, dài nhất 28,5 ms). Có khung dài trùng lúc spring dừng thì ghi lại và báo (xem "Đánh đổi").
- [ ] **Step 2: Hồi quy.**
  - (R1) Chiều thuận FX7: trang 1 → Library 4 × vuốt trái, `D` ∈ {60, 80, 120} × `G` ∈ {150, 250} × 3: **18/18** Library mở.
  - (R2) Xen kẽ FX7 7d: từ trang cuối trái/phải xen kẽ, cách 150/250/400 ms, `D` 60/80/120: 18/18 mọi cú đổi trạng thái; riêng B6 (đóng rồi mở lại sau 150 ms) 6/6 Library mở.
  - (R3) Mở/đóng chậm: 4 × vuốt phải `400/700` từ Library → trang 1 (3/3); trang cuối → Library vuốt trái 400 ms → mở; kéo nửa chừng rồi thả chậm → về đúng phía gần hơn.
  - (R4) Nút Home khi Library mở: Library đóng (hoạt ảnh), Home ở trang cuối; bấm Home rồi vuốt phải ngay trong lúc Library còn trượt ra: lùi một trang.
  - (R5) Chạm nhẹ lúc Library đang trượt (mở và đóng): Library tới đích cũ, không kẹt giữa chừng.
  - (R6) Vuốt phải ở trang cuối khi Library đóng và đứng yên: lùi trang như cũ; kéo icon ở trang cuối (edit mode) không bị Library cướp (TL1 Task 9).
- [ ] **Step 3:** Điền bảng "trước/sau" vào Nhật ký; chưa đạt thì quay lại Task 1 (tính vòng).

**Kết quả Task 2 (đo được, Live 4, 2026-11-12 ~14:30-15:30, một phiên giữ khóa `live4.lock`):** `LB-r1.apk` (nhánh `390f384`, sạch, md5 `a1741b7b…` khớp bản đã cài) rồi, cùng phiên, `main-36f4630.apk` (= bản `main` đã cài trước đó, md5 `eb053b8b…`) cho phần khung hình của hồi quy. Kết quả từng lượt: `scratchpad\lb\fix.jsonl`, `scratchpad\lb\main2.jsonl` (baseline B1-B7: `base.jsonl`). "Thẳng lưới" = mọi trạng thái cuối có `IconGridView` ở `left = 0`.

| Mục | Ca | `main` | `LB-r1` | Đạt? |
|---|---|---|---|---|
| N1 | B1: Library, 4 × vuốt phải, `D` 80/120/200 × `G` 150/250 × 3 | 3/18 | **18/18** (trang 1, Library đóng, thẳng lưới) | Đạt |
| N2 | B2: 2 × `120/200` | 0/6 (kẹt trang 4) | **6/6** (trang 3) | Đạt |
| N3 | B3: `G1` 150/250/350/450/600 × 3 | 9/15 | **15/15** | Đạt |
| N4 | Khung hình N1 | 1675 khung, Janky 2,5 %, 0 khung > 33 ms, dài nhất 28,5 ms | 1856 khung (nhiều hơn vì nay có đủ 4 lần đổi trang), Janky 5,7 %, 1 khung > 33 ms, dài nhất 35,1 ms; **bỏ lượt đầu tiên sau khi cài**: 1774 khung, Janky 3,0 %, 0 khung > 33 ms, dài nhất 31,8 ms | Đạt (không khung > 50 ms). Lượt đầu sau khi cài (62 % Janky) là khởi động nóng của bản debug mới cài: bản `main` cài lại cùng phiên cũng có lượt đầu 4 khung > 33 ms, dài nhất 51,8 ms |
| R1 | Chiều thuận FX7, `D` 60/80/120 × `G` 150/250 × 3 | 6/6 (một lượt mỗi tổ hợp; 518 khung, Janky 7,3 %, 1 khung > 33 ms, dài nhất 38,2 ms) | **18/18** Library mở (1561 khung, Janky 6,8 %, 3 khung > 33 ms, dài nhất 37,3 ms) | Đạt |
| R2 | Xen kẽ FX7 7d (mở, đóng, mở, đóng, mở), cách 150/250/400, `D` 60/80/120 × 2 | 18/18 (1715 khung, Janky 13,2 %, 3 khung > 33 ms, dài nhất 35,9 ms) | **18/18** Library mở (1720 khung, Janky 12,0 %, 7 khung > 33 ms, dài nhất 35,3 ms) | Đạt |
| R2b | B6: đóng rồi mở lại sau 150 ms | 6/6 | **6/6** | Đạt |
| R3 | 4 × vuốt phải `400/700` từ Library | 3/3 | **3/3** | Đạt |
| R3b | Trang cuối → Library vuốt trái 400 ms; Library → kéo phải 300 px trong 800 ms (về lại mở); kéo phải 750 px trong 800 ms (đóng) | 3/3 | **3/3** | Đạt (lượt mở chậm trên `LB-r1` Janky 69 %, `main` 12 %: một lượt, đường `OPEN` không bị sửa đụng tới; ghi lại để theo dõi) |
| R4 | Nút Home khi Library mở → đóng, ở trang 4 | — | **3/3** | Đạt |
| R4b | Nút Home rồi vuốt phải ngay (`input keyevent` rồi `Swiper` trong cùng lệnh shell) | — | **3/3** lùi về trang 3 | Đạt |
| R5 | Chạm nhẹ (`540,60`, 40 ms) 100 ms sau cú đóng / cú mở | 3/3 + 3/3 | **3/3 + 3/3** (đóng hẳn / mở hẳn, không kẹt; không mở nhầm app: `mCurrentFocus` vẫn `HomeActivity`) | Đạt |
| R6 | Vuốt phải ở trang cuối khi Library đóng, đứng yên | 1/1 | **1/1** (trang 3) | Đạt |
| R6b | Kéo icon ở trang cuối (edit mode) không bị Library cướp | — | Không chạy bằng script (sẽ đổi bố cục thật của chủ dự án); đường `OPEN`/`canOpen` không đổi | Chủ dự án thử tay |

Khung hình của các ca có Library trượt (R1-R5) dao động mạnh giữa từng lượt ở **cả hai bản** (một lượt R2 của `main` Janky 77 %, của `LB-r1` 49 %): đó là chi phí vẽ Library/kính trên Live 4 đã biết (AP: vuốt Home ↔ Library Janky ~21 %), không do sửa. Không thấy khung dài nào trùng lúc spring đóng dừng giữa lúc pager kéo (N1 bỏ lượt đầu: 0 khung > 33 ms), nên rủi ro `refreshHomeGlass` ở "Đánh đổi" không xảy ra ở phép đo này.

### Task 3: Knowledgement và đóng nhánh

**Bắt buộc kiểm tay:** không.
- [ ] Viết `docs/knowledgements/LB-library-back-swipe.md` theo mẫu mục 8 (Mermaid: `sequenceDiagram` cú vuốt thứ hai lúc spring đóng, trước/sau; `flowchart` quyết định `GRAB` ở `MOVE`). Điền "Bằng chứng Review Focus", "Đề xuất cập nhật tài liệu chung". Báo điều phối để rebase và chuyển PR sang Ready.

---

## Bài kiểm cả tính năng (điều phối gộp ở bước 5)

Máy: Vsmart Live 4, launcher mặc định, debug, dữ liệu thật (4 trang + Library); sao lưu dữ liệu launcher trước khi cài. Thứ tự: N1 → N2 → N3 → R1 → R2 → R3 → R4 → R5 → R6 (Task 2). Chủ dự án thử tay: từ App Library vuốt phải nhanh 3-4 lần về trang 1, lặp vài lần; rồi chiều thuận trang 1 → Library dưới 2 giây như FX7.
**Kết quả kiểm chứng (máy, ngày, nhánh@commit):** chưa.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú (vòng x/5, chặn, câu hỏi, đổi kế hoạch) |
|---|---|---|---|---|
| 2026-11-12 | 3 | Viết knowledgement; điền kết quả Task 2 | (commit docs) | Chờ điều phối review toàn nhánh |
| 2026-11-12 | 2 | Một phiên Live 4 (khóa `live4.lock`): `LB-r1.apk` (md5 khớp) chạy N1-N4, R1-R6; rồi `main-36f4630.apk` (md5 khớp) chạy R1 (một lượt mỗi tổ hợp), R2, R3b, R5, R6 để so khung hình. N1 **18/18** (main 3/18), N2 6/6, N3 15/15, mọi hồi quy đạt (bảng dưới Task 2). Kết thúc: cài lại bản `main`, trang 1, Library đóng, trả khóa | (không có commit mã) | Đạt; R6b chờ chủ dự án thử tay |
| 2026-11-12 | 1 | Nhánh mới trong `ACTION_MOVE` vượt slop: `GRAB` + spring đang đóng + `dx > 0` → `cancelCandidate()`, `PASS`; KDoc lớp và `Candidate.GRAB`. `assembleDebug` xanh; `apk/LB-r1.apk` (md5 `a1741b7b…`) | 390f384 | Chờ đo Task 2; chờ điều phối review |
| 2026-11-12 | 0 | Dựng `LB-t0-log.apk` (`main` + log `LBTMP`, tag `LBD`), cài Live 4 (khóa `live4.lock`, md5 khớp), chạy B2 × 6 và B1 rút gọn × 6: **xác nhận `TAKEOVER cand=GRAB` ở cú 2** (chi tiết dưới Task 0). Gỡ log (`git checkout`), `grep LBTMP` rỗng. Trả khóa, máy ở trang 1 | (không có commit mã) | Đạt |
| 2026-11-12 | Plan | **Điều phối duyệt plan** (ủy quyền của chủ dự án); chủ dự án trả lời Q1: có; điều phối thêm Task 0 (log tạm trước khi sửa) và cho agent tự cài/đo trên Live 4 có khóa | — | — |
| 2026-11-12 | Plan | Đọc FX7 (plan, knowledgement, Task 7d), mục D2 của audit, `LibraryOverlayController`, `HomeTouchRouter`, `AppLibraryController`, `HomePagerController`, `SpringProgress`, `AppLibraryMotion`. Đo baseline trên Live 4 bằng `Swiper` (bản `main` đang cài, không cài gì): B1-B7 + khung hình. Viết plan | (commit plan) | Chờ điều phối rà; Q1 |

## Bằng chứng Review Focus (điền khi đóng)

| # | Dòng Review Focus | Bằng chứng | Kết quả |
|---|---|---|---|
| 1 | Nhánh mới chỉ khi `GRAB` + spring đang đóng + `dx > 0`, đứng sau khối `GRAB` → `CLOSE`/`OPEN` | Diff `390f384`: điều kiện `candidate == Candidate.GRAB && !settleOpen && dx > 0` ngay sau khối `:124-127` (sau khối đó `GRAB` còn lại nghĩa là `settling == true`). Log Task 0: ca hỏng đúng là `DOWN cand=GRAB settling=true settleOpen=false` | Đạt (đọc code + log) |
| 2 | Chỉ `cancelCandidate()` + `PASS`; spring chạy nốt, `onSettled(false)` một lần | Diff: không đụng `spring`, `settling`, `setProgress`. N1/N2/N3: Library luôn kết thúc `G` (đóng), pager thẳng lưới | Đạt (đọc code + đo) |
| 3 | `GRAB` mở lại và `GRAB` lúc spring mở không đổi | R2 18/18, R2b 6/6 (`main`: 18/18, 6/6) | Đạt (đo được) |
| 4 | `CLOSE`/`OPEN` thường không đổi | R1 18/18, R3 3/3, R3b 3/3, R6 1/1 | Đạt (đo được) |
| 5 | Chạm nhẹ lúc spring chạy: về đích cũ, không kẹt | R5 3/3 + 3/3, không mở nhầm app | Đạt (đo được) |
| 6 | Không file ngoài vùng; không `LBTMP` | `git diff origin/main --stat` (code): chỉ `ui/home/LibraryOverlayController.kt`; `grep -rn LBTMP app/src` rỗng; `LB-r1.apk` dựng từ cây sạch | Đạt |

## Đề xuất cập nhật tài liệu chung (điều phối áp dụng sau merge)

- `Launcher-Features.md`: không.
- `project_structure.md`: không (không thêm file).
- `room-schema.md`: không.
- `lessons/home-pager-animation.md`: thêm vào mục FX7: "Hoạt ảnh ngắt được (`GRAB`) chỉ nên bắt cú vuốt **ngược** chiều hoạt ảnh đang chạy; cú vuốt **cùng** chiều thuộc lớp phía sau (pager). FX7 vòng 4 bắt cả hai chiều nên cú vuốt phải ngay sau khi đóng Library bị nuốt (`main`: 3/18 ở nhịp chủ dự án). Ma trận kiểm cử chỉ ngắt được phải có cả ca cùng chiều, không chỉ xen kẽ" (đo được, LB, 2026-11-12).
- `MultiAgent-Workflow.md` mục 10: dòng LB.
