<#
.SYNOPSIS
  FX (2026-11-27) - xem mốc thời gian ảnh mờ sau bảng thư mục (máy dưới Android 12) trong lúc nhìn hoạt ảnh mở thư mục.

.DESCRIPTION
  Công dụng: đo ảnh mờ có icon sau bảng thư mục (máy dưới Android 12) tới lúc nào so với hoạt ảnh mở bảng, trong lúc mắt nhìn lớp mờ.
  Dùng khi sửa đường chụp / làm mờ lớp Home (`rememberBlurredLayerSnapshot` trong DsFrosted.kt, lớp phủ trong HomeScreen.kt).

  LOG ĐÃ GỠ khi chốt FX (2026-11-27): muốn đo lại thì thêm lại `FolderBlurTrace` + các dòng gọi nó, chép từ commit ad39de3
  (`git show ad39de3 -- app/src/main/java`), build debug, chạy script; đo xong gỡ log trước khi merge.
  Cần một máy cắm adb, API < 31 (máy >= 31 dùng Modifier.blur, không đi đường này).
  Script xoá logcat, (tuỳ chọn) làm chậm hoạt ảnh hệ thống, rồi in từng lần mở thư mục:
    open        bắt đầu chụp (thư mục vừa mở)
    frames      đã chờ 2 khung (lớp Home đã ghi)
    readback    chụp lớp Home xong (main thread)
    copy        chép HARDWARE -> ARGB xong (luồng nền)
    blur        thu 1/8 + làm mờ xong (luồng nền)
    shown       ảnh mờ có icon bắt đầu hiện (kèm tiến độ hoạt ảnh mở bảng, 0..1)
    panel full  bảng phóng gần hết (tiến độ >= 0,98)
  Ảnh mờ tới tốt khi "shown" xảy ra TRƯỚC "panel full" (tiến độ < 1).
  Có -FolderX -FolderY thì script tự chạm ô thư mục rồi Back, -Cycles lần; không có thì anh tự mở / đóng thư mục, Ctrl+C để dừng.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\tools\debug\folder-blur.ps1
.EXAMPLE
  .\tools\debug\folder-blur.ps1 -Slow 5
.EXAMPLE
  .\tools\debug\folder-blur.ps1 -FolderX 180 -FolderY 900 -Cycles 5
#>
param(
    [string]$Serial = '',
    [double]$Slow = 0,          # > 0: đặt animator_duration_scale (vd 5 = chậm 5 lần) để dễ nhìn; trả lại giá trị cũ khi thoát
    [int]$FolderX = -1,
    [int]$FolderY = -1,
    [int]$Cycles = 5,
    [int]$OpenMs = 1500,
    [int]$CloseMs = 1200
)

$ErrorActionPreference = 'Stop'
$adbArgs = @()
if ($Serial) { $adbArgs = @('-s', $Serial) }
# Tên hàm KHÔNG được là "Adb": PowerShell không phân biệt hoa thường, hàm sẽ che lệnh adb và tự gọi lại mình (CallDepthOverflow).
function Invoke-Adb { & adb.exe @adbArgs @args }

$sdk = (Invoke-Adb shell getprop ro.build.version.sdk).Trim()
Write-Host "Thiết bị API $sdk"
if ([int]$sdk -ge 31) { Write-Warning 'Máy API >= 31: Home dùng Modifier.blur, không có log FolderBlur.' }

$oldScale = $null
if ($Slow -gt 0) {
    $oldScale = (Invoke-Adb shell settings get global animator_duration_scale).Trim()
    Invoke-Adb shell settings put global animator_duration_scale $Slow | Out-Null
    Write-Host "animator_duration_scale = $Slow (cũ: $oldScale)"
}

Invoke-Adb logcat -c
$job = $null
try {
    if ($FolderX -ge 0 -and $FolderY -ge 0) {
        # Chạm / Back chạy nền để vòng đọc log bên dưới in ngay.
        $job = Start-Job -ArgumentList $adbArgs, $FolderX, $FolderY, $Cycles, $OpenMs, $CloseMs -ScriptBlock {
            param($a, $x, $y, $n, $openMs, $closeMs)
            Start-Sleep -Milliseconds 800
            for ($i = 1; $i -le $n; $i++) {
                & adb.exe @a shell input tap $x $y | Out-Null
                Start-Sleep -Milliseconds $openMs
                & adb.exe @a shell input keyevent KEYCODE_BACK | Out-Null
                Start-Sleep -Milliseconds $closeMs
            }
        }
        Write-Host "Tự mở thư mục tại ($FolderX, $FolderY) x $Cycles. Ctrl+C để dừng sớm."
    } else {
        Write-Host 'Mở / đóng thư mục trên máy. Ctrl+C để dừng.'
    }
    $n = 0
    $shownAt = $null
    Invoke-Adb logcat -v time -s FolderBlur:I | ForEach-Object {
        $line = $_
        if ($line -match 'FolderBlur\(\s*\d+\):\s*(.*)$') {
            $msg = $Matches[1]
            if ($msg -eq 'open') {
                $n++
                $shownAt = $null
                Write-Host ''
                Write-Host "=== Lần mở $n ===" -ForegroundColor Cyan
                return
            }
            if ($msg -match '^\+(\d+)ms shown progress=([\d.,]+)') {
                $shownAt = [int]$Matches[1]
                $p = [double]($Matches[2] -replace ',', '.')
                $color = if ($p -lt 0.98) { 'Green' } else { 'Yellow' }
                Write-Host "  $msg" -ForegroundColor $color
                return
            }
            if ($msg -match '^\+(\d+)ms panel full') {
                Write-Host "  $msg"
                if ($null -eq $shownAt) { Write-Host '  -> ảnh mờ CHƯA tới khi bảng đã phóng xong' -ForegroundColor Yellow }
                else { Write-Host '  -> ảnh mờ tới trước khi bảng phóng xong' -ForegroundColor Green }
                return
            }
            Write-Host "  $msg"
        }
    }
} finally {
    if ($job) { Stop-Job $job -ErrorAction SilentlyContinue; Remove-Job $job -Force -ErrorAction SilentlyContinue }
    if ($null -ne $oldScale) {
        if ($oldScale -eq 'null' -or $oldScale -eq '') { $oldScale = '1' }
        Invoke-Adb shell settings put global animator_duration_scale $oldScale | Out-Null
        Write-Host "Trả animator_duration_scale = $oldScale"
    }
}
