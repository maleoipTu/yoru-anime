$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    dotnet publish Yoru/Yoru.csproj -c Release -r win-x64 --self-contained true -p:DebugType=None -p:DebugSymbols=false -o outputs/Yoru
    if ($LASTEXITCODE -ne 0) { throw 'Не удалось собрать Yoru.' }
    dotnet run --project Tests/Tests.csproj -c Release -r win-x64 --self-contained true
    if ($LASTEXITCODE -ne 0) { throw 'Проверки не пройдены.' }
    Copy-Item -LiteralPath README.md -Destination outputs/Yoru/README.md
    Write-Host 'Готово: outputs/Yoru/Yoru.exe'
} finally { Pop-Location }
