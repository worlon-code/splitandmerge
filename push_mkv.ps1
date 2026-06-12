$file = Get-ChildItem "app\src\androidTest\assets\*.mkv" | Select-Object -First 1
$adb = "D:\idm\platform-tools-latest-windows\platform-tools\adb.exe"
& $adb push $file.FullName "/sdcard/Android/data/com.splitandmerge.mkvslice/files/Dridam.mkv"
