param([string]$OutputRoot='dist-windows',[string]$JdkHome=$env:JAVA_HOME,[string]$MysqlVersion='8.4.11')
$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
$repo=Split-Path $PSScriptRoot -Parent
if($env:OS -ne 'Windows_NT' -or -not [Environment]::Is64BitOperatingSystem) { throw 'Compilare su Windows x64.' }
if(-not (Test-Path -LiteralPath "$JdkHome\bin\jlink.exe")) { throw 'Serve JDK 17 sul PC che prepara il pacchetto, non sul PC del collega.' }
if(-not [IO.Path]::IsPathRooted($OutputRoot)) { $OutputRoot=Join-Path $repo $OutputRoot }
$package=Join-Path $OutputRoot 'BollettaLAB-Windows-x64'
if(Test-Path -LiteralPath $package) { throw 'Destinazione gia esistente: scegliere una cartella nuova per non cancellare dati.' }
New-Item -ItemType Directory -Force -Path $package,(Join-Path $package 'app'),(Join-Path $package 'licenses') | Out-Null
foreach($name in @('utenti','pagamento','luce','luce-business','gas')) {
    Copy-Item -LiteralPath (Join-Path $repo "services\$name\target\$name-1.0.0.jar") -Destination (Join-Path $package "app\$name.jar")
}
Copy-Item -LiteralPath (Join-Path $repo 'platform-common\target\platform-common-1.0.0.jar') -Destination (Join-Path $package 'app\platform-common.jar')
Copy-Item -Path (Join-Path $repo 'windows\*') -Destination $package
Copy-Item -LiteralPath (Join-Path $repo 'frontend\dist') -Destination (Join-Path $package 'web') -Recurse
Copy-Item -LiteralPath (Join-Path $repo 'docs\TEST_MANUALI.md') -Destination (Join-Path $package 'DATI-DI-TEST.md')
& "$JdkHome\bin\jlink.exe" --add-modules java.se,jdk.httpserver,jdk.crypto.ec,jdk.unsupported,jdk.management,jdk.charsets,jdk.localedata --include-locales=en,it --strip-debug --no-header-files --no-man-pages --compress=2 --output (Join-Path $package 'runtime')
if($LASTEXITCODE -ne 0) { throw 'Creazione runtime fallita.' }
# Dipendenze MSVC app-local: prese dalla cartella ufficiale redistribuibile di Visual Studio.
# Il destinatario non deve installare Visual Studio o un redistributable di sistema.
$vswhere=Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
if(-not (Test-Path -LiteralPath $vswhere)) { throw 'Sul builder servono i runtime redistribuibili Visual Studio 2022 x64.' }
$vs=& $vswhere -latest -products '*' -property installationPath
$crt=Get-ChildItem (Join-Path $vs 'VC\Redist\MSVC') -Directory | ForEach-Object {
    $x64=Join-Path $_.FullName 'x64'
    if(Test-Path -LiteralPath $x64) { Get-ChildItem $x64 -Directory | Where-Object Name -Match '^Microsoft\.VC\d+\.CRT$' }
} | Sort-Object FullName -Descending | Select-Object -First 1
if($null -eq $crt) { throw 'DLL runtime MSVC x64 non trovate nel builder.' }
Get-ChildItem $crt.FullName -Filter '*.dll' | Copy-Item -Destination (Join-Path $package 'runtime\bin')
$archive=Join-Path $OutputRoot "mysql-$MysqlVersion-winx64.zip"
$uri="https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-$MysqlVersion-winx64.zip"
Write-Host "Download MySQL Community $MysqlVersion dal sito ufficiale..."
Invoke-WebRequest -Uri $uri -OutFile $archive -UseBasicParsing
Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $OutputRoot 'mysql-download')
Move-Item -LiteralPath (Join-Path $OutputRoot "mysql-download\mysql-$MysqlVersion-winx64") -Destination (Join-Path $package 'mysql')
Get-ChildItem $crt.FullName -Filter '*.dll' | Copy-Item -Destination (Join-Path $package 'mysql\bin')
@("MySQL Community $MysqlVersion", "Sorgenti ufficiali della stessa versione: https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-$MysqlVersion.tar.gz", "Licenze e copyright MySQL: cartella mysql del pacchetto.", 'Runtime Java: Eclipse Temurin, licenze in runtime\legal.', 'MSVC x64: DLL redistribuibili app-local dalla cartella Visual Studio VC\Redist\MSVC; copyright Microsoft.', "Cartella CRT usata: $($crt.Name)") | Set-Content (Join-Path $package 'licenses\COMPONENTI.txt') -Encoding UTF8
$revision=git -C $repo rev-parse HEAD
if($LASTEXITCODE -ne 0) { throw 'Revisione git non disponibile.' }
@('BollettaLAB Windows x64',"Commit: $revision","MySQL: $MysqlVersion", "SHA256 download MySQL: $((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash)","Creato UTC: $([DateTime]::UtcNow.ToString('o'))","Java: $(Get-Content "$JdkHome\release" -Raw)") | Set-Content (Join-Path $package 'VERSIONE.txt') -Encoding UTF8
# Uno ZIP pulito: credenziali e database verranno generati SOLO all'avvio sul PC finale.
if(Test-Path -LiteralPath (Join-Path $package 'data')) { throw 'Non distribuire cartelle data.' }
$zip=Join-Path $OutputRoot 'BollettaLAB-Windows-x64.zip'
Compress-Archive -LiteralPath $package -DestinationPath $zip
(Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash+'  BollettaLAB-Windows-x64.zip' | Set-Content (Join-Path $OutputRoot 'SHA256.txt') -Encoding ASCII
Write-Host "Pacchetto creato: $zip"
