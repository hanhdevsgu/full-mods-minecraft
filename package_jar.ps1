Write-Host "Creating backup: sell pvp.jar.bak"
Copy-Item -Path "sell pvp.jar" -Destination "sell pvp.jar.bak" -Force

Write-Host "Updating sell pvp.jar with newly compiled classes..."
$jarProc = Start-Process -FilePath "jar" -ArgumentList "-uf `"sell pvp.jar`" -C build\classes ." -NoNewWindow -Wait -PassThru

if ($jarProc.ExitCode -eq 0) {
    Write-Host "Jar updated successfully!"
} else {
    Write-Host "Jar update FAILED! Code: $($jarProc.ExitCode)"
}

Write-Host "Inspecting sell pvp.jar entries:"
$proc = Start-Process -FilePath "jar" -ArgumentList "-tf `"sell pvp.jar`"" -NoNewWindow -Wait -PassThru

