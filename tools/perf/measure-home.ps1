<#
.SYNOPSIS
  PG4 - đo khung hình Home (dumpsys gfxinfo) theo từng cấu hình công tắc đo, xen kẽ trong cùng phiên.

.DESCRIPTION
  Cần bản build `perf` (.\gradlew.bat assemblePerf installPerf) và một máy cắm adb. Mỗi lượt: đặt công tắc
  (adb shell setprop debug.os26.perf ...), tắt hẳn app, mở Home, chờ kính dựng xong, reset gfxinfo, chạy kịch bản, đọc gfxinfo.
  Thứ tự: mỗi vòng chạy lần lượt mọi cấu hình (xen kẽ), cuối phiên lặp lại cấu hình đầu (bài học GL: máy trôi theo nhiệt).
  Kết quả in ra bảng và ghi CSV vào tools/perf/out/ (không commit).

  Kịch bản:
    swipe  - lướt qua lại giữa trang 1 và trang -Pages (không chạm Today / Thư viện). Home phải mở ở trang 1.
    cold   - SW: vuốt ngay sau khi mở app (một lượt đi-về qua -Pages trang), đo cú khựng của lần đầu; dùng kèm -WarmupSec 1.
    jiggle - nhấn giữ chỗ trống (-GapX, -GapY) vào edit mode, đứng yên -IdleSec giây (icon rung).
    folder - chạm ô thư mục (-FolderX, -FolderY) rồi Back, -Cycles lần.
    idle   - đứng yên -IdleSec giây.
    spotlight - SP: vuốt xuống giữa màn mở Spotlight (-OpenY1 -> -OpenY2, phần chiều cao), gõ -Query, chờ -ResultsMs rồi mới bắt đầu
             đo; mỗi vòng -Cycles lần kéo danh sách lên rồi xuống (0,45 -> 0,15 chiều cao). Cần bật "Vuốt xuống để tìm kiếm", launcher
             mặc định (có lối tắt) và truy vấn ra đủ section để danh sách cuộn được (danh sách không cuộn được thì kéo lên là đóng lớp).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\tools\perf\measure-home.ps1 -Scenario swipe -Pages 3
.EXAMPLE
  .\tools\perf\measure-home.ps1 -Scenario jiggle -GapX 540 -GapY 1500 -Configs full,noRealGlass
.EXAMPLE
  .\tools\perf\measure-home.ps1 -Scenario spotlight -Configs full,flatSearchCards -Cycles 5 -Query ca
#>
param(
    [ValidateSet('swipe', 'cold', 'jiggle', 'folder', 'idle', 'spotlight')] [string]$Scenario = 'swipe',
    [string[]]$Configs = @('full', 'noRealGlass', 'noRealGlass+noFrosted', 'noRealGlass+noFrosted+noWidgets'),
    [int]$Rounds = 3,
    [int]$Pages = 2,
    [int]$Cycles = 5,
    [int]$SwipeMs = 250,
    [int]$SettleMs = 700,
    [int]$GapX = -1,
    [int]$GapY = -1,
    [int]$FolderX = -1,
    [int]$FolderY = -1,
    [int]$IdleSec = 10,
    # SP: kịch bản spotlight. Truy vấn chỉ chữ ASCII (adb input text), nên khớp cả app, lối tắt, danh bạ và mục Cài đặt.
    [string]$Query = 'ca',
    [double]$OpenY1 = 0.30,
    [double]$OpenY2 = 0.60,
    [int]$ResultsMs = 2500,
    [int]$WarmupSec = 4,
    [string]$Serial = '',
    # Đặt app làm launcher mặc định trước khi đo: không thì mỗi lần mở Home hiện hộp hỏi "đặt làm mặc định" (PB) che màn và làm sai số đo.
    [bool]$SetHome = $true,
    # SW: ép ART biên dịch xong app (theo profile) trước khi đo. Vừa cài APK thì máy yếu còn biên dịch nền vài phút, làm số đo xấu đi
    # ở MỌI cấu hình (đo 2026-11-25, Live 4: 6–10 % so với 0–1 % sau khi biên dịch xong). Bỏ qua với bản debug.
    [bool]$Compile = $true
)

$ErrorActionPreference = 'Stop'
$pkg = 'gambi.global.os26launcher'
$activity = "$pkg/.ui.home.HomeActivity"

function Invoke-Adb {
    $all = @()
    if ($Serial) { $all += @('-s', $Serial) }
    $all += $args
    # Không gộp stderr (2>&1): ở PowerShell 5.1 với ErrorActionPreference=Stop, dòng stderr của adb (vd. khởi động daemon) thành lỗi dừng script.
    $out = & adb @all
    if ($LASTEXITCODE -ne 0) { throw "adb $($args -join ' ') failed (exit $LASTEXITCODE): $out" }
    return $out
}

function Get-ScreenSize {
    $text = (Invoke-Adb shell wm size) -join "`n"
    $m = [regex]::Matches($text, '(\d+)x(\d+)')
    if ($m.Count -eq 0) { throw "Không đọc được cỡ màn: $text" }
    $last = $m[$m.Count - 1]
    return @([int]$last.Groups[1].Value, [int]$last.Groups[2].Value)
}

function Get-Number([string]$text, [string]$pattern) {
    $m = [regex]::Match($text, $pattern, 'Multiline')
    if ($m.Success) { return [double]$m.Groups[1].Value }
    return $null
}

function Read-GfxInfo {
    $text = (Invoke-Adb shell dumpsys gfxinfo $pkg) -join "`n"
    return [pscustomobject]@{
        Frames = Get-Number $text '^\s*Total frames rendered:\s*(\d+)'
        Janky  = Get-Number $text '^\s*Janky frames:\s*\d+\s*\(([\d.]+)%\)'
        P50    = Get-Number $text '^\s*50th percentile:\s*(\d+)ms'
        P90    = Get-Number $text '^\s*90th percentile:\s*(\d+)ms'
        P95    = Get-Number $text '^\s*95th percentile:\s*(\d+)ms'
        P99    = Get-Number $text '^\s*99th percentile:\s*(\d+)ms'
        # SW: số khung > 33 ms (cú khựng mắt thấy) đếm từ HISTOGRAM của gfxinfo (lần xuất hiện đầu = toàn app).
        Slow33 = Get-Slow33 $text
    }
}

function Get-Slow33([string]$text) {
    $m = [regex]::Match($text, '^\s*HISTOGRAM:(.*)$', 'Multiline')
    if (-not $m.Success) { return $null }
    $sum = 0
    foreach ($b in [regex]::Matches($m.Groups[1].Value, '(\d+)ms=(\d+)')) {
        if ([int]$b.Groups[1].Value -gt 33) { $sum += [int]$b.Groups[2].Value }
    }
    return $sum
}

function Get-BatteryTemp {
    $text = (Invoke-Adb shell dumpsys battery) -join "`n"
    $t = Get-Number $text '^\s*temperature:\s*(\d+)'
    if ($null -eq $t) { return $null }
    return $t / 10
}

function Start-Home([string]$config) {
    $value = if ($config -eq 'full') { 'none' } else { $config }
    Invoke-Adb shell setprop debug.os26.perf $value | Out-Null
    Invoke-Adb shell am force-stop $pkg | Out-Null
    Invoke-Adb shell am start -W -n $activity | Out-Null
    Start-Sleep -Seconds $WarmupSec
}

function Invoke-Scenario([int]$w, [int]$h) {
    $y = [int]($h * 0.45)
    switch ($Scenario) {
        'cold' {
            $left = [int]($w * 0.2); $right = [int]($w * 0.8)
            for ($i = 1; $i -lt $Pages; $i++) {
                Invoke-Adb shell input swipe $right $y $left $y $SwipeMs | Out-Null
                Start-Sleep -Milliseconds $SettleMs
            }
            for ($i = 1; $i -lt $Pages; $i++) {
                Invoke-Adb shell input swipe $left $y $right $y $SwipeMs | Out-Null
                Start-Sleep -Milliseconds $SettleMs
            }
        }
        'swipe' {
            $left = [int]($w * 0.2); $right = [int]($w * 0.8)
            for ($c = 0; $c -lt $Cycles; $c++) {
                for ($i = 1; $i -lt $Pages; $i++) {
                    Invoke-Adb shell input swipe $right $y $left $y $SwipeMs | Out-Null
                    Start-Sleep -Milliseconds $SettleMs
                }
                for ($i = 1; $i -lt $Pages; $i++) {
                    Invoke-Adb shell input swipe $left $y $right $y $SwipeMs | Out-Null
                    Start-Sleep -Milliseconds $SettleMs
                }
            }
        }
        'jiggle' {
            Start-Sleep -Seconds $IdleSec
            Invoke-Adb shell input keyevent KEYCODE_HOME | Out-Null
        }
        'folder' {
            for ($c = 0; $c -lt $Cycles; $c++) {
                Invoke-Adb shell input tap $FolderX $FolderY | Out-Null
                Start-Sleep -Milliseconds 1200
                Invoke-Adb shell input keyevent KEYCODE_BACK | Out-Null
                Start-Sleep -Milliseconds 1200
            }
        }
        'idle' { Start-Sleep -Seconds $IdleSec }
        'spotlight' {
            $x = [int]($w * 0.5); $low = [int]($h * 0.45); $high = [int]($h * 0.15)
            for ($c = 0; $c -lt $Cycles; $c++) {
                Invoke-Adb shell input swipe $x $low $x $high $SwipeMs | Out-Null
                Start-Sleep -Milliseconds $SettleMs
                Invoke-Adb shell input swipe $x $high $x $low $SwipeMs | Out-Null
                Start-Sleep -Milliseconds $SettleMs
            }
        }
    }
}

function Enter-Spotlight([int]$w, [int]$h) {
    # SP: vuốt xuống ở giữa màn (cử chỉ HT) mở Spotlight, bàn phím tự hiện; gõ truy vấn rồi chờ kết quả (gợi ý web cần mạng).
    $x = [int]($w * 0.5)
    Invoke-Adb logcat -c | Out-Null
    Invoke-Adb shell input swipe $x ([int]($h * $OpenY1)) $x ([int]($h * $OpenY2)) 250 | Out-Null
    Start-Sleep -Milliseconds 1200
    Invoke-Adb shell input text $Query | Out-Null
    # Chờ chỉ mục lối tắt dựng xong (log "shortcut index" của SearchViewModel, tối đa 10 giây), kẻo section Lối tắt chen vào giữa lúc đo.
    $deadline = (Get-Date).AddSeconds(10)
    while ((Get-Date) -lt $deadline) {
        $idx = Invoke-Adb logcat -d -s SearchViewModel:D
        if (@($idx | Select-String -SimpleMatch 'shortcut index').Count -gt 0) { break }
        Start-Sleep -Milliseconds 500
    }
    if ((Get-Date) -ge $deadline) { Write-Warning 'Không thấy log "shortcut index" sau 10 giây (chưa là launcher mặc định?): section Lối tắt có thể rỗng.' }
    Start-Sleep -Milliseconds $ResultsMs
    # Kéo ngắn (200 px, dưới ngưỡng đóng 300 px) ở phần trên danh sách để hạ bàn phím trước khi đo: lượt đo không gồm hoạt cảnh IME,
    # và các cú kéo sau không rơi vào bàn phím.
    $y0 = [int]($h * 0.30)
    Invoke-Adb shell input swipe $x $y0 $x ($y0 - 200) 200 | Out-Null
    Start-Sleep -Milliseconds $SettleMs
}

function Enter-EditMode {
    # input swipe tại chỗ không phải nhấn giữ với launcher này (bài học GL): dùng motionevent.
    Invoke-Adb shell input motionevent DOWN $GapX $GapY | Out-Null
    Start-Sleep -Milliseconds 1100
    Invoke-Adb shell input motionevent MOVE ($GapX + 2) $GapY | Out-Null
    Invoke-Adb shell input motionevent MOVE $GapX $GapY | Out-Null
    Invoke-Adb shell input motionevent UP $GapX $GapY | Out-Null
    Start-Sleep -Milliseconds 800
}

# `powershell -File` truyền `-Configs a,b,c` thành MỘT chuỗi "a,b,c" (không tách mảng như khi gọi trong PowerShell): tự tách theo dấu phẩy.
$Configs = @($Configs | ForEach-Object { $_ -split '[,;]' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })

# Kiểm tham số
if (($Scenario -eq 'swipe' -or $Scenario -eq 'cold') -and $Pages -lt 2) { throw '-Pages phải >= 2 (số trang lưới thật để lướt qua lại).' }
if ($Scenario -eq 'jiggle' -and ($GapX -lt 0 -or $GapY -lt 0)) { throw 'jiggle cần -GapX -GapY (toạ độ một chỗ trống trên trang 1).' }
if ($Scenario -eq 'folder' -and ($FolderX -lt 0 -or $FolderY -lt 0)) { throw 'folder cần -FolderX -FolderY (tâm ô thư mục trên trang 1).' }

$model = ((Invoke-Adb shell getprop ro.product.model) -join '').Trim()
$sdk = ((Invoke-Adb shell getprop ro.build.version.sdk) -join '').Trim()
$flags = (Invoke-Adb shell dumpsys package $pkg) -join "`n"
if ($flags -match 'pkgFlags=\[[^\]]*DEBUGGABLE') {
    Write-Warning 'App đang cài là bản DEBUG: số đo không đại diện (trình thông dịch). Cài bản perf: .\gradlew.bat installPerf'
}
$size = Get-ScreenSize
if ($Compile -and -not ($flags -match 'pkgFlags=\[[^\]]*DEBUGGABLE')) {
    Write-Host 'Đang ép biên dịch app (cmd package compile -m speed-profile -f), có thể mất một lúc trên máy yếu...'
    Invoke-Adb shell cmd package compile -m speed-profile -f $pkg | Out-Null
}
if ($SetHome) {
    Invoke-Adb shell cmd package set-home-activity $activity | Out-Null
    Write-Host "Đã đặt $activity làm launcher mặc định (tắt bằng -SetHome `$false)."
}
Write-Host "Máy: $model (API $sdk), màn $($size[0])x$($size[1]), kịch bản $Scenario, $Rounds vòng x $($Configs.Count) cấu hình"

$plan = @()
for ($r = 1; $r -le $Rounds; $r++) { foreach ($c in $Configs) { $plan += [pscustomobject]@{ Round = $r; Config = $c } } }
$plan += [pscustomobject]@{ Round = 'cuối'; Config = $Configs[0] }

$outDir = Join-Path $PSScriptRoot 'out'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

$results = @()
foreach ($step in $plan) {
    Write-Host "-> vòng $($step.Round): $($step.Config)"
    Start-Home $step.Config
    if ($Scenario -eq 'jiggle') { Enter-EditMode }
    if ($Scenario -eq 'spotlight') { Enter-Spotlight $size[0] $size[1] }
    Invoke-Adb shell dumpsys gfxinfo $pkg reset | Out-Null
    Invoke-Adb logcat -c | Out-Null
    Invoke-Scenario $size[0] $size[1]
    $g = Read-GfxInfo
    # SW: log khung chậm / dựng trang của app (HomePerfLog, chỉ bản debug / perf), lưu mỗi lượt.
    $log = Invoke-Adb logcat -d -s SwFrame:I SwPage:I SwDock:I SwSpot:I
    $logFile = Join-Path $outDir ("{0}-{1}-{2}-r{3}-{4}.log" -f $stamp, ($model -replace '\W', ''), $Scenario, $step.Round, ($step.Config -replace '\W', '_'))
    $log | Set-Content -Path $logFile -Encoding UTF8
    $slowLog = @($log | Select-String -SimpleMatch 'SwFrame').Count
    $pageLog = @($log | Select-String -SimpleMatch 'composed page=').Count
    $dockLive = @($log | Select-String -SimpleMatch 'live draw').Count # SW: số lần kính dock vẽ sống (RenderEffect chạy)
    $spotClosed = @($log | Select-String -SimpleMatch 'swipe dismiss').Count # SP: Spotlight bị vuốt đóng giữa lượt = lượt hỏng
    if ($Scenario -eq 'spotlight' -and $spotClosed -gt 0) { Write-Warning "Lượt $($step.Round) $($step.Config): Spotlight bị vuốt đóng (danh sách không cuộn được?): bỏ số lượt này, đổi -Query." }
    $results += [pscustomobject]@{
        Model = $model; Api = $sdk; Scenario = $Scenario; Round = $step.Round; Config = $step.Config
        Frames = $g.Frames; JankyPct = $g.Janky; P50 = $g.P50; P90 = $g.P90; P95 = $g.P95; P99 = $g.P99; Slow33 = $g.Slow33
        SlowLog = $slowLog; PagesComposed = $pageLog; DockLive = $dockLive; SpotClosed = $spotClosed
        TempC = Get-BatteryTemp
    }
}
Invoke-Adb shell setprop debug.os26.perf none | Out-Null

$results | Format-Table Round, Config, Frames, JankyPct, P50, P90, P95, P99, Slow33, SlowLog, PagesComposed, DockLive, SpotClosed, TempC -AutoSize

Write-Host 'Trung bình theo cấu hình (bỏ lượt lặp cuối):'
$results | Where-Object { $_.Round -ne 'cuối' } | Group-Object Config | ForEach-Object {
    [pscustomobject]@{
        Config   = $_.Name
        JankyPct = [math]::Round(($_.Group | Measure-Object JankyPct -Average).Average, 2)
        P50      = [math]::Round(($_.Group | Measure-Object P50 -Average).Average, 1)
        P95      = [math]::Round(($_.Group | Measure-Object P95 -Average).Average, 1)
        P99      = [math]::Round(($_.Group | Measure-Object P99 -Average).Average, 1)
        Slow33   = [math]::Round(($_.Group | Measure-Object Slow33 -Average).Average, 1)
    }
} | Format-Table -AutoSize

$file = Join-Path $outDir ("{0}-{1}-{2}.csv" -f $stamp, ($model -replace '\W', ''), $Scenario)
$results | Export-Csv -Path $file -NoTypeInformation -Encoding UTF8
Write-Host "CSV: $file"
