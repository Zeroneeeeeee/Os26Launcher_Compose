# Launcher OS: theme Liquid Glass hoạt động thế nào (phân tích ngược bằng REA)

- **Đối tượng:** `com.babydola.launcherios` 7.0.1 (versionCode 384), cài từ Play Store trên SM-A176B. Đã kéo `base.apk` và 2 split về máy, md5 khớp với bản trên điện thoại.
- **APK sha256:** `036c408bf7a9ae0e4982511028dba53efdbe876911063bca6606fed2f0fed271`
- **Cách làm:** chỉ phân tích tĩnh, gồm REA (JADX 0.7.1), decompile JADX cục bộ một số package, và quét chuỗi trong dex. Không chạy app, không gọi server theme.
- **Ký hiệu:** **[O]** là thấy trực tiếp trong code đã decompile. **[I]** là suy luận, chưa xác minh.

---

## 1. Tóm tắt

1. **Có hai công tắc độc lập:**
   - **Glass toàn cục**, tức kiểu "OS 26". Đây là pref `pref_use_glass_concept`, chỉ có hiệu lực trên **API ≥ 33**. Khi bật, dock, folder, thanh search, nút edit-mode… đều có tấm kính. **[O]**
   - **Cờ blur của từng theme**, gồm `isBlur` trong JSON của icon pack và `is_blur` trên từng widget của theme. Cờ này quyết định icon và widget nào được đặt lên tấm kính. **[O]**
2. **Bubble Kiss không nằm trong APK.** Không có chuỗi "Bubble" hay "Kiss" trong dex, `resources.arsc` hay assets, nên đây là theme tải từ server. **[O]** Hiệu ứng icon và widget có nền kính gần như chắc chắn đến từ `icon_pack.config.isBlur = true` cộng với `widgets[].is_blur = true`. **[I]**
3. **Kính của icon được vẽ lúc runtime, không nằm sẵn trong PNG.** `BubbleTextView` chèn một `LiquidView` (vuông, bo góc 25%, đúng bằng kích thước icon) vào child index 0, rồi vẽ ảnh icon nguyên vẹn lên trên. **[O]**
4. **Kính không phải blur trực tiếp của màn hình.** Nguồn là **ảnh wallpaper chụp một lần**, thu nhỏ còn 0,12 lần rồi stack-blur bán kính 4 (bằng native). Mỗi view tự cắt đúng vùng nằm dưới nó bằng `getLocationOnScreen`. Trên API 33+ có thêm một **AGSL RuntimeShader "Refraction"** bẻ cong viền theo SDF, và một **viền SweepGradient 2 dp** làm highlight. **[O]**

---

## 2. Luồng theme: từ lúc chọn đến lúc vẽ

```
Theme store (ThemePackActivity / ThemePreviewActivity)
   │ ThemeResponse từ server (ThemeApi/ThemeApiV2), cache trong Room ThemeCacheDatabase
   ▼
ThemeApplyActivity ── ContentResolver.insert("content://com.babydola.launcherios.theme.provider/<path>")
   ▼
ThemeProvider.insert
   ├─ icon_pack            → lưu JSON icon pack vào DB icon (model w17, có isBlur)
   ├─ default_icon_pack_id → pref reference_default_icon_id = id
   │                          PENDING_APPLY_ICON_PACK = "<id>|<withWallpaper>"
   ├─ wallpaper_pack       → lưu wallpaper
   └─ theme_widget         → thêm widget 2x2 / 4x2 / 4x4 vào workspace
   ▼
SharedPreferences "com.android.launcher3.prefs"
   │ Launcher đăng ký OnSharedPreferenceChangeListener, nghe key PENDING_APPLY_ICON_PACK
   ▼
  "concept_changed" → huỷ LauncherAppState → Activity.recreate()
  "<id>|<bool>"     → dialog "applying icon pack" → nạp lại icon
   ▼
Render
  BubbleTextView : glass && (workspace | dock) && iconPack.isBlur → LiquidView phía sau icon
  ThemeWidgetView: widget.isBlur → LiquidWallpaperView (index 0, MATCH_PARENT)
  Hotseat/Folder : glass → LiquidView + HighlightLiquidView, không phụ thuộc theme
```

- Không có broadcast hay LiveData. Toàn bộ cơ chế là **pref + listener**, sau đó recreate hoặc reload. **[O]**
- Cờ glass được đọc **một lần** khi khởi tạo `LauncherAppState`, nên khi đổi cờ thì bắt buộc phải recreate Activity. **[O]**

### Dữ liệu model liên quan **[O]**

| Model | Trường liên quan đến kính |
|---|---|
| `ThemeResponse` | Không có cờ glass. Chỉ chứa `icon_pack`, `wallpaper`, `widgets[]`, `widget_group_id` |
| Icon pack `config` (JSON) | `isBlur` (tấm kính sau icon), `isTransparentMockup`, kèm `background{fill, stroke, shadow, radius, shape…}`, `foreground{…}`, `glossyLight`, `overlay` |
| `WidgetResponse` | `is_blur`, `corner_radius`, `widget_size`, `widget_type`, `config`. Room table `widget(serverId, widgetType, widgetSize, isBlur, cornerRadius)` |

### Pref và remote config **[O]**

| Key | Ý nghĩa |
|---|---|
| `pref_use_glass_concept` | Bật/tắt glass toàn cục. Nếu chưa có giá trị: remote `os_version` 18 cho off, 26 cho on, còn lại lấy `!use_os_18_for_default`, tức **mặc định ON** |
| `PENDING_APPLY_ICON_PACK` | Kích hoạt reload, giá trị là `concept_changed[_force]` hoặc `id\|withWallpaper` |
| `reference_default_icon_id` | Icon pack đang dùng, nơi chứa `isBlur` |
| `pref_theme_widget_id_<appWidgetId>` | Ánh xạ widget host sang widget của theme |

Màn hình bật/tắt là `HomeScreenStyleActivity`, gồm hai lựa chọn OS 18 và OS 26.

---

## 3. Pipeline vẽ kính

### Bước A: tạo bitmap wallpaper (`WallpaperHelper`) **[O]**
1. Gọi `WallpaperManager.getDrawable()` (hoặc decode file wallpaper riêng của app), scale về đúng kích thước màn hình, rồi scale tiếp còn **0,12 lần**.
2. Sao chép bitmap và chạy `NativeBlur.nativeStackBlur` hai lượt, ngang rồi dọc, **bán kính 4 px trên ảnh 0,12 lần**. Quy ra độ phân giải gốc khoảng 33 px. **[I]**
3. Phát cho listener (`WallpaperChangedListener`): `g(sharp)` nhận ảnh nét, `A(blurred)` nhận ảnh đã blur. Hotseat, BubbleTextView và ThemeWidgetView đăng ký trong `onAttachedToWindow`.

### Bước B: `LiquidView` cắt vùng và gắn shader **[O]**
- **Cắt vùng:** lấy `getLocationOnScreen`, nhân với scale tích luỹ của các view cha, rồi nhân 0,12 để ra rect nguồn trên bitmap nhỏ. Sau đó `drawBitmap(src → dst)`. Việc phóng từ 0,12 lên kích thước thật làm ảnh mềm thêm.
- **Đồng bộ khi cuộn:** mỗi lần draw đều so vị trí, kích thước và scale với lần trước. Nếu có gì thay đổi thì tạo lại `RenderEffect`.
- **Trên API 33+:** `setRenderEffect(createRuntimeShaderEffect(Refraction))`, có thể nối chain với ColorFilter.
- **Dưới API 33 hoặc canvas phần mềm:** clip rounded rect, rồi vẽ bitmap có tint.

### Bước C: shader "Refraction" (mô tả thuật toán) **[O]**
- **Uniform:** `size`, `offset`, `cornerRadii`, `refractionHeight`, `refractionAmount`, `depthEffect`.
- **Cách tính:**
  1. Tính SDF của rounded rect.
  2. Pixel nằm sâu hơn `refractionHeight` tính từ mép thì giữ nguyên.
  3. Trong dải sát mép, độ dịch là `circleMap(1 − depth/refractionHeight) × refractionAmount`, với `circleMap(x) = 1 − sqrt(1 − x²)`, tức một cung tròn.
  4. Hướng dịch là pháp tuyến của SDF, khi bật `depthEffect` thì cộng thêm hướng toả từ tâm.
- Vì `refractionAmount` được truyền **giá trị âm**, mép kính kéo nội dung từ phía trong ra, tạo cảm giác viền kính cong như kính lúp.
- **Không có** chromatic aberration hay specular trong shader. Blur đến từ bitmap đã blur sẵn, highlight là một view riêng.

### Bước D: viền highlight (`HighlightLiquidView` và `lx`) **[O]**
- **Nét vẽ:** STROKE dày 2 dp (bản legacy 1 dp), màu paint `0x80FFFFFF`, vẽ bằng `drawRoundRect`.
- **SweepGradient:** có matrix scale theo tỉ lệ khung hình, gồm 8 stop:
  - màu: `0D, 0D, BF, 0D, 0D, FF, 0D, 0D` (alpha của trắng)
  - vị trí: `0.01, 0.02, 0.13, 0.23, 0.38, 0.63, 0.88, 0.99`
  - Kết quả là hai vệt sáng chéo: một vệt 100% ở góc trên-trái và một vệt 75% ở góc dưới-phải. **[I] về hướng**
- **`lx`** có thêm thuộc tính animate `edge_width` và `angularRotation`.

### Tham số theo từng loại view **[O]**

| View | blur RenderEffect | Bo góc | refractionAmount / Height |
|---|---|---|---|
| Tấm kính sau icon (BubbleTextView) | 0 | 25% kích thước icon | 0,5 / 0,2 (× cạnh ngắn) |
| Dock (Hotseat) | 25 | 35% cạnh ngắn | 0,5 / 0,2 |
| Glass*Layout (nút, sheet) | 0 | theo `za1` | 0,1 / 0,1 |
| Widget theme | — | `cornerRadius` của widget | (không trích được) |

### Các view dùng kính **[O]**
- **Icon workspace và dock:** `LiquidView`, chỉ khi `isBlur`. Icon trong drawer và trong folder **không** có kính.
- **Widget theme:** `LiquidWallpaperView`, khi `is_blur`. Các widget built-in (Battery, AI…) kế thừa `u33` và kiểm tra `getUseLiquidGlass()`.
- **Dock, folder icon, search bar, App Library:** luôn có kính khi glass mode bật.
- **`GlassFrameLayout`:** chỉ bọc các thành phần khung giao diện như nút edit-mode, nút ở zero page, widget picker sheet và frame quảng cáo. **Không** dùng cho icon hay widget.

---

## 4. Áp dụng cho Os26Launcher

1. **Hai cờ:**
   - `glassMode` toàn cục, đọc lúc khởi động. Khi đổi thì recreate Activity, hoặc nếu ta dùng state reactive thì vẽ lại.
   - `isBlur` theo từng icon style hoặc widget, quyết định item nào có tấm kính.
2. **Nguồn backdrop:** đọc `WallpaperManager.getDrawable()` (cần quyền đọc wallpaper trên API 33+), downscale 0,12 lần rồi blur **một lần**, dùng chung cho mọi tấm kính. Cách này giải luôn vấn đề "wallpaper không nằm trong View tree" ở §12 của `LiquidGlass-Android.md`, và rẻ hơn nhiều so với capture live.
3. **Icon:** đặt tấm kính bo 25% làm child index 0 của ô icon, căn giữa, kích thước bằng icon. Nó vẽ đúng vùng wallpaper đã blur theo `getLocationOnScreen` và cập nhật khi trang cuộn. Ảnh icon vẽ lên trên.
4. **Widget (app chưa có):** container widget có tấm kính MATCH_PARENT ở index 0, bo góc lấy từ dữ liệu widget, nội dung widget vẽ lên trên.
5. **Hiệu ứng:** dùng shader refraction viền theo SDF và stroke SweepGradient 8 stop như trên. Lens 2.0 hiện tại của ta đã có refraction, dispersion và specular nên giàu hơn. Có thể giữ Lens cho dock và dùng bản "bitmap tĩnh + refraction viền" rẻ hơn cho hàng chục icon cùng lúc.

---

## 5. Chưa xác định

- JSON thật của Bubble Kiss, vì theme nằm trên server và không gọi mạng.
- Giá trị màu `default_dim_color` và `hotseat_blur_color_filter`, vì apktool chưa cài nên chưa decode được resource.
- Tên child shader bị obfuscate, giả định là `content`.
- Hàm `ej7.h`/`w7b.u` có phải đổi dp → px hay không.
- Thứ tự các bước trong ViewModel của `ThemeApplyActivity`.
- Pipeline biến icon pack JSON/zip thành bitmap icon.
- Phần native `nativeStackBlur` (lib trong split arm64).
- Các class `LiquidGlassWallpaperView`, `GlassBlurWallpaperView`, và đường dưới API 33 của `CustomIosLiquidGlassView` (`wn5`) chưa được decompile.
