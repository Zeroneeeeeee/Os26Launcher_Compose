# LiquidGlass for Android — Tài liệu sử dụng

> **JX (2026-11-20): thư viện này đã gỡ khỏi dự án** (cùng JitPack). Kính hiện nay là Backdrop (Kyant0) cho Compose, xem mục 12b. Phần còn lại giữ làm lịch sử.

> Nguồn: https://github.com/QWEA0/Liquid-Glass-Android (MIT, 196★, 90 commit, tác giả QWEA0)
> Bản dùng trong tài liệu này: `v2.0.11` (đã kiểm tra JitPack build trạng thái `ok`).
> Tài liệu này được tổng hợp từ README, `llms.txt` và `docs/LIQUID_GLASS_V2.md` của repo.
> Lưu ý: README có nhắc `AGENTS.md` nhưng file này **không tồn tại** (404).

---

## 1. Thư viện là gì

`LiquidGlassView` là một `FrameLayout` tự vẽ hiệu ứng kính lỏng kiểu iOS 26 phía sau các view con:

- Khúc xạ SDF thật (vòng nén ở rìa kính, bám theo bo góc và hình dạng)
- Tán sắc (viền quang phổ ở rìa)
- Specular theo cảm biến trọng lực (highlight di chuyển khi nghiêng máy)
- Tint tự thích ứng theo độ sáng nền
- Vật liệu Regular / Clear
- Liquid merge (hai khối kính hòa vào nhau), Liquid press (nhấn thì kính phồng lên)
- Blur luỹ tiến cho mép cuộn (`ScrollEdgeBlurView`)

**Hệ UI: Android View/XML, không phải Compose.** Chính tác giả khuyên: nếu app là Compose thuần thì dùng
[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), đừng bọc thư viện này trong `AndroidView`.

## 2. Yêu cầu và các mức pipeline

| Android | Pipeline | Có gì |
|---|---|---|
| API 33+ | **Lens 2.0** (AGSL một pass) | Toàn bộ hiệu ứng: SDF, tán sắc, specular, adaptive tint, merge, press |
| API 31–32 | Classic GPU | Blur + saturation |
| API 24–30 | Classic CPU (C++/NEON) | Blur + chromatic aberration + edge highlight |
| API 36+ | Bổ sung cho pipeline classic | Vibrancy phi tuyến, highlight một pass, tint theo từng pixel (tự động, không đổi API). Lens pipeline từ 33+ đã có sẵn bản tương đương |
| Bật "high contrast text" hoặc `FORCE_OPAQUE` | Nền đặc | Giống Reduce Transparency của iOS |

- Thuộc tính đánh dấu **33+** bị bỏ qua êm ở API thấp hơn (không crash, không cần `Build.VERSION`).
- AGSL biên dịch lỗi trên một số driver → tự động rơi vĩnh viễn về pipeline cũ.
- AAR chứa `.so` native cho `arm64-v8a` và `armeabi-v7a` (không có x86/x86_64).
- Thư viện compile với SDK 36, targetSdk 35. `appcompat` và `material` là `compileOnly` (chỉ cần nếu dùng `LiquidGlassDialogBuilder`).

## 3. Cài đặt (JitPack)

`settings.gradle.kts` — thêm JitPack vào `dependencyResolutionManagement`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.QWEA0:liquidglass:v2.0.11")
}
```

- Tọa độ đúng: `com.github.QWEA0:liquidglass` (alias `com.github.QWEA0:Liquid-Glass-Android` cũng chạy).
- Dạng `com.github.QWEA0.Liquid-Glass-Android:liquidglass` **không tồn tại**.
- Package của class thư viện là `com.example.liquidglass` (không phải `com.qwea0.*`).

> Dự án Os26Launcher (`E:\SourceTree\IosLauncher`) đặt `repositoriesMode = FAIL_ON_PROJECT_REPOS`, nên JitPack **bắt buộc** khai báo trong `settings.gradle.kts`, không được khai báo trong `build.gradle.kts` của module.
>
> Hiện trạng: JitPack và dependency `v2.0.11` đã có trong dự án. `appcompat`, `material`, `viewpager2`, `constraintlayout` cũng đã có, nên các widget cần chúng (`LiquidGlassDialogBuilder`, `LiquidGlassTabLayoutMediator`) dùng được ngay mà không phải thêm gì.

## 4. Nguyên lý quan trọng nhất

Kính **chụp lại những gì được vẽ phía sau nó** trong view cha, rồi xử lý. Hệ quả:

1. Nền phải là view **anh em đứng trước** kính trong một parent chồng lớp (thường là `FrameLayout`).
2. `enableDynamicBackground` mặc định `false` — nền chỉ chụp một lần. **Nền hoặc kính di chuyển thì phải đặt `true`**, nếu không kính trông như đóng băng. Đây là lỗi tích hợp số 1.
3. Nền phẳng hoặc gradient mượt thì **không thấy khúc xạ và tán sắc**. Cần chi tiết tần số cao (lưới icon, chữ, ảnh) để test.
4. Mặc định nguồn nền là parent trực tiếp. Dùng `backdropSource` để chụp view ở chỗ khác trong cây (xem mục 8).

## 5. Ví dụ tối thiểu

```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- Nền: phải có chi tiết -->
    <ImageView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="centerCrop"
        android:src="@drawable/your_background" />

    <com.example.liquidglass.LiquidGlassView
        android:id="@+id/glass"
        android:layout_width="280dp"
        android:layout_height="96dp"
        android:layout_gravity="center"
        app:cornerRadius="999dp"
        app:glassMaterial="regular"
        app:refractionHeight="12dp"
        app:bevelWidth="24dp"
        app:dispersionStrength="0.12"
        app:sensorHighlight="true"
        app:adaptiveTint="true">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:text="Liquid Glass"
            android:textColor="#FFFFFF"
            android:textSize="20sp" />
    </com.example.liquidglass.LiquidGlassView>
</FrameLayout>
```

```kotlin
val glass = findViewById<LiquidGlassView>(R.id.glass)
glass.enableDynamicBackground = true   // BẮT BUỘC nếu nền hoặc kính di chuyển
```

## 6. Chỉnh trong code

```kotlin
// Lens 2.0 (API 33+, thấp hơn thì bị bỏ qua)
glass.material = GlassMaterial.REGULAR     // REGULAR | CLEAR
glass.refractionHeight = 160f              // px, 0–300
glass.bevelWidth = 48f                     // px, 2–200
glass.refractionFalloff = 2f               // 0–4, càng cao vòng nén càng mảnh
glass.dispersionStrength = 0.10f           // 0–1, >0.25 trông như cầu vồng
glass.enableSensorHighlight = true
glass.enableAdaptiveTint = true

// Kính màu (mọi API)
glass.glassTint = 0x4C0A84FF               // ARGB, alpha = độ đậm
glass.setGlassTint(Color.MAGENTA, 0.25f)

// Đổi màu chữ khi nền sáng/tối
glass.glassAppearanceListener = { isOverLight ->
    label.setTextColor(if (isOverLight) Color.BLACK else Color.WHITE)
}

// Liquid merge (API 33+)
glass.setPrimaryShape(dockRect, cornerRadiusPx = 36f)
glass.setSecondaryShape(bubbleRect, cornerRadiusPx = 44f, smoothing = 40f)

// Classic pipeline
glass.blurAmount = 0.0625f
glass.saturation = 140f
glass.aberrationIntensity = 2f
glass.blurMethod = BlurMethod.SMART
```

## 7. Bảng thuộc tính của `LiquidGlassView`

| Thuộc tính | Kiểu | Mặc định | Ghi chú |
|---|---|---|---|
| `enableDynamicBackground` | Boolean | `false` | Đặt `true` nếu nền/kính di chuyển |
| `cornerRadius` | Float (px) | `999f` | `999f` = hình viên thuốc |
| `material` | `GlassMaterial` | `REGULAR` | 33+. `CLEAR` dùng trên media |
| `refractionHeight` | Float | `160f` | 33+. 0–300 px |
| `bevelWidth` | Float | `48f` | 33+. 2–200 px, độ "dày" của kính |
| `refractionFalloff` | Float | `2f` | 33+. 0–4. 0 = vát phẳng kiểu cũ |
| `refractionNoFold` | Boolean | `false` | 33+. `true` thì không gập, rìa chỉ kéo giãn |
| `refractionOutward` | Boolean | `false` | 33+. Chế độ thấu kính lồi (mặc định lấy mẫu vào trong, giống iOS) |
| `adaptiveLensScale` | Boolean | `true` | 33+. Tự giới hạn theo cạnh ngắn để nút nhỏ không toàn là viền |
| `dispersionStrength` | Float | `0.10f` | 33+. 0–1 |
| `enableSensorHighlight` | Boolean | `false` | 33+. Highlight theo cảm biến |
| `enableAdaptiveTint` | Boolean | `false` | 33+. Tint theo độ sáng nền |
| `glassTint` | Int ARGB | trong suốt | Mọi API |
| `useShaderPipeline` | Boolean | `true` | `false` = ép pipeline classic |
| `useHardwareBlurWhenPossible` | Boolean | `true` | `false` tắt luôn lens pipeline |
| `debugApiLevelCap` | Int | `MAX_VALUE` | Chỉ để debug, giả lập tầng API thấp |
| `effectiveApiLevel` | Int | — | Chỉ đọc: tầng pipeline đang dùng |
| `blurAmount` | Float | `0.0625f` | 0–1, tỉ lệ theo kích thước view |
| `saturation` | Float | `140f` | 100 = giữ nguyên |
| `aberrationIntensity` | Float | `2f` | Classic |
| `displacementScale` | Float | `70f` | Classic |
| `elasticity` | Float | `0.15f` | Độ co giãn khi chạm |
| `enablePressEffect` | Boolean | `true` | Hiệu ứng nhấn |
| `pressScale` | Float | `0.95f` | 0.5–1.5. >1 phồng lên như iOS 26. Có thể tràn ra ngoài, cần `clipChildren="false"` ở parent |
| `blurMethod` | `BlurMethod` | `SMART` | Xem enum |
| `accessibilityMode` | `GlassAccessibilityMode` | `AUTO` | Tôn trọng high-contrast, bỏ animation, tiết kiệm pin |
| `downsampleScale` | Int | `2` | Cao hơn = nhanh hơn, mềm hơn |
| `collectFrameStats` | Boolean | `true` | Tắt ở production |
| `glassAppearanceListener` | `((Boolean)->Unit)?` | `null` | Gọi khi nền đổi sáng/tối |
| `backdropSource` | `View?` | `null` | `null` = parent trực tiếp |

**XML attrs (namespace `app`)**: `displacementScale`, `blurAmount`, `saturation`, `aberrationIntensity`, `elasticity`, `cornerRadius`, `glassMaterial` (`regular|clear`), `bevelWidth`, `refractionHeight`, `refractionFalloff`, `refractionNoFold`, `refractionOutward`, `adaptiveLensScale`, `dispersionStrength`, `sensorHighlight`, `adaptiveTint`, `glassTint`, `glassTintStrength` (0–1, ghi đè alpha của `glassTint`), `backdropSourceId`.

**Enum (đúng chính tả, sai là không build được):**

| Enum | Giá trị |
|---|---|
| `BlurMethod` | `BOX_BLUR`, `BOX_BLUR_CPP`, `IIR_GAUSSIAN`, `IIR_GAUSSIAN_NEON`, `SMART`, `DOWNSAMPLE` |
| `GlassMaterial` | `REGULAR`, `CLEAR` |
| `GlassAccessibilityMode` | `AUTO`, `FORCE_FULL`, `FORCE_OPAQUE` |
| `ScrollEdgeBlurView.Edge` | `TOP`, `BOTTOM` |

**Methods:** `setPrimaryShape(rect, cornerRadiusPx)`, `setSecondaryShape(rect, cornerRadiusPx, smoothing)`, `setGlassTint(color, strength)`, `setBackdropSource(view)`, `setCustomBackdropCapture { rect -> Bitmap? }` (ép về CPU pipeline, nên tránh), `refreshAccessibilityState()`.

## 8. Chọn nền: `backdropSource`

- **Mặc định**: nền là parent trực tiếp. Kính phải nằm sau nền trong cùng parent.
- **`backdropSource = viewKhac`**: chụp view bất kỳ trong cây, vùng chồng lấn tính theo toạ độ màn hình, vẫn giữ GPU pipeline, cuộn trong nguồn tự làm kính vẽ lại. Nguồn không được là chính kính hay con của kính. Nếu nguồn là tổ tiên của kính, nhánh chứa kính được chụp cuối cùng.
- **Kính trên `ScrollView`/`RecyclerView`**: cho cùng vào một `FrameLayout` (kính khai báo sau) và `enableDynamicBackground = true`, hoặc đặt `backdropSource` trỏ vào view cuộn.
- **Kính trong `Dialog`/`BottomSheetDialog`/`PopupWindow`**: `backdropSource = activity.findViewById(android.R.id.content)` (bắt buộc). Không dùng `glass.apply { backdropSource = findViewById(...) }` vì `findViewById` bị gọi trên chính kính và trả `null` ngầm. Tắt animation của window (`window.setWindowAnimations(0)`), xoá các lớp nền của window, `dimAmount = 0`.
- **Có `WebView` trong nền**: `webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)` để WebView không vẽ lại mỗi frame.

## 9. Widget dựng sẵn (đều kế thừa `LiquidGlassView`)

| Widget | Dùng cho |
|---|---|
| `LiquidGlassButton` | Nút viên thuốc, `text`, `setTextColor`, `setOnClickListener` |
| `LiquidGlassFab` | Nút tròn 56dp có icon (`setIconResource`, `setIconTint`) |
| `LiquidGlassTabBar` | Tab bar kiểu iOS 26, chỉ báo là giọt kính thật, kéo được. `setTabs(...)`, `selectedIndex`, `onTabSelected` |
| `LiquidGlassTabLayout` + `LiquidGlassTabLayoutMediator` | Dải tab kiểu Material, đồng bộ ViewPager2 (app tự thêm `androidx.viewpager2`) |
| `LiquidGlassChip` + `LiquidGlassChipGroup` | Chip lọc/nhập, nhóm chọn đơn/đa |
| `LiquidGlassListItem` + `LiquidGlassListGroup` | Danh sách M3, `MERGED` hoặc `SEPARATED`. Mỗi dòng là một `LiquidGlassView`, **dùng cho trang cài đặt/menu, không dùng cho feed dài** |
| `LiquidGlassToast` | Toast kính trong window riêng, nằm trên dialog. Cần Activity context |
| `LiquidGlassDialogBuilder` | `MaterialAlertDialogBuilder` bọc panel bằng kính. Cần app tự thêm appcompat + material |
| `ScrollEdgeBlurView` | Blur luỹ tiến ở mép cuộn: `edge`, `maxBlurRadius`, `bindScrollView(view)` |

Khi `enableAdaptiveTint = true`, chữ/icon của button, FAB, tab bar tự đổi sáng/tối theo nền cho tới khi bạn đặt màu cụ thể.

## 9b. Kiến trúc nội bộ (để hiểu khi debug)

- `LiquidGlassView`: view chính, xử lý chạm
- `GlassLensRenderer`: AGSL một pass (SDF → khúc xạ → tán sắc → specular → tint)
- `GlassRuntimeEffects`: AGSL color filter / blend (API 36+)
- `LightSourceController`: cảm biến trọng lực → hướng sáng
- `BackdropLuminanceMeter`: lấy mẫu độ sáng nền (mỗi 350 ms, 1000 ms khi tiết kiệm pin)
- `GlassAccessibility`: high-contrast, giảm động, tiết kiệm pin
- `AsyncRenderer`, `EnhancedBlurEffect`, `ChromaticAberrationEffect`, `EdgeHighlightEffect`: pipeline classic
- Native: `gauss_iir_neon.cpp`, `chromatic_aberration.cpp`, `boxblur.cpp`

Quy tắc đáng nhớ: **kính không khúc xạ kính khác.** Khi một kính đang nằm trong vùng chụp của kính khác, các kính còn lại bị ẩn tạm để tránh lồng đệ quy.

## 10. Hiệu năng (theo tác giả)

- Lens pipeline: khoảng 0.06 ms CPU ghi lệnh mỗi frame, 60 fps trên thiết bị thật, không cấp phát bitmap, không đọc ngược pixel.
- Classic pipeline nặng hơn: chỉnh `downsampleScale`, `blurMethod`.
- Đây là số liệu do tác giả tự đo, **cần tự đo lại trên máy mục tiêu** (nhất là khi có nhiều kính trên một màn hình).

## 11. Xử lý sự cố

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| Không thấy hiệu ứng | `enableDynamicBackground` chưa `true`; sau kính không có gì để chụp; nền quá phẳng/mượt |
| Kính đứng im khi nền chạy | Như trên (nguyên nhân số 1) |
| Kính đen hoàn toàn | Trong Dialog/popup mà chưa đặt `backdropSource` |
| Khúc xạ yếu/không có | Máy dưới Android 13 (API 33) — chỉ chạy classic pipeline |
| Lỗi liên quan thư viện native (`UnsatisfiedLinkError`) | Chưa xác minh: AAR chỉ có `.so` cho arm, nên emulator x86/x86_64 có thể gặp lỗi này. Nên thử trên máy thật hoặc emulator arm64 |
| Build lỗi không tìm thấy artifact | Quên thêm JitPack trong `settings.gradle.kts`, hoặc sai tọa độ |
| Build lỗi enum | Sai tên hằng (xem bảng enum) |
| Nút ở sát mép bị cắt khi nhấn | `pressScale` > 1 → `clipChildren="false"` ở parent |

## 12. Cân nhắc riêng cho Os26Launcher

Đây là các điểm cần kiểm chứng trước khi cam kết dùng thư viện cho launcher:

1. **Hệ UI: đã chốt View/XML.** Dự án đã chuyển sang `AppCompatActivity` + XML + theme `Theme.Material3.DayNight.NoActionBar`, khớp với thư viện (View-based), không còn vấn đề Compose/`AndroidView`. Hai lưu ý khi dùng XML:
   - Layout chứa kính nên dùng `FrameLayout` (hoặc parent chồng lớp khác) với nền khai báo **trước** kính. Layout mặc định của template là `ConstraintLayout` với id `main` và đặt padding theo system bars lên root. Padding đó làm nền co lại, nên với màn hình có kính hãy bỏ padding trên root và chỉ áp insets cho các view cần tránh thanh hệ thống.
   - View tùy biến (như `DemoBackdropView`) phải có constructor nhận `AttributeSet` mới inflate được từ XML.
2. **Wallpaper hệ thống không nằm trong cây View của app.** Kính chỉ chụp được thứ nằm trong window của app. Muốn khúc xạ hình nền thật thì phải tự đưa ảnh nền vào cây View (ví dụ một `ImageView` đặt sau, rồi dùng nó làm `backdropSource`). Cách lấy ảnh wallpaper và quyền cần thiết cần thử riêng ở bước sau.
3. **Số lượng kính trên một màn hình.** Mỗi `LiquidGlassView` có một lần chụp nền riêng. Dock, thanh tìm kiếm, thư mục, widget… cộng lại cần đo FPS thực tế.
4. **minSdk 26 của dự án.** Máy Android 8–12 chỉ có classic pipeline (blur + aberration), không có khúc xạ SDF. Hiệu ứng "đúng chất iOS 26" chỉ có từ Android 13 trở lên.
5. **Độ trưởng thành.** Thư viện còn trẻ, một tác giả, có 4 issue mở. API có thể thay đổi giữa các bản. Nên ghim đúng `v2.0.11`.
6. **Giấy phép.** MIT, dùng thương mại được, giữ lại thông báo bản quyền.

## 12b. Backdrop (Kyant0) — kính cho Compose (JC, 2026-11-19)

Chủ dự án chọn thư viện này cho kính khi chuyển UI sang Compose (hướng B). Chi tiết và số đo: [knowledgements/JC-compose-pilot.md](knowledgements/JC-compose-pilot.md).

- **Toạ độ:** `io.github.kyant0:backdrop:2.0.1` (Maven Central, Apache-2.0, repo `Kyant0/AndroidLiquidGlass`, docs `kyant.gitbook.io/backdrop`). Build bằng Kotlin 2.4.10 nên dự án phải nâng KGP lên 2.4.10 (đọc metadata, đo được). Kéo theo `io.github.kyant0:shapes:1.2.1` và các gói `org.jetbrains.compose.*-android` 1.12.0; các gói này chỉ là vỏ ~6 KB trỏ sang `androidx.compose.*`, không trùng code (đo được).
- **Chỉ Compose.** Bản 2.0.0 bỏ phần Android View. Kính chỉ khúc xạ được thứ vẽ trong cùng cây Compose: Home phải là Compose thì dock/thư mục/pill mới dùng được Backdrop.
- **API:** `rememberLayerBackdrop()` + `Modifier.layerBackdrop(b)` trên vùng nền; `Modifier.drawBackdrop(b, shape = { … }, effects = { vibrancy(); blur(r); lens(h, a) }, highlight, shadow, onDrawSurface)` trên bề mặt kính.
- **Mức theo API (đọc mã nguồn):** `blur` từ API 31, `lens`/`gammaAdjustment` từ API 33, thư viện tự bỏ qua. Dưới API 31 `drawBackdrop` vẫn vẽ lại nền nhưng không hiệu ứng → dự án rẽ sang `DsSurfaceDrawable` (`core/glass/compose/DsGlass.kt`).
- **Bẫy đã gặp (đo được):**
  - `layerBackdrop` ghi `drawContent()` ⇒ nền phải đứng **sau** nó trong chuỗi modifier; viết ngược thì kính không vẽ gì.
  - Lambda `shape`/`effects` bị node giữ từ lần dựng đầu ⇒ giá trị đổi theo cỡ (bán kính) phải để trong state và đọc trong lambda; nếu không kính vẽ góc vuông.
  - Shape phải là `CornerBasedShape` (lens ném lỗi), độ cao khúc xạ ≤ bán kính góc.
  - Nền mượt (aurora) làm blur/lens gần như vô hình; muốn chứng minh kính đang vẽ thì tạm thêm `colorControls(brightness = 0.5f)`.

| | QWEA0 `liquidglass` | Kyant0 Backdrop |
|---|---|---|
| Hệ UI | View (`LiquidGlassView`) | Compose (`Modifier`) |
| Android 8–11 | Blur CPU + quang sai | Không hiệu ứng (dự án tự vẽ bề mặt tĩnh) |
| Android 12 | Blur GPU | Blur |
| Android 13+ | Lens SDF đầy đủ | Lens |
| Native `.so` | Có (arm64, armv7) | Không |
| Giấy phép | MIT | Apache-2.0 |
| Trong dự án | Home, dock, thư mục, pill, Control Center | Màn 1.1, probe Home (debug) |

## 13. Liên kết

- Repo: https://github.com/QWEA0/Liquid-Glass-Android
- README đầy đủ: https://github.com/QWEA0/Liquid-Glass-Android/blob/main/README.md
- Chi tiết lens pipeline: https://github.com/QWEA0/Liquid-Glass-Android/blob/main/docs/LIQUID_GLASS_V2.md
- Digest cho AI: https://github.com/QWEA0/Liquid-Glass-Android/blob/main/llms.txt
- JitPack: https://jitpack.io/#QWEA0/Liquid-Glass-Android
- APK demo của tác giả (5 MB, API 24+): https://github.com/QWEA0/Liquid-Glass-Android/releases/latest
- Thư viện cho Compose (nếu đổi hướng): https://github.com/Kyant0/AndroidLiquidGlass

## Ghi chú đọc `javap` v2.0.11 (GL, 2026-11-04)

- `debugApiLevelCap` là thuộc tính của TỪNG `LiquidGlassView`, không toàn cục.
- Đường Lens: bán kính mờ = (4 + `blurAmount` x 32) x `material.blurScale`; `edgeSoftness` > 0 làm shader lấy 9 mẫu thay vì 3 ở vành khúc xạ; dispersion chỉ đổi lượng khúc xạ từng kênh, không đổi số mẫu.
- Kính lấy nền ở MỖI lần vẽ; `enableDynamicBackground = true` gọi `invalidate()` sau mỗi lần vẽ (vòng liên tục).
- `LiquidGlassView` tự `invalidate()` trên mọi `onScrollChanged` của cửa sổ, bất kể `enableDynamicBackground` (TL1).
