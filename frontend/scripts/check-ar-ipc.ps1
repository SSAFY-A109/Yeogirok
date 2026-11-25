param([switch]$Run, [string]$Device)

$ErrorActionPreference = 'Stop'
$arRoot = Split-Path -Parent $PSScriptRoot
$arSdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$arTools = (Get-ChildItem "$arSdk/build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
$arAndroidJar = (Get-ChildItem "$arSdk/platforms" -Filter android.jar -Recurse | Sort-Object FullName -Descending | Select-Object -First 1).FullName
$arJavaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { Split-Path (Get-Command javac).Source }
$arOutput = Join-Path $arRoot '.expo/ar-ipc-test'
$arClasses = Join-Path $arRoot 'android/app/build/tmp/kotlin-classes/debug'
if (!(Test-Path "$arClasses/com/yeogirokarpoc/geo/ArResultReceiverKt.class")) { throw 'Build :app:assembleDebug first.' }

Push-Location $arRoot
try {
    New-Item -ItemType Directory -Force -Path $arOutput, "$arOutput/classes", "$arOutput/dex" | Out-Null
    $arManifest = '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.ssafya109.yeogirok.archecks"><uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36"/><application android:label="AR IPC Check" android:testOnly="true"/><instrumentation android:name="com.yeogirokarpoc.geo.ArResultReceiverCheck" android:targetPackage="com.ssafya109.yeogirok"/></manifest>'
    [IO.File]::WriteAllText("$arOutput/AndroidManifest.xml", $arManifest)
    & "$arJavaBin/javac.exe" --release 11 -cp "$arAndroidJar;$arClasses" -d "$arOutput/classes" native/ar/tests/ArResultReceiverCheck.java
    if ($LASTEXITCODE -ne 0) { throw 'Java check compilation failed.' }
    $arTestClasses = @(Get-ChildItem "$arOutput/classes" -Recurse -Filter '*.class' | ForEach-Object FullName)
    & "$arTools/d8.bat" --lib $arAndroidJar --classpath $arClasses --min-api 24 --output "$arOutput/dex" $arTestClasses
    if ($LASTEXITCODE -ne 0) { throw 'Check dex compilation failed.' }
    & "$arTools/aapt2.exe" link -I $arAndroidJar --manifest "$arOutput/AndroidManifest.xml" -o "$arOutput/unsigned.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Check manifest linking failed.' }
    & "$arJavaBin/jar.exe" uf "$arOutput/unsigned.apk" -C "$arOutput/dex" classes.dex
    if ($LASTEXITCODE -ne 0) { throw 'Check APK packaging failed.' }
    & "$arTools/zipalign.exe" -f 4 "$arOutput/unsigned.apk" "$arOutput/check.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Check APK alignment failed.' }
    & "$arTools/apksigner.bat" sign --ks android/app/debug.keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$arOutput/check-signed.apk" "$arOutput/check.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Check APK signing failed.' }
    Write-Output 'AR IPC check APK built'
    if ($Run) {
        $arDeviceArgs = if ($Device) { @('-s', $Device) } else { @() }
        & adb @arDeviceArgs install -t -r "$arOutput/check-signed.apk"
        if ($LASTEXITCODE -ne 0) { throw 'Check APK install failed.' }
        $arResult = (& adb @arDeviceArgs shell am instrument -r -w com.ssafya109.yeogirok.archecks/com.yeogirokarpoc.geo.ArResultReceiverCheck) -join "`n"
        Write-Output $arResult
        if ($arResult -notmatch 'AR ResultReceiver boot-loader Bundle round trip passed' -or $arResult -notmatch 'INSTRUMENTATION_CODE: -1') { throw 'AR IPC check failed.' }
    }
} finally { Pop-Location }
