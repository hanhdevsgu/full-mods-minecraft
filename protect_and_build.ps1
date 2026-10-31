# Step 1: Compile Java sources (Java 8 bytecode 52.0)
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "[1/3] Compiling Java sources..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
& powershell -ExecutionPolicy Bypass -File .\build_mod.ps1
if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed!"
    exit 1
}

# Step 2: Assemble unobfuscated staging JAR
Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host "[2/3] Assembling staging JAR..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
if (Test-Path "sell kho_unobf.jar") {
    Remove-Item -Force "sell kho_unobf.jar"
}
Copy-Item -Path "sellsat.jar.bak" -Destination "sell kho_unobf.jar" -Force

$jarProc = Start-Process -FilePath "jar" -ArgumentList "-uf `"sell kho_unobf.jar`" -C build\classes ." -NoNewWindow -Wait -PassThru
if ($jarProc.ExitCode -ne 0) {
    Write-Error "Failed to update staging jar!"
    exit 1
}
Write-Host "Staging JAR assembled successfully." -ForegroundColor Green

# Step 3: Run ProGuard 7.4.2 Obfuscation
Write-Host "`n==========================================" -ForegroundColor Cyan
Write-Host "[3/3] Running ProGuard 7.4.2 Obfuscation..." -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

if (Test-Path "sell kho.jar") {
    Remove-Item -Force "sell kho.jar"
}

$pgArgs = @(
    "-jar",
    "proguard.jar",
    "@proguard_production.pro"
)

$pgProc = Start-Process -FilePath "java" -ArgumentList $pgArgs -NoNewWindow -Wait -PassThru
if ($pgProc.ExitCode -ne 0) {
    Write-Error "ProGuard obfuscation failed with exit code $($pgProc.ExitCode)"
    exit 1
}

Write-Host "`n==========================================" -ForegroundColor Green
Write-Host "SUCCESS! sell kho.jar has been compiled and obfuscated 100%!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green

$fileInfo = Get-Item "sell kho.jar"
Write-Host "Output File: $($fileInfo.FullName)"
Write-Host "Size: $($fileInfo.Length) bytes"
Write-Host "Modified: $($fileInfo.LastWriteTime)"

