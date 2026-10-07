@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -Command "if(Test-Path 'data\install.json'){$c=Get-Content 'data\install.json' -Raw|ConvertFrom-Json;Write-Host ('Email: '+$c.AdminEmail);Write-Host ('Password iniziale: '+$c.AdminPassword)}else{Write-Host 'Avvia prima BollettaLAB.'}"
pause
