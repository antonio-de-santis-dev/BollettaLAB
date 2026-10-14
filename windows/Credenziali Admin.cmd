@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -Command "if(Test-Path 'data\install.json'){$c=Get-Content 'data\install.json' -Raw|ConvertFrom-Json;Write-Host ('Email: '+$c.AdminEmail);Write-Host ('Password iniziale: '+$c.AdminPassword);if(Test-Path 'data\session.json'){$s=Get-Content 'data\session.json' -Raw|ConvertFrom-Json;if($s.Url){Write-Host ('Indirizzo: '+$s.Url)}}}else{Write-Host 'Avvia prima BollettaLAB.'}"
pause
