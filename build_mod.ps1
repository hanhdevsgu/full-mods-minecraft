$libDir = "$env:APPDATA\.minecraft\libraries"
$gradleCaches = "$env:USERPROFILE\.gradle\caches"

$forgeBin = "$gradleCaches\minecraft\net\minecraftforge\forge\1.12.2-14.23.5.2847\snapshot\20171003\forgeBin-1.12.2-14.23.5.2847.jar"
$srgJar = "$gradleCaches\forge_gradle\mcp_repo\net\minecraft\joined\1.12.2\joined-1.12.2-srg.jar"
$allJars = (Get-ChildItem -Path $libDir -Recurse -Filter "*.jar" | Select-Object -ExpandProperty FullName)

# Put srgJar FIRST so SRG Minecraft classes take precedence!
$cpList = @($srgJar, $forgeBin) + $allJars
$cp = ($cpList | Where-Object { $_ -ne $null -and (Test-Path $_) }) -join ";"

if (Test-Path "build\classes") {
    Remove-Item -Recurse -Force "build\classes"
}
New-Item -ItemType Directory -Force -Path "build\classes" | Out-Null

$argsContent = @(
    "-source",
    "1.8",
    "-target",
    "1.8",
    "-cp",
    $cp,
    "-d",
    "build\classes",
    "-encoding",
    "UTF-8",
    "src\com\zorotool\macrorecorder\LicenseGuard.java",
    "src\com\zorotool\macrorecorder\MacroRecorderMod.java"
)
[System.IO.File]::WriteAllLines((Join-Path (Get-Location) "javac_args.txt"), $argsContent)

Write-Host "Compiling LicenseGuard.java and MacroRecorderMod.java with javac (Java 8 target)..."
$proc = Start-Process -FilePath "javac" -ArgumentList "@javac_args.txt" -NoNewWindow -Wait -PassThru
Write-Host "Javac exit code: $($proc.ExitCode)"

if ($proc.ExitCode -eq 0) {
    Write-Host "Compilation SUCCESS (Java 8 Bytecode)!"
} else {
    Write-Host "Compilation FAILED!"
    exit 1
}

