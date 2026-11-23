# Quy trình làm việc — Os26Launcher (từ 2026-11-16)

Trạng thái: **chủ dự án chốt 2026-11-16**, thay [MultiAgent-Workflow.md](MultiAgent-Workflow.md) (file đó chỉ còn để tham khảo lịch sử, bảng phụ thuộc ở mục 9 và bảng nhánh đã merge ở mục 10).

File này bổ sung cho [AGENTS.md](AGENTS.md). Các quy ước khác ở AGENTS.md giữ nguyên: không tự xóa file, không viết test tự động, commit message tiếng Anh, lệnh viết cho CMD/PowerShell, Vsmart Live 4 là máy test chính, quy trình gỡ lỗi. Hai file mâu thuẫn chỗ nào thì dừng lại hỏi chủ dự án.

---

## 1. Nguyên tắc

- **Một tính năng một lúc, làm nối tiếp.** Không chạy song song nhiều tính năng, không giao code cho agent phụ.
- **Tính năng tiếp theo do chủ dự án quyết**, hoặc lấy theo thứ tự đã được chủ dự án chốt trước đó ([Figma-Draft.md](Figma-Draft.md) mục 6, `project_structure.md` mục 9). Claude không tự chọn tính năng ngoài danh sách đã chốt; danh sách có nhiều việc ngang hàng thì hỏi.
- **Claude trực tiếp triển khai**: viết plan, code, cập nhật docs.
- **Chủ dự án** review, test, chốt hoặc góp ý sửa, và **tự commit/push**.

| Vai | Làm gì |
|---|---|
| Chủ dự án | Chọn tính năng (hoặc duyệt thứ tự); duyệt plan; xác nhận từng Task; review code và kiểm tay cả tính năng; chốt hoặc góp ý; commit, push, merge |
| Claude | Đọc docs liên quan; viết plan; code từng Task; tự rà diff; build khi môi trường cho phép (mục 5); viết bài kiểm và knowledgement; gỡ lỗi; chốt plan lần cuối; cập nhật tài liệu chung; soạn commit message |

## 2. Vòng đời một tính năng

**Bước 1 — Đọc trước khi viết.** `AGENTS.md`, `lessons/README.md` rồi các file chủ đề trong `lessons/` liên quan, `project_structure.md`, mục TT# trong `Launcher-Features.md`, `room-schema.md` nếu đụng bố cục, `Figma-Draft.md` nếu có màn trong bản thiết kế, plan của các mốc liên quan.

**Bước 2 — Plan.** Viết `docs/plans/<ID>-<slug>.md` theo mẫu ở mục 4. Câu hỏi chỉ chủ dự án trả lời được (phạm vi, hành vi UI, đánh đổi) gom ở mục "Câu hỏi" của plan, kèm khuyến nghị. Dừng và báo. **Không viết code trước khi plan được duyệt.**

**Bước 3 — Duyệt plan.** Chủ dự án duyệt hoặc góp ý; Claude sửa plan rồi báo lại điểm đã đổi. Duyệt xong ghi ngày vào plan.

**Bước 4 — Từng Task.**
1. Code đúng phạm vi Task.
2. Tự rà diff trước khi báo: khớp Task (không thừa, không bỏ bước, không sửa file ngoài "Vùng file" của plan); đúng luật phụ thuộc `ui → domain ← data`; không lặp lại bẫy đã ghi trong `AGENTS.md` và `lessons/`; trường hợp biên mà kiểm tay khó bắt.
3. Build `gradlew.bat assembleDebug` nếu phiên build được (mục 5); build đỏ thì không báo xong. Phiên không build được thì ghi rõ "chưa build" trong báo cáo để chủ dự án build.
4. Ghi một dòng vào mục **Nhật ký** của plan.
5. Báo cáo: đã làm gì, file nào, điểm cần soi, kịch bản kiểm của Task (gộp vào bài kiểm cuối).
6. **Chờ chủ dự án xác nhận mới tick Task và sang Task kế** (quy ước dự án). Task **bắt buộc kiểm tay** (mục 3) thì chủ dự án kiểm trên máy trước khi xác nhận.

**Bước 5 — Xong tính năng.** Khi mọi Task đã được xác nhận:
1. Gộp kịch bản của mọi Task thành **một bài kiểm**: theo thứ tự thao tác tự nhiên trên máy, bỏ bước trùng, thêm phần hồi quy cho tính năng cũ có thể bị ảnh hưởng, ghi rõ máy và dữ liệu cần chuẩn bị.
2. Viết knowledgement (mục 6) nếu tính năng có từ 2 Task trở lên hoặc đổi luồng có sẵn; việc sửa nhỏ một Task thì bỏ qua, giải thích trong báo cáo.
3. Báo chủ dự án: bài kiểm, knowledgement, danh sách file đã đổi.

**Bước 6 — Chủ dự án review và test.**
- **Đạt:** sang bước 7.
- **Có lỗi:** gỡ lỗi theo quy trình ở `AGENTS.md`, sửa, tự rà, build (nếu được), báo. Chủ dự án chỉ kiểm lại **các mục lỗi + phần hồi quy ngắn** liên quan, không chạy lại cả bài.
- **Góp ý đổi phạm vi hoặc thiết kế:** sửa plan trước (ghi vào "Quyết định đã chốt" và Nhật ký), báo điểm đổi, rồi mới code.
- Vá cùng một chỗ lần thứ hai vẫn hỏng thì dừng, xem lại thiết kế trong plan và đề xuất 2–3 hướng khác cho chủ dự án chọn.

**Bước 7 — Chốt plan lần cuối và báo commit.**
1. Plan: tick đủ Task, điền "Kết quả kiểm chứng" (máy, ngày), "Bằng chứng Review Focus", trạng thái `Xong YYYY-MM-DD`.
2. Cập nhật **trực tiếp** tài liệu chung bị ảnh hưởng: `Launcher-Features.md`, `project_structure.md` (dòng mốc ở mục 9), `room-schema.md`, `Figma-Draft.md` mục 6 (dấu ✅), bài học vào cuối file chủ đề đúng trong `lessons/` (giữ ngày và nhãn nguồn), quy ước chung vào `AGENTS.md`.
3. Gỡ log tạm, công tắc đo tạm; liệt kê file cần xóa (nếu có) kèm lý do, chờ duyệt.
4. Báo chủ dự án commit: danh sách file đã đổi và **commit message soạn sẵn** (tiếng Anh, đúng quy ước AGENTS.md; nhiều commit thì chia theo nhóm file). **Claude không tự commit, push hay merge**, trừ khi chủ dự án bảo rõ trong lượt đó.

**Nhánh:** mặc định làm trên thư mục chính, nhánh đang checkout. Chủ dự án muốn nhánh riêng thì tạo trước khi Claude bắt đầu code. Không dùng worktree, không mở PR trừ khi chủ dự án bảo.

## 3. Task bắt buộc kiểm tay

Chỉ đánh dấu khi Task thuộc một trong các loại sau, và plan ghi lý do:
- Task sau xây trên một **giả định chỉ máy thật trả lời được** (hệ thống có cho launcher đọc dữ liệu X không, ROM có phát callback Y không);
- **migration Room** (cài bản cũ → tạo dữ liệu → cài bản mới lên trên);
- **phép đo hiệu năng** dùng để chọn phương án (Janky của kính, thời gian nạp);
- **cử chỉ chạm/kéo-thả** mới (loại lỗi review code khó thấy, bài học M7).

## 4. Mẫu plan

Giữ khung của các plan cũ (gần nhất là `plans/WX-weather-cities.md`), bỏ các dòng của luồng nhiều agent (worktree, PR, khóa, vòng x/5, điều phối).

```markdown
# <ID> — <Tên tính năng> (TT#..) — Implementation Plan

> Làm theo `docs/AGENTS.md` và `docs/Workflow.md`: code từng Task, báo cáo, chờ chủ dự án xác nhận mới tick và sang Task sau; cả tính năng kiểm tay một lần ở cuối. Không có test tự động; kiểm chứng tay trên Vsmart Live 4 (Oppo A3x chỉ khi cần kính). Lệnh viết cho CMD/PowerShell.

**Bắt đầu:** YYYY-MM-DD · **Plan duyệt:** YYYY-MM-DD
**Trạng thái:** Chờ duyệt plan | Đang làm Task N | Chờ kiểm tay Task N | Chờ review cả tính năng | Xong YYYY-MM-DD
**Máy:** Live 4 | Live 4 + Oppo (lý do) · **Đổi schema Room:** không | có (version x → y)

**Goal:** ...
**Architecture:** ...
**Tech Stack:** ... (dependency mới nếu có, kèm version đã kiểm theo AGENTS.md)
**Spec:** TT#.. trong `Launcher-Features.md` / màn .. trong `Figma-Draft.md` + tài liệu liên quan

## Vì sao chọn thiết kế này
- **Vấn đề cần giải:** ràng buộc thật của dự án (tier máy, bẫy trong AGENTS.md/lessons, số đo đã có).
- **Các phương án đã xét** (ít nhất 2):
| Phương án | Cách làm | Ưu | Nhược / rủi ro | Chọn? |
- **Vì sao chọn:** dựa trên số đo, tài liệu chính thức hoặc bài học của dự án (ghi nhãn nguồn).
- **Đánh đổi chấp nhận:** cái gì mất đi, khi nào nên xét lại.

## Quyết định đã chốt
| # | Quyết định | Ghi chú (ai quyết, ngày, cơ sở) |

## Câu hỏi cho chủ dự án
| # | Câu hỏi | Khuyến nghị | Trả lời |

## Ngoài phạm vi / ghi để sau

## Vùng file
| File | Tạo / Sửa | Ghi chú |

## Global Constraints

## Review Focus

---

### Task N: <tên>
**Bắt buộc kiểm tay:** không | có — lý do (`Workflow.md` mục 3)
**Files:** ...
**Interfaces:** Consumes / Produces
- [ ] **Step 1: ...**
- [ ] **Step k: Kịch bản kiểm (Live 4)** — (a), (b), ...

---

## Bài kiểm cả tính năng (Claude gộp ở bước 5)
Máy · dữ liệu cần chuẩn bị · các mục kiểm theo thứ tự thao tác · phần hồi quy.
**Kết quả kiểm chứng (máy, ngày):** ...

## Nhật ký
| Ngày | Task | Việc đã làm | Ghi chú (lỗi, câu hỏi, đổi kế hoạch) |

## Bằng chứng Review Focus (điền khi chốt)
| # | Dòng Review Focus | Bằng chứng | Kết quả |

## Tài liệu chung đã cập nhật (điền ở bước 7)
- `Launcher-Features.md`: ...
- `project_structure.md`: ...
- `room-schema.md`: ...
- `lessons/<chủ đề>.md` / `AGENTS.md`: ...
```

## 5. Môi trường: ai build, ai cài, ai commit

Phiên Claude có hai kiểu, đầu mỗi phiên kiểm bằng `java -version` và `adb version`:

| Kiểu phiên | Build | Cài APK, đọc logcat | Commit |
|---|---|---|---|
| Claude Code CLI chạy thẳng trên Windows (`AGENTS.md` mục "Môi trường", đo 2026-11-02) | Claude chạy `gradlew.bat assembleDebug` | Chỉ khi chủ dự án bảo và `adb devices` thấy máy | Chủ dự án (Claude chỉ khi được bảo rõ) |
| Claude trên claude.ai nối vào máy qua Linux VM (`$HOME/mnt/Os26Launcher_Compose`; trước 2026-11-23 là `$HOME/mnt/IosLauncher`, đo 2026-11-16) | **Không**: JDK 11, không `adb`, `dl.google.com` trả 403. Chủ dự án build và gửi lỗi | Chủ dự án | Chủ dự án. Git trong VM không xóa được file nên `git commit`/`pull` để lại `.git/*.lock` |

Kiểm tay luôn là việc của chủ dự án. Ghi rõ máy cho từng bước kiểm (Live 4 mặc định, Oppo chỉ khi cần kính).

## 6. Mẫu knowledgement

Người đọc là **chủ dự án**, đọc **trước** khi review code. Giải thích thuật ngữ Android ở lần đầu xuất hiện, dùng ví dụ thật của dự án (giọng như [knowledgements/android-task-affinity.md](knowledgements/android-task-affinity.md)). Mọi khẳng định ghi nhãn **đo được** / **đọc tài liệu** / **suy luận chưa kiểm chứng**.

**Luồng hoạt động bắt buộc viết bằng Mermaid:** `sequenceDiagram` cho thao tác chính đi qua các tầng (ít nhất một), `flowchart` cho rẽ nhánh theo điều kiện, `stateDiagram-v2` khi UI có trạng thái chuyển qua lại. Tên trong sơ đồ dùng đúng tên class/hàm trong code; nhãn tiếng Việt, chữ có ngoặc hay ký tự đặc biệt thì đặt trong ngoặc kép. Ví dụ đầy đủ: `MultiAgent-Workflow.md` mục 8.

```markdown
# <ID> — <Tên tính năng>: đọc trước khi review

**Plan:** [plans/<ID>-<slug>.md](../plans/<ID>-<slug>.md)

## 1. Tóm tắt
## 2. Kiến thức nền
## 3. Luồng hoạt động (Mermaid + 2–5 dòng giải thích dưới mỗi sơ đồ)
## 4. Thay đổi theo file
| File | Vai trò | Vì sao thêm/sửa | Đọc trước? |
## 5. Quyết định và đánh đổi
## 6. Chỗ cần soi khi review
## 7. Cách kiểm lại
## 8. Bài học (đã chép vào `lessons/<chủ đề>.md` ở bước 7)
```

## 7. Đặt tên

Tên file, tên tài nguyên viết **tiếng Anh**; nội dung mọi file trong `docs/` viết **tiếng Việt**.

| Thứ | Quy tắc | Ví dụ |
|---|---|---|
| Mã tính năng (ID) | Theo `project_structure.md` mục 9 hoặc `Figma-Draft.md` mục 6; chưa có mã thì Claude đề xuất trong plan | `CI` |
| Slug | Tiếng Anh, chữ thường, gạch nối, 2–4 từ | `custom-icons` |
| Plan | `docs/plans/<ID>-<slug>.md` | `docs/plans/CI-custom-icons.md` |
| Knowledgement | `docs/knowledgements/<ID>-<slug>.md`, cùng tên gốc với plan | `docs/knowledgements/CI-custom-icons.md` |
| Nhánh (nếu chủ dự án tạo) | `feature/<id>-<slug>`, `fix/`, `refactor/`, `docs/` | `feature/ci-custom-icons` |
| Chuỗi, màu, dimens | Tiền tố theo tính năng, thêm thành một khối liền ở **cuối** file tài nguyên | `custom_icon_title` |

## 8. Những gì bỏ so với MultiAgent-Workflow.md

Vai điều phối và agent thực thi; worktree và chép `local.properties`; draft PR và "Ready for review"; file nóng độc quyền, khóa schema, khóa Oppo (chỉ còn một tính năng chạy nên không cần); giới hạn 5 vòng (thay bằng luật "vá lần hai thì dừng" ở bước 6); bảng model/effort; bảng trạng thái nhánh (trạng thái tính năng ghi ở `project_structure.md` mục 9 và `Figma-Draft.md` mục 6); mục "Đề xuất cập nhật tài liệu chung" (Claude cập nhật thẳng ở bước 7).
