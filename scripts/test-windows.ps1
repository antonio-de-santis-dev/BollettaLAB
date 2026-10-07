param([Parameter(Mandatory=$true)][string]$PackageRoot)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$PackageRoot=(Resolve-Path -LiteralPath $PackageRoot).Path
$launcher=Join-Path $PackageRoot 'Launcher.ps1'
function Launch($action) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcher -Action $action -NoBrowser
    if($LASTEXITCODE -ne 0) { throw "Launcher $action fallito" }
}
try {
    # Il PATH del destinatario non contiene Java, Docker, Node o MySQL.
    $originalPath=$env:PATH
    $env:PATH="$env:SystemRoot\System32;$env:SystemRoot\System32\WindowsPowerShell\v1.0"
    Launch 'Start'
    $first=Get-Content (Join-Path $PackageRoot 'data\session.json') -Raw | ConvertFrom-Json
    Launch 'Start'
    $second=Get-Content (Join-Path $PackageRoot 'data\session.json') -Raw | ConvertFrom-Json
    if($first.Instance -ne $second.Instance) { throw 'Il doppio avvio ha creato una seconda istanza.' }
    $config=Get-Content (Join-Path $PackageRoot 'data\install.json') -Raw | ConvertFrom-Json
    $headers=@{'X-Requested-With'='BollettaLAB'}
    $session=New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $body=@{email=$config.AdminEmail;password=$config.AdminPassword} | ConvertTo-Json
    Invoke-RestMethod 'http://127.0.0.1:8091/api/utenti/auth/login' -Method Post -ContentType 'application/json' -Body $body -Headers $headers -WebSession $session | Out-Null
    $identity=Invoke-RestMethod 'http://127.0.0.1:8091/api/utenti/auth/me' -WebSession $session
    if($identity.role -ne 'ADMIN') { throw 'Accesso admin fallito.' }
    $env:PATH=$originalPath
    & python (Join-Path $repo 'scripts\smoke.py')
    if($LASTEXITCODE -ne 0) { throw 'Regressioni HTTP Windows fallite.' }
    $env:TEST_ADMIN_EMAIL=$config.AdminEmail;$env:TEST_ADMIN_PASSWORD=$config.AdminPassword
    Push-Location (Join-Path $repo 'frontend')
    try { & npx.cmd playwright test; if($LASTEXITCODE -ne 0) { throw 'Regressioni browser Windows fallite.' } }
    finally { Pop-Location }
    # Crea un privato e verifica la persistenza dopo arresto e riavvio.
    $email='restart-'+[Guid]::NewGuid().ToString('N')+'@example.test'
    $body=@{name='Persistenza Windows';email=$email;password='PasswordRestart123!';type='PRIVATE'} | ConvertTo-Json
    Invoke-RestMethod 'http://127.0.0.1:8091/api/utenti/auth/register' -Method Post -ContentType 'application/json' -Body $body -Headers $headers | Out-Null
    Launch 'Stop'
    foreach($entry in $first.Processes) {
        $process=Get-Process -Id $entry.ProcessId -ErrorAction SilentlyContinue
        if($null -ne $process -and $process.StartTime.ToUniversalTime().Ticks.ToString() -eq $entry.StartTicks) { throw "Processo $($entry.Name) non arrestato." }
    }
    Launch 'Start'
    $body=@{email=$email;password='PasswordRestart123!'} | ConvertTo-Json
    Invoke-RestMethod 'http://127.0.0.1:8091/api/utenti/auth/login' -Method Post -ContentType 'application/json' -Body $body -Headers $headers | Out-Null
    Write-Host 'Windows PASS: PATH senza tool, primo avvio, doppio avvio, admin, tre simulatori, browser, arresto e persistenza.'
} finally {
    if($originalPath) { $env:PATH=$originalPath }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $launcher -Action Stop -NoBrowser
}
