import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
}

// G3e: khóa OpenWeatherMap đọc từ local.properties (gitignore) hoặc biến môi trường OWM_API_KEY; thiếu thì rỗng (widget báo "chưa cấu hình khóa").
// Đọc qua providers.fileContents để tương thích configuration cache. KHÔNG in giá trị này ra log/console.
val owmApiKey: String = run {
    val fromFile = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.map { text -> Properties().apply { load(text.reader()) }.getProperty("OWM_API_KEY", "") }
        .orElse("").get().trim()
    fromFile.ifEmpty { providers.environmentVariable("OWM_API_KEY").orElse("").get().trim() }
}

android {
    namespace = "gambi.global.os26launcher"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "gambi.global.os26launcher"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "OWM_API_KEY", "\"" + owmApiKey.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
        // PG4: cong tac do hieu nang (core/perf/PerfSwitches.kt), chi bat o build type `perf`.
        buildConfigField("boolean", "PERF_SWITCHES", "false")
    }

    buildTypes {
        release {
            // RB: R8 bat (AGP 9.3.3: optimization.enable = code shrinking + resource shrinking + optimize, dung R8).
            // Keep rule nam o src/main/keepRules/rules.keep; baseline profile o src/main/baseline-prof.txt.
            optimization {
                enable = true
            }
        }
        // PG4 (chu du an 2026-11-23, Q1): ban do hieu nang = release (R8 + baseline profile), ky bang khoa debug de cai duoc, khong debuggable;
        // chi ban nay doc cong tac do `adb shell setprop debug.os26.perf ...`. Build: .\gradlew.bat assemblePerf installPerf
        create("perf") {
            initWith(getByName("release"))
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "PERF_SWITCHES", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true // JC: Compose song song voi View/XML
    }
    // LG: app có bộ chọn ngôn ngữ riêng, nên mọi bản dịch phải nằm trong APK gốc. Để split theo ngôn ngữ thì Play chỉ giao chuỗi
    // của ngôn ngữ máy: chọn ngôn ngữ khác trong app vẫn hiện tiếng Anh (plan LG, L9).
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric) // HL-2: khóa Ứng dụng ẩn
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.dynamicanimation)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // JC: Compose (khong dung material3) + kinh Backdrop
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kyant.backdrop)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // JH1: unit test JVM (planner bo cuc, GridMetrics); khong test UI
    testImplementation(libs.junit)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}


// WP store guard: the iOS 27 test wallpaper (Apple, unlicensed) lives in src/main/res so release APKs can be tested,
// but it must NEVER reach the store. bundleRelease (the store AAB) fails while the files exist.
// assembleDebug and assembleRelease are not affected. Configuration-cache safe: only declared file inputs, no Project access at run time.
abstract class CheckNoStoreTestWallpaper : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val testWallpapers: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val present = testWallpapers.files.filter { it.exists() }
        if (present.isNotEmpty()) {
            throw GradleException(
                "Remove the Apple iOS 27 test wallpaper before a store build: " +
                    "app/src/main/res/drawable*-nodpi/default_wallpaper_ios27.jpg " +
                    "(found: " + present.joinToString { it.parentFile.name + "/" + it.name } + "). " +
                    "Also set DefaultBackdropSource.isAvailable() to false and drop its R.drawable.default_wallpaper_ios27 references. "
            )
        }
    }
}

val checkNoStoreTestWallpaper = tasks.register<CheckNoStoreTestWallpaper>("checkNoStoreTestWallpaper") {
    group = "verification"
    description = "Fails while the Apple iOS 27 test wallpaper is still in src/main/res (store builds only)."
    testWallpapers.from(
        layout.projectDirectory.file("src/main/res/drawable-nodpi/default_wallpaper_ios27.jpg"),
        layout.projectDirectory.file("src/main/res/drawable-night-nodpi/default_wallpaper_ios27.jpg")
    )
    // WI (2026-11-20): the 5 Apple preset wallpapers were replaced by the design's own wallpapers (wp_*), no longer guarded here.
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    dependsOn(checkNoStoreTestWallpaper)
}

// AB store guard: the settings "Contact us" group ships with PLACEHOLDER values (privacy URL, developer name, other apps) in
// src/main/res/values/about_config.xml. bundleRelease (the store AAB) fails while any value still contains PLACEHOLDER.
// If there are no other apps to promote, set about_more_apps_enabled to false: the entry is hidden and the about_more_app_* values are skipped.
// assembleDebug and assembleRelease are not affected. Configuration-cache safe: only a declared file input, no Project access at run time.
abstract class CheckNoAboutPlaceholders : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val configFile: RegularFileProperty

    @TaskAction
    fun check() {
        val text = configFile.get().asFile.readText()
        val moreAppsEnabled = Regex("""<bool\s+name="about_more_apps_enabled"[^>]*>\s*(\w+)\s*</bool>""")
            .find(text)?.groupValues?.get(1)?.lowercase() != "false"
        val left = Regex("""<string\s+name="([^"]+)"[^>]*>([^<]*)</string>""").findAll(text)
            .filter { it.groupValues[2].contains("PLACEHOLDER") }
            .map { it.groupValues[1] }
            .filter { moreAppsEnabled || !it.startsWith("about_more_app_") }
            .toList()
        if (left.isNotEmpty()) {
            throw GradleException(
                "Replace the PLACEHOLDER values in app/src/main/res/values/about_config.xml before a store build " +
                    "(still placeholders: " + left.joinToString() + "). " +
                    "If there are no other apps to promote, set about_more_apps_enabled to false instead of filling in the about_more_app_* values."
            )
        }
    }
}

val checkNoAboutPlaceholders = tasks.register<CheckNoAboutPlaceholders>("checkNoAboutPlaceholders") {
    group = "verification"
    description = "Fails while about_config.xml still has PLACEHOLDER values (store builds only)."
    configFile.set(layout.projectDirectory.file("src/main/res/values/about_config.xml"))
}

tasks.matching { it.name == "bundleRelease" }.configureEach {
    dependsOn(checkNoAboutPlaceholders)
}
