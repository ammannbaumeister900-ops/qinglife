# 把微信开发者工具窗口移到指定显示器并最大化，保证模拟器按 100% 渲染（截图 ≥390px 宽）。
# 开发者工具新开窗口时尺寸很小，模拟器会被压缩到 149px 宽；1080px 高的横屏也放不下 100% 的模拟器。
# 用法：pwsh tools/capture/place-window.ps1 [-X -1440 -Y -982 -Width 1440 -Height 2560]
# 默认值是本机竖屏显示器的位置；换机器时用 [System.Windows.Forms.Screen]::AllScreens 查看。
param(
  [int]$X = -1440,
  [int]$Y = -982,
  [int]$Width = 1440,
  [int]$Height = 2560
)

Add-Type @"
using System; using System.Runtime.InteropServices;
public class QingWindow {
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr a, int x, int y, int cx, int cy, uint f);
}
"@

$windows = Get-Process | Where-Object { $_.MainWindowHandle -ne 0 -and $_.ProcessName -like '*开发者工具*' }
if (-not $windows) { Write-Error '没有找到微信开发者工具窗口'; exit 1 }
foreach ($process in $windows) {
  $handle = $process.MainWindowHandle
  [QingWindow]::ShowWindow($handle, 9) | Out-Null          # SW_RESTORE
  [QingWindow]::SetWindowPos($handle, [IntPtr]::Zero, $X, $Y, $Width, $Height, 0x0040) | Out-Null
  [QingWindow]::ShowWindow($handle, 3) | Out-Null          # SW_MAXIMIZE
  Write-Output "placed: $($process.MainWindowTitle)"
}
