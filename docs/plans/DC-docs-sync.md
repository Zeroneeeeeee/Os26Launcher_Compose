# DC — Đồng bộ tài liệu chung với code, mục lục plan và danh sách việc còn lại — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`. Chỉ sửa tài liệu, không đụng code, không xoá hay di chuyển file.

**Bắt đầu:** 2026-11-25 · **Plan duyệt:** 2026-11-25 (chủ dự án: "ok, bạn tạo đi", kèm trả lời từng mục danh sách việc)
**Trạng thái:** Xong 2026-11-25 (Task 1–3), chờ chủ dự án xem
**Nhánh:** `an` · **Đổi schema Room:** không

**Goal:** (1) Tài liệu chung (`Launcher-Features.md`, `project_structure.md`) nói đúng code Compose hiện tại. (2) Một chỗ tra plan: `plans/README.md`. (3) Một chỗ ghi việc còn lại: `plans/BACKLOG.md`; việc nhỏ ghi một dòng ở đây, chỉ tạo plan mới khi bắt đầu làm một mục đã duyệt (tránh file plan rác).

## Hiện trạng (đối chiếu code 2026-11-25)

- `docs/plans/` có 88 file: khoảng 60 file chép từ repo XML (trạng thái trong file là của repo XML, nhiều file còn ghi "chờ review / chờ merge"), chuỗi Compose JC → JX, và các plan làm trên repo Compose từ 2026-11-23 (PG, PG1–PG4, LD, SW-home-swipe-smoothness, WF, PD). Trùng mã SW: `SW-menu-width-fit.md` (XML) và `SW-home-swipe-smoothness.md`.
- Có 349 đường dẫn `plans/…` trong 124 file (lessons, knowledgements, AGENTS, comment trong code) → **không** di chuyển plan sang thư mục con.
- `Launcher-Features.md` lệch code: mục 1 ghi UI là View/XML (JX đã gỡ lớp View); dòng 11 ghi Thư viện "chỉ đọc (không kéo-thả/ẩn), nền phẳng" (LD đã có chế độ sửa, gỡ cài đặt, kéo ra Home); dòng 21 ghi Trung tâm điều khiển "Chưa" (code có `ui/controlcenter/`, G3d); thiếu dòng Today View (code có `ui/home/compose/today/`); mục 4 còn "Đa ngôn ngữ" (LG xong).
- `project_structure.md`: đầu file vẫn ghi "bản thiết kế, chờ bạn duyệt… chưa viết dòng code nào"; danh sách "Tệp thêm ở…" dừng ở JS + JX.
- `room-schema.md` (v3, `offHome`, `className = "recents"`) và `config-schema.md`: khớp code.

## Vùng file

| File | Tạo / Sửa | Task |
|---|---|---|
| `docs/Launcher-Features.md` | Sửa | 1 |
| `docs/project_structure.md` | Sửa | 1 |
| `docs/AGENTS.md` (quyết định kính dưới API 31; trỏ tới mục lục / backlog) | Sửa | 1 |
| `docs/plans/README.md` | Tạo | 2 |
| `docs/plans/BACKLOG.md` | Tạo | 3 |
| `docs/plans/WF-widget-drag-first-run.md`, `docs/plans/PD-picker-menus-dialogs.md` (kết quả kiểm chứng: chủ dự án đã kiểm) | Sửa | 3 |

---

### Task 1: Tài liệu chung khớp code
- [x] Step 1: `Launcher-Features.md`: mục 1 (UI Compose), dòng 11, dòng 21, thêm dòng 22 Today View, mục 4.
- [x] Step 2: `project_structure.md`: đầu file; đoạn "Tệp thêm từ 2026-11-23 (repo Compose)" liệt kê theo commit (`git log --diff-filter=A 2615d5d..HEAD`).
- [x] Step 3: `AGENTS.md`: quyết định chủ dự án 2026-11-25 (dưới API 31 chỉ dùng kính mờ tĩnh, tập trung cải thiện API 31+); dẫn `plans/README.md` + `plans/BACKLOG.md`.

### Task 2: Mục lục plan
- [x] Step 1: `plans/README.md`: quy ước (một mục backlog → một plan khi bắt đầu làm), nhóm Compose đang mở / đã xong, nhóm lịch sử XML (liệt kê mã, không cập nhật trạng thái bên trong).

### Task 3: Danh sách việc còn lại
- [x] Step 1: `plans/BACKLOG.md` theo trả lời chủ dự án 2026-11-25 (mục đã kiểm bỏ; mục của anh dvha ghi chờ merge; hoãn có lý do).
- [x] Step 2: WF, PD: ghi chủ dự án đã kiểm (bố cục lần đầu; giữ thẻ là kéo, thẻ chặn chạm); lỗi mới về menu nhấn giữ icon ghi vào backlog.

## Nhật ký

| Ngày | Task | Việc | Kết quả |
|---|---|---|---|
| 2026-11-25 | — | Đối chiếu tài liệu chung với code, đếm link `plans/`, đề xuất mục lục + backlog | Chủ dự án duyệt |
| 2026-11-25 | 1–3 | Sửa tài liệu chung, tạo `README.md`, `BACKLOG.md` | Chờ chủ dự án xem |
