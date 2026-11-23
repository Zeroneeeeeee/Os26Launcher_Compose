# DR — Tổ chức lại thư mục `docs/` (tách bài học khỏi AGENTS.md) — Implementation Plan

> **Dành cho agent thực thi:** làm theo `docs/AGENTS.md` và `docs/MultiAgent-Workflow.md`. Task này **chỉ đụng tài liệu**, không code, không máy thật, không xóa file (chỉ chuyển chữ ra khỏi `AGENTS.md`).

**Nhánh:** `docs/dr-docs-reorg` · **Worktree:** `E:\SourceTree\IosLauncher-wt\DR-docs-reorg` · **PR:** draft (xem bảng trạng thái ở MultiAgent-Workflow mục 10) · **Bắt đầu:** 2026-11-10 · **Plan duyệt:** 2026-11-10 (chủ dự án duyệt sẵn "làm luôn, chỉnh sau")
**Trạng thái:** Đã làm xong, chờ review
**Khóa:** không

**Goal:** `docs/AGENTS.md` (747 dòng, ~140 KB) quá dài, agent đọc tốn ngữ cảnh và khó tìm bài học. Giữ `AGENTS.md` ngắn (chỉ cái mọi agent cần mọi lần) và chuyển các mục "bài học" theo mốc sang các file chủ đề trong `docs/lessons/`, **không mất một dòng nội dung nào**.

**Architecture:** `AGENTS.md` giữ: quy ước làm việc, tên gọi/package, build/toolchain, môi trường Claude, quy tắc kiểm trên máy thật (và thông tin máy), quy tắc viết docs, danh sách việc trước khi phát hành lên cửa hàng, kèm mục lục trỏ sang `docs/lessons/`. Chín file chủ đề trong `docs/lessons/` chứa nguyên văn các mục bài học, giữ thứ tự thời gian, giữ ngày, nhãn nguồn và thẻ mốc. `docs/lessons/README.md` là mục lục và bảng tra tên mục cũ → file mới.

**Tech Stack:** chỉ Markdown; kiểm bằng script Python tạm (không đưa vào repo).

**Spec:** dòng DR ở mục 9 của `MultiAgent-Workflow.md`.

## Vì sao chọn thiết kế này

- **Vấn đề cần giải (đo được):** `AGENTS.md` đã 747 dòng/~140 KB, hơn 50 mục "bài học" nối đuôi theo mốc. Agent bước 1 của vòng đời phải đọc "toàn bộ", nên tốn hàng chục nghìn token cho cả phần không liên quan (vd làm tìm kiếm mà đọc kéo-thả). Bài học cùng chủ đề (kính: M6, M8a Task 4, GL, GS, DG, SM) nằm rải rác ở 6 chỗ xa nhau nên khó thấy hết khi làm một việc về kính.
- **Các phương án đã xét:**

| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
|---|---|---|---|---|
| **A** | `AGENTS.md` ngắn + `docs/lessons/<chủ đề>.md`; mỗi mục bài học chuyển nguyên văn sang file chủ đề; có mục lục | Agent đọc đúng file chủ đề cần; gom bài học cùng chủ đề một chỗ; `AGENTS.md` nhỏ, đọc hết mỗi lần; knowledgement vẫn là nơi viết thô | Phải quyết một mục thuộc chủ đề nào (một số mục pha nhiều chủ đề); thêm một bước điều phối khi gộp bài học | **Chọn** |
| B | Bỏ mục bài học khỏi `AGENTS.md`, để bài học ở "Bài học cho AGENTS.md" của từng knowledgement, `AGENTS.md` chỉ có mục lục theo mốc | Không phải di chuyển chữ; không cần chọn chủ đề | Bài học nằm trong 23+ file; muốn biết "bẫy kính" phải mở nhiều knowledgement dài; knowledgement viết cho chủ dự án đọc trước review, trộn với kiến thức nền nên mật độ bài học thấp; mất ý nghĩa "tổng hợp" của `AGENTS.md` | Không |
| C | Giữ một file nhưng thêm mục lục và đánh dấu "bắt buộc đọc" / "đọc khi cần" | Rẻ nhất | Không giảm ngữ cảnh: agent vẫn nạp cả file khi đọc; chỉ báo trước chứ không chặn | Không |

- **Vì sao chọn A:** theo số đo ở trên, vấn đề là kích thước file bị nạp. Chỉ A và B làm nhỏ file bị nạp mặc định; A giữ bài học ở dạng đã chắt lọc, gom theo chủ đề nên đọc một lần là đủ, còn B ép agent đọc nhiều tài liệu dài. A cũng giữ nguyên chuỗi chữ của bài học nên đối chiếu "không mất nội dung" làm được bằng script (nhãn **đo được** / **đọc code** / **suy luận chưa kiểm chứng**, ngày và thẻ mốc đi theo từng mục).
- **Đánh đổi chấp nhận:** (1) thêm một bước cho điều phối: bài học mới đi vào file chủ đề thay vì cuối `AGENTS.md`; (2) vài mục pha nhiều chủ đề (B1+CL+IC, DD+SM+DG, RB+G3a) bị tách theo từng gạch đầu dòng, nên tiêu đề mục mới ghi "tách từ «...»"; (3) các plan/knowledgement cũ viết "AGENTS.md mục P1" bằng chữ thường (không phải link) sẽ không trỏ đúng; bảng tra trong `lessons/README.md` giải quyết. Xét lại nếu một chủ đề phình quá ~400 dòng: tách tiếp.

## Quyết định đã chốt

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Phương án A | Chủ dự án yêu cầu 2026-11-09/10; hướng đề xuất ở dòng DR |
| D2 | Chín file chủ đề: `glass`, `drag-and-layout`, `startup-performance`, `search`, `widgets`, `icons-and-app-list`, `permissions-and-device-testing`, `home-pager-animation`, `settings-and-data` | Theo gợi ý của chủ dự án, gộp "quyền" với "kiểm trên máy thật" |
| D3 | Chỉ **chuyển** chữ, không viết lại, không gộp bài học trùng | "Gộp chỉ khi nói đúng một điều": rà thấy không có cặp nào trùng nguyên văn nên giữ cả hai ở mọi chỗ |
| D4 | Không di chuyển, đổi tên `plans/` và `knowledgements/` | Nhánh G3e, AP, IP đang thêm file ở đó; dòng DR đề cập gom theo mốc là tùy chọn, **không làm** lần này (đã ghi ở "Ngoài phạm vi") |
| D5 | `AGENTS.md` giữ cả mục "Trước khi phát hành lên cửa hàng" và "Kiểm chứng trên máy thật" | Mọi agent cần mỗi lần |
| D6 | Bài học mới: knowledgement mục "Bài học cho AGENTS.md" → điều phối đưa vào đúng file `docs/lessons/` | Sửa MultiAgent-Workflow mục 5.4, 7, 8 |

## Ngoài phạm vi / ghi để sau

- Gom `plans/` và `knowledgements/` đã merge vào thư mục theo mốc: bỏ qua vì nhánh khác đang thêm file ở đó (đã dặn chủ dự án); làm riêng khi không còn nhánh nào mở.
- Sửa chữ "AGENTS.md mục X" trong các plan/knowledgement cũ: không đụng docs nhánh khác.
- Cập nhật memory của Claude (`MEMORY.md`): việc của điều phối (nằm ngoài repo).

## Vùng file

| File | Tạo / Sửa | Nóng |
|---|---|---|
| `docs/AGENTS.md` | Sửa (chuyển chữ ra) | tài liệu chung, DR được phép |
| `docs/lessons/*.md` (9 file chủ đề + `README.md`) | Tạo | mới |
| `docs/MultiAgent-Workflow.md` | Sửa (mục 4 bước 1, 5.4, 7, 8, 9 dòng DR) | tài liệu chung, DR được phép |
| `docs/project_structure.md` | Sửa (cây docs) | tài liệu chung, DR được phép |
| `docs/plans/DR-docs-reorg.md`, `docs/knowledgements/DR-docs-reorg.md` | Tạo | mới |

## Global Constraints

- Không đổi code, không máy thật, không xóa file; commit tiếng Anh `<type>: <mệnh lệnh>`.
- Không mất nội dung: mỗi dòng không rỗng của `AGENTS.md` cũ xuất hiện đúng một lần ở nơi mới (cho phép khác khoảng trắng).
- Không sửa file trong `plans/`, `knowledgements/` của nhánh khác.

## Review Focus

1. Số dòng của script đối chiếu: thiếu = 0, trùng thừa = 0.
2. Mọi link nội bộ trong `docs/` còn sống (script kiểm).
3. `AGENTS.md` mới đọc một lần là đủ cho: quy ước, build, môi trường, máy thật, viết docs, phát hành; có mục lục sang `docs/lessons/`.
4. Mục 5.4/7/8 của MultiAgent-Workflow nói đúng nơi bài học mới đi.

---

### Task 1: Chuyển bài học theo chủ đề
**Bắt buộc kiểm tay:** không (chỉ tài liệu).
**Files:** `docs/AGENTS.md`, `docs/lessons/*`.
- [x] **Step 1:** lập bảng "tiêu đề mục cũ → file mới" (một mục pha nhiều chủ đề thì tách theo gạch đầu dòng).
- [x] **Step 2:** script chuyển nguyên văn, giữ thứ tự; viết `lessons/README.md`.
- [x] **Step 3:** script đối chiếu từng dòng và kiểm link; ghi số vào knowledgement.

### Task 2: Cập nhật tài liệu chung
- [x] Sửa MultiAgent-Workflow (mục 4 bước 1, 5.4, 7, 8, dòng DR) và `project_structure.md` (cây docs).
- [x] Viết knowledgement `DR-docs-reorg.md`.

---

## Bài kiểm cả tính năng

Không cần máy. Chủ dự án: mở `docs/AGENTS.md` (ngắn), vào `docs/lessons/README.md`, thử tìm một bài học cũ (vd "P1 cold start") bằng bảng tra, và đọc số đối chiếu ở knowledgement.

## Nhật ký

| Ngày | Task | Việc đã làm | Commit | Ghi chú |
|---|---|---|---|---|
| 2026-11-10 | Plan + 1 + 2 | Plan, chuyển nội dung, sửa tài liệu chung, knowledgement | xem PR | Chủ dự án duyệt sẵn "làm luôn, chỉnh sau" |

## Đề xuất cập nhật tài liệu chung

DR được phép sửa trực tiếp `AGENTS.md`, `MultiAgent-Workflow.md`, `project_structure.md`. `Launcher-Features.md`, `room-schema.md`: không đổi. Memory: điều phối cập nhật mục "Docs language and branch docs" nói bài học vào `docs/lessons/`.
