param([ValidateSet('Start','Stop')][string]$Action='Start', [switch]$NoBrowser)
$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$data=Join-Path $root 'data'
$stateFile=Join-Path $data 'session.json'
$configFile=Join-Path $data 'install.json'
$baseUrl='http://127.0.0.1:8091'
$services=@('utenti','pagamento','luce','luce-business','gas')
$java=Join-Path $root 'runtime\bin\java.exe'
$mysqld=Join-Path $root 'mysql\bin\mysqld.exe'
$hash=[Security.Cryptography.SHA256]::Create()
$key=[BitConverter]::ToString($hash.ComputeHash([Text.Encoding]::UTF8.GetBytes($root.ToLowerInvariant()))).Replace('-','')
$hash.Dispose()
$mutex=New-Object Threading.Mutex($false,"Local\BollettaLAB-$key")
$locked=$false
$script:state=$null
$config=$null

function Write-Utf8($path,$value) {
    [IO.File]::WriteAllText($path,$value,(New-Object Text.UTF8Encoding($false)))
}
function Save-State {
    $temporary=$stateFile+'.tmp'
    Write-Utf8 $temporary ($script:state | ConvertTo-Json -Depth 8)
    Move-Item -LiteralPath $temporary -Destination $stateFile -Force
}
function New-Secret {
    $bytes=New-Object byte[] 32
    $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [BitConverter]::ToString($bytes).Replace('-','').ToLowerInvariant()
}
function Get-Owned($entry) {
    $process=Get-Process -Id $entry.ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $null }
    if ($process.Path -ne $entry.Executable -or $process.StartTime.ToUniversalTime().Ticks.ToString() -ne $entry.StartTicks) {
        throw "PID di $($entry.Name) riutilizzato da un altro processo: non verra arrestato."
    }
    return $process
}
function Start-Owned($name,$exe,$parameters,$variables) {
    $saved=@{}
    foreach($item in $variables.GetEnumerator()) {
        $saved[$item.Key]=[Environment]::GetEnvironmentVariable($item.Key,'Process')
        [Environment]::SetEnvironmentVariable($item.Key,[string]$item.Value,'Process')
    }
    try {
        foreach($suffix in @('log','error.log')) {
            $log=Join-Path $root "logs\$name.$suffix"
            if(Test-Path -LiteralPath $log) { Move-Item -LiteralPath $log -Destination ($log+'.previous') -Force }
        }
        $process=Start-Process -FilePath $exe -ArgumentList $parameters -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root "logs\$name.log") -RedirectStandardError (Join-Path $root "logs\$name.error.log")
        $entry=[pscustomobject]@{Name=$name;Executable=$exe;ProcessId=$process.Id;StartTicks=$process.StartTime.ToUniversalTime().Ticks.ToString()}
        $script:state.Processes=@($script:state.Processes)+$entry
        Save-State
        return $process
    } finally {
        foreach($item in $saved.GetEnumerator()) { [Environment]::SetEnvironmentVariable($item.Key,$item.Value,'Process') }
    }
}
function Assert-Free($port) {
    $listener=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,$port)
    try { $listener.Start() } catch { throw "Porta $port occupata. Chiudi l'altra copia di BollettaLAB o il servizio Docker prima di avviare." }
    finally { $listener.Stop() }
}
function Test-Mysql {
    $probe=Start-Process -FilePath (Join-Path $root 'mysql\bin\mysql.exe') -ArgumentList @(('"--defaults-extra-file='+(Join-Path $data 'root-client.ini')+'"'),'--connect-timeout=2','-Nse','"SELECT 1"') -WindowStyle Hidden -PassThru -Wait -RedirectStandardOutput (Join-Path $root 'logs\mysql-check.log') -RedirectStandardError (Join-Path $root 'logs\mysql-check.error.log')
    return $probe.ExitCode -eq 0
}
function Wait-Service($name,$port) {
    $entry=@($script:state.Processes | Where-Object Name -eq $name)[0]
    for($i=0;$i -lt 150;$i++) {
        if($null -eq (Get-Owned $entry)) { throw "$name non si e avviato: consulta logs\$name.log e logs\$name.error.log." }
        try {
            $status=Invoke-RestMethod "http://127.0.0.1:$port/internal/portable/status" -Headers @{'X-Internal-Key'=$config.InternalKey} -TimeoutSec 2
            $health=Invoke-RestMethod "http://127.0.0.1:$port/actuator/health" -TimeoutSec 2
            if($status.instance -eq $script:state.Instance -and $health.status -eq 'UP') { return }
        } catch {}
        Start-Sleep -Seconds 1
    }
    throw "Avvio $name non completato. Consulta logs e usa Ferma BollettaLAB prima di riprovare."
}
function Stop-All {
    if($null -eq $script:state) { Write-Host 'BollettaLAB e gia fermo.';return }
    # Prima verificare TUTTI i PID: nessun processo estraneo deve essere toccato.
    foreach($entry in @($script:state.Processes)) { Get-Owned $entry | Out-Null }
    $entries=@($script:state.Processes)
    [Array]::Reverse($entries)
    foreach($entry in $entries) {
        $process=Get-Owned $entry
        if($null -eq $process) { continue }
        if($entry.Name -eq 'mysql') {
            & (Join-Path $root 'mysql\bin\mysqladmin.exe') ("--defaults-extra-file="+(Join-Path $data 'root-client.ini')) '--connect-timeout=3' 'shutdown' 2>$null | Out-Null
            if($LASTEXITCODE -ne 0 -or -not $process.WaitForExit(30000)) { throw 'MySQL non si arresta: consulta logs. Il database non e stato terminato forzatamente.' }
        } elseif($entry.Name -eq 'gateway') {
            try { Invoke-RestMethod "$baseUrl/portable/shutdown" -Method Post -Headers @{'X-Portable-Token'=$script:state.Token} -TimeoutSec 5 | Out-Null } catch {}
            if(-not $process.WaitForExit(10000)) { Get-Owned $entry | Stop-Process -Force }
        } elseif($services -contains $entry.Name) {
            $port=8101+[Array]::IndexOf($services,$entry.Name)
            try { Invoke-RestMethod "http://127.0.0.1:$port/internal/portable/shutdown" -Method Post -Headers @{'X-Internal-Key'=$config.InternalKey} -TimeoutSec 5 | Out-Null } catch {}
            if(-not $process.WaitForExit(45000)) { Get-Owned $entry | Stop-Process -Force }
        } else {
            Get-Owned $entry | Stop-Process -Force
        }
    }
    Remove-Item -LiteralPath $stateFile -ErrorAction SilentlyContinue
    Write-Host 'BollettaLAB fermato. Dati conservati nella cartella data.'
}
try {
    if($env:OS -ne 'Windows_NT' -or -not [Environment]::Is64BitOperatingSystem) { throw 'Serve Windows 10/11 x64.' }
    try { $locked=$mutex.WaitOne(1000) } catch [Threading.AbandonedMutexException] { $locked=$true }
    if(-not $locked) { throw 'Avvio o arresto gia in corso. Attendi e riprova.' }
    if(Test-Path -LiteralPath $configFile) { $config=Get-Content -LiteralPath $configFile -Raw | ConvertFrom-Json }
    if(Test-Path -LiteralPath $stateFile) { $script:state=Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json }
    if($Action -eq 'Stop') { Stop-All;exit 0 }
    if($null -ne $script:state) {
        $alive=@($script:state.Processes | ForEach-Object { Get-Owned $_ } | Where-Object { $null -ne $_ })
        if($alive.Count -gt 0) {
            try { $instance=Invoke-RestMethod "$baseUrl/portable/status" -TimeoutSec 2 } catch { $instance='' }
            if($instance -eq $script:state.Instance -and $alive.Count -eq 7) {
                Write-Host 'BollettaLAB e gia aperto.'
                if(-not $NoBrowser) { Start-Process "$baseUrl/" };exit 0
            }
            throw 'Avvio precedente incompleto: usa Ferma BollettaLAB e poi Avvia BollettaLAB.'
        }
    }
    foreach($port in @(8091,8101,8102,8103,8104,8105,33079)) { Assert-Free $port }
    foreach($file in @($java,$mysqld,(Join-Path $root 'app\platform-common.jar'),(Join-Path $root 'web\index.html'))) {
        if(-not (Test-Path -LiteralPath $file)) { throw 'Pacchetto incompleto. Estrai tutto lo ZIP prima di avviare.' }
    }
    foreach($name in $services) { if(-not (Test-Path -LiteralPath (Join-Path $root "app\$name.jar"))) { throw "Manca app\$name.jar" } }
    foreach($folder in @('data','logs','data\mysql')) { New-Item -ItemType Directory -Force -Path (Join-Path $root $folder) | Out-Null }
    if($null -eq $config) {
        if(Test-Path -LiteralPath (Join-Path $data 'mysql\mysql')) { throw 'Database esistente senza install.json. Ripristina il file dalla tua copia di backup.' }
        $passwords=@{};foreach($name in $services) { $passwords[$name]=New-Secret }
        $config=[pscustomobject]@{AdminEmail='admin@bollettalab.local';AdminPassword=(New-Secret);InternalKey=(New-Secret);RootPassword=(New-Secret);DbPasswords=$passwords}
        Write-Utf8 $configFile ($config | ConvertTo-Json -Depth 8)
    }
    # Questi file restano locali; nessuna password e presente nel pacchetto distribuito.
    Write-Utf8 (Join-Path $data 'root-client.ini') "[client]`nhost=127.0.0.1`nport=33079`nprotocol=tcp`nuser=root`npassword=$($config.RootPassword)`n"
    $script:state=@{Instance=[Guid]::NewGuid().ToString();Token=(New-Secret);Processes=@()}
    Save-State
    $mysqlRoot=(Join-Path $root 'mysql').Replace('\','/')
    $mysqlData=(Join-Path $data 'mysql').Replace('\','/')
    $ini=Join-Path $data 'my.ini'
    Write-Utf8 $ini "[mysqld]`nbasedir=`"$mysqlRoot`"`ndatadir=`"$mysqlData`"`nbind-address=127.0.0.1`nport=33079`nmysqlx=0`nlocal-infile=0`nsecure-file-priv=NULL`ncharacter-set-server=utf8mb4`ncollation-server=utf8mb4_unicode_ci`ndefault-time-zone=+00:00`ninnodb-buffer-pool-size=128M`nmax-connections=100`n"
    $bootstrap=Join-Path $data 'bootstrap.sql'
    $installed=Join-Path $data 'mysql-ready.txt'
    if(-not (Test-Path -LiteralPath $installed)) {
        $sql="ALTER USER 'root'@'localhost' IDENTIFIED BY '$($config.RootPassword)';`n"
        foreach($name in $services) {
            $db=$name.Replace('-','_');$pw=$config.DbPasswords.$name
            $sql+="CREATE DATABASE IF NOT EXISTS ``$db`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`nCREATE USER IF NOT EXISTS '$db'@'localhost' IDENTIFIED BY '$pw';`nGRANT ALL ON ``$db``.* TO '$db'@'localhost';`n"
        }
        Write-Utf8 $bootstrap $sql
    }
    if(-not (Test-Path -LiteralPath (Join-Path $data 'mysql\mysql'))) {
        Write-Host 'Prima inizializzazione MySQL...'
        $initialization=Start-Owned 'mysql-init' $mysqld @(('"--defaults-file='+$ini+'"'),'--initialize-insecure','--console') @{}
        if(-not $initialization.WaitForExit(120000) -or $initialization.ExitCode -ne 0) { throw 'Inizializzazione MySQL fallita. Consulta logs\mysql-init.error.log.' }
    }
    $parameters=@(('"--defaults-file='+$ini+'"'),'--console')
    if(-not (Test-Path -LiteralPath $installed)) { $parameters+=('"--init-file='+$bootstrap+'"') }
    Write-Host 'Avvio database locale...'
    $mysql=Start-Owned 'mysql' $mysqld $parameters @{}
    $ready=$false
    for($i=0;$i -lt 90;$i++) {
        if($mysql.HasExited) { throw 'MySQL non si avvia: consulta logs\mysql.error.log.' }
        if(Test-Mysql) { $ready=$true;break };Start-Sleep -Seconds 1
    }
    if(-not $ready) { throw 'MySQL non risponde. Usa Ferma BollettaLAB e consulta logs.' }
    Write-Utf8 $installed 'MySQL inizializzato'
    Remove-Item -LiteralPath $bootstrap -ErrorAction SilentlyContinue
    # mysql-init e gia terminato: lo stato corrente contiene solo i sette processi persistenti.
    $script:state.Processes=@($script:state.Processes | Where-Object Name -ne 'mysql-init');Save-State
    foreach($name in $services) {
        $port=8101+[Array]::IndexOf($services,$name);$db=$name.Replace('-','_')
        $variables=@{SERVER_PORT=$port;SERVER_ADDRESS='127.0.0.1';SPRING_PROFILES_ACTIVE='dev';INTERNAL_KEY=$config.InternalKey;USERS_URL='http://127.0.0.1:8101';PAYMENT_URL='http://127.0.0.1:8102';DB_URL="jdbc:mysql://127.0.0.1:33079/$($db)?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";DB_USER=$db;DB_PASSWORD=$config.DbPasswords.$name;PUBLIC_URL=$baseUrl;COOKIE_SECURE='false';ADMIN_EMAIL=$config.AdminEmail;ADMIN_PASSWORD=$config.AdminPassword;PORTABLE_INSTANCE=$script:state.Instance}
        $parameters=@('-Xms48m','-Xmx384m','-Dfile.encoding=UTF-8','-jar',('"'+(Join-Path $root "app\$name.jar")+'"'),'--platform.portable-enabled=true','--server.shutdown=graceful','--spring.lifecycle.timeout-per-shutdown-phase=20s')
        foreach($domain in $services | Where-Object { $_ -ne 'utenti' }) {
            $domainPort=8101+[Array]::IndexOf($services,$domain)
            $parameters+="--platform.domain-urls.$domain=http://127.0.0.1:$domainPort"
        }
        Write-Host "Avvio $name..."
        Start-Owned $name $java $parameters $variables | Out-Null
    }
    foreach($name in $services) { Wait-Service $name (8101+[Array]::IndexOf($services,$name)) }
    Start-Owned 'gateway' $java @('-Xms16m','-Xmx128m','-cp',('"'+(Join-Path $root 'app\platform-common.jar')+'"'),'it.bollettalab.platform.portable.PortableGateway') @{PORTABLE_TOKEN=$script:state.Token;PORTABLE_INSTANCE=$script:state.Instance} | Out-Null
    $ready=$false
    for($i=0;$i -lt 30;$i++) {
        try { if((Invoke-RestMethod "$baseUrl/portable/status" -TimeoutSec 2) -eq $script:state.Instance) { $ready=$true;break } } catch {}
        Start-Sleep -Seconds 1
    }
    if(-not $ready) { throw 'Interfaccia non disponibile. Consulta logs\gateway.error.log.' }
    Write-Host "BollettaLAB pronto: $baseUrl"
    Write-Host "Admin: $($config.AdminEmail) - password in data\install.json"
    if(-not $NoBrowser) { Start-Process "$baseUrl/" }
    exit 0
} catch {
    Write-Host ('ERRORE: '+$_.Exception.Message) -ForegroundColor Red
    Write-Host 'I dati sono conservati. Usa Ferma BollettaLAB prima di riprovare.'
    exit 1
} finally {
    if($locked) { $mutex.ReleaseMutex() };$mutex.Dispose()
}
