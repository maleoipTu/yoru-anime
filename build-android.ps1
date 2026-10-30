param([string]$SdkPath=$env:ANDROID_HOME,[switch]$Release)
$ErrorActionPreference='Stop'
if (-not $SdkPath) { throw 'Укажите Android SDK: .\build-android.ps1 -SdkPath C:\Android\Sdk' }
$env:ANDROID_HOME=$SdkPath
Push-Location $PSScriptRoot
try {
    $tasks=if($Release){@('assembleRelease','testDebugUnitTest','lintRelease')}else{@('assembleDebug','testDebugUnitTest','lintDebug')}
    if($Release -and -not $env:YORU_STORE_FILE) { throw 'Для release задайте YORU_STORE_FILE, YORU_STORE_PASSWORD и YORU_KEY_PASSWORD. Приватный ключ не храните в Git.' }
    & ./android/gradlew.bat -p android @tasks --console plain
    if($LASTEXITCODE -ne 0){throw 'Сборка или проверки Android не пройдены.'}
    New-Item -ItemType Directory -Force outputs | Out-Null
    $variant=if($Release){'release'}else{'debug'}
    $metadata=Get-Content "android/app/build/outputs/apk/$variant/output-metadata.json" -Raw | ConvertFrom-Json
    $version=$metadata.elements[0].versionName
    Copy-Item -LiteralPath "android/app/build/outputs/apk/$variant/app-$variant.apk" -Destination "outputs/Yoru-Android-$version-$variant.apk"
    Write-Host "Готово: outputs/Yoru-Android-$version-$variant.apk"
} finally { Pop-Location }
