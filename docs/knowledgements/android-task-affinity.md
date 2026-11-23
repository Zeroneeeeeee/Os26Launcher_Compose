# Task, taskAffinity và vì sao "Demo kính" từng không mở được

Tài liệu lý thuyết, không phải quyết định của dự án — giải thích cơ chế Android đứng sau 1 bug cụ thể gặp ở M3 Task 5 (2026-10-27): bấm icon "Demo kính" từ lưới launcher không phản hồi; nếu launcher đang chạy ngầm, bấm icon đó từ nơi khác lại chỉ đưa launcher lên trước.

## 1. Task là gì

Một **Task** là một ngăn xếp (stack) các Activity mà người dùng điều hướng qua lại bằng nút Back. Khi mở một app, Android không nhất thiết tạo Task mới cho app đó — nó tìm xem có Task nào "phù hợp" để đặt Activity mới vào không, dựa trên vài quy tắc. Mỗi Task có một con số nhận diện và một **affinity** (độ "thân thuộc") để Android quyết định Activity nào thuộc Task nào.

Khi bấm nút Home, Task hiện tại không bị hủy — nó lùi vào nền ("background task"), launcher (chính app của dự án này) hiện lên. Mở lại app đó từ Recents sẽ đưa đúng Task đó lên trước, với toàn bộ back stack cũ còn nguyên.

## 2. taskAffinity: Activity "thuộc về" Task nào

Mỗi Activity có một thuộc tính `android:taskAffinity`. **Mặc định, nếu không khai báo, nó bằng tên package của app**. Nghĩa là: **mọi Activity trong cùng một app, nếu không khai `taskAffinity` riêng, đều coi như "cùng một nhóm Task".**

`taskAffinity` chỉ thật sự có tác dụng khi kết hợp với:
- Activity được khởi chạy bằng `Intent.FLAG_ACTIVITY_NEW_TASK` (luôn có khi hệ thống mở app từ launcher, từ icon, hoặc từ `LauncherApps.startMainActivity`/`startActivity` bên ngoài một Activity context), **hoặc**
- Activity có `launchMode="singleTask"` hoặc `"singleInstance"` (hai launchMode này luôn ứng xử như thể có cờ `NEW_TASK`).

Khi một trong hai điều trên đúng, hệ thống **tìm trong các Task đang tồn tại** (kể cả đang chạy ngầm) xem có Task nào có affinity **trùng** với affinity của Activity sắp mở không:
- **Có Task trùng affinity đang tồn tại** → hệ thống ưu tiên đưa Task đó lên trước, thay vì tạo Task mới. Activity mới có thể được đặt vào Task đó (nếu nó chưa có trong Task) hoặc, tùy tình huống, hệ thống chỉ đơn giản đưa Task cũ lên mà không thực sự "tới" được Activity mới (xem phần 4).
- **Không có Task nào trùng** → một Task mới được tạo, với Activity mới làm root.

## 3. launchMode liên quan

| launchMode | Hành vi |
|---|---|
| `standard` (mặc định) | Luôn tạo instance mới, đặt vào Task đang gọi nó (trừ khi có cờ Intent khác can thiệp) |
| `singleTop` | Giống `standard`, trừ khi instance đã ở ngay đỉnh stack thì tái dùng (gọi `onNewIntent`) |
| `singleTask` | Activity này **chỉ có 1 instance trong toàn hệ thống**, luôn là **root** của Task mang affinity của nó. Nếu Task đó đã tồn tại, hệ thống đưa Task lên trước và xóa mọi Activity nằm trên nó trong Task đó |
| `singleInstance` | Như `singleTask`, nhưng Task của nó **không bao giờ chứa Activity nào khác** |

`HomeActivity` của dự án này là `singleTask` — đúng và cần thiết cho một launcher: dù mở lại HOME bao nhiêu lần, chỉ có đúng 1 instance, 1 Task.

## 4. `clearTaskOnLaunch`

Thuộc tính này chỉ có ý nghĩa cho Activity là **root của Task** (như `HomeActivity`). `true` nghĩa là: **mỗi lần Task này được đưa lên nền trước từ bên ngoài** (ví dụ người dùng bấm Home, hoặc bất kỳ ai khởi chạy một Activity khớp affinity của Task này), mọi Activity phía trên root trong Task đó bị dọn sạch, chỉ còn lại root Activity.

Mục đích ban đầu (hợp lý cho launcher): nếu người dùng từng mở 1 Activity khác *bên trong* Task của launcher rồi bấm Home đi nơi khác, lần sau quay lại Home phải thấy đúng màn hình chính, không phải Activity dở dang đó.

## 5. Chuỗi sự kiện gây ra bug

Dự án có 2 Activity **cùng 1 package** (`gambi.global.os26launcher`), không Activity nào khai `taskAffinity` riêng → cả hai mặc định **cùng affinity = tên package**:

- `HomeActivity`: `singleTask`, `clearTaskOnLaunch=true`, category `HOME`+`LAUNCHER` → luôn là root của 1 Task "chính" của app, Task này tồn tại gần như suốt vòng đời (launcher luôn chạy).
- `MainActivity` ("Demo kính"): `standard` (không khai `launchMode`), nhưng khi mở từ icon (`LauncherApps.startMainActivity` hoặc chạm icon trên 1 launcher khác) luôn có cờ `NEW_TASK`.

Khi `MainActivity` được mở với cờ `NEW_TASK`, hệ thống tìm Task trùng affinity → luôn thấy ngay Task của `HomeActivity` (vì nó gần như luôn tồn tại, do launcher luôn chạy). Ba tình huống quan sát được khớp đúng cơ chế này:

1. **Launcher đang chạy ngầm, mở Demo kính từ nơi khác (vd launcher khác đang là mặc định):** hệ thống thấy Task launcher trùng affinity đang tồn tại → đưa Task đó lên trước. Người dùng chỉ thấy launcher hiện ra, không thấy "Demo kính".
2. **Launcher đã bị dừng hẳn (process bị kill), mở Demo kính:** không còn Task nào đang tồn tại mang affinity đó (Task bị dọn theo process) → hệ thống tạo Task mới cho `MainActivity` → chạy đúng như kỳ vọng.
3. **Bấm "Demo kính" ngay trong lưới của chính launcher:** Task của `HomeActivity` không chỉ tồn tại mà còn **đang là foreground** ngay lúc đó. Yêu cầu mở `MainActivity` (affinity trùng) bị hệ thống xử lý như "đưa Task đang có sẵn lên trước" — nhưng Task đó đã ở trước rồi, nên gần như không có gì xảy ra: không phản hồi.

## 6. Cách sửa và vì sao đây là lựa chọn đúng cho dự án

```xml
<activity
    android:name=".MainActivity"
    ...
    android:taskAffinity="" />
```

`taskAffinity=""` (chuỗi rỗng, không phải bỏ trống thuộc tính) là giá trị hợp lệ, khác với affinity mặc định (tên package) — Android coi nó là 1 affinity riêng biệt, không trùng bất kỳ Task nào khác của app. Nhờ vậy `MainActivity` luôn tạo Task mới của riêng nó khi mở bằng `NEW_TASK`, không bao giờ bị hệ thống "nhận nhầm" là Task của `HomeActivity`.

**Vì sao không sửa phía `HomeActivity`:** `singleTask` + `clearTaskOnLaunch` là hành vi cốt lõi, đúng đắn của 1 launcher thật (xử lý Back, không để lộ Activity dở dang khi quay về Home) — đổi 2 thuộc tính này rủi ro cao cho toàn bộ app, trong khi gốc rễ chỉ nằm ở việc 2 Activity vô tình cùng affinity.

**Vì sao đây chỉ là vấn đề tạm thời của dự án:** `MainActivity` (màn demo kính) được giữ lại tạm để đối chứng hiệu năng, dự kiến xóa sau M5 (xem `project_structure.md`). Trong app launcher hoàn chỉnh sau này sẽ không còn 2 Activity "LAUNCHER" cùng tồn tại trong 1 package như vậy, nên tình huống trùng affinity này sẽ không tái diễn tự nhiên — nhưng nếu dự án sau này thêm bất kỳ Activity thứ hai nào cũng khai `LAUNCHER`/mở độc lập (ví dụ màn cài đặt mở như 1 app riêng), bài học ở đây vẫn áp dụng: **luôn tự hỏi Activity mới có vô tình trùng affinity với Task launcher hay không.**

## 7. Bài học tổng quát (áp dụng ngoài case này)

- Bất cứ khi nào thấy "bấm mở app A thì app B hiện ra" hoặc "mở app chạy im lặng, không phản hồi" trên Android, nghi ngờ đầu tiên nên là **taskAffinity trùng + launchMode đặc biệt (singleTask/singleInstance) ở phía nào đó**, đặc biệt nếu 2 Activity đó **cùng một package**.
- `adb shell dumpsys activity activities` in ra toàn bộ Task hiện có kèm affinity — cách tra cứu nhanh khi nghi vấn tương tự lặp lại.
- Luôn khai `taskAffinity` rõ ràng (kể cả rỗng) cho bất kỳ Activity "launcher-like" thứ hai nào sống chung package với 1 Activity `singleTask`/`singleInstance`, thay vì dựa vào mặc định.
