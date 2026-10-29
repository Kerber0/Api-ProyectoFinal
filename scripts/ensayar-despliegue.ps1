param([string]$JavaHome = (Join-Path $env:USERPROFILE '.jdks/jbr-21.0.9'), [switch]$UsuariosIniciales)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'postgresql-local.ps1')
Assert-PgInstance
$project = Split-Path -Parent $PSScriptRoot
$local = Join-Path $project '.local'
$jar = Join-Path $project 'target/api-0.0.1-SNAPSHOT.jar'
if (!(Test-Path -LiteralPath $jar)) { throw 'Generar el JAR antes de ensayar el despliegue.' }
foreach ($port in @(8443, 9091)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { throw "Puerto $port ocupado; no se detendra otro proceso." }
}
$run = Join-Path $local ('despliegue-' + [guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Path $run)
$database = Get-PgCredential 'dorna_dev'
$environment = @{
    DORNA_DOCUMENTOS_RAIZ = (Join-Path $local 'documentos')
    DORNA_DB_URL = 'jdbc:postgresql://127.0.0.1:15432/dorna_dev'
    # Base previamente migrada: la aplicacion HTTPS solo recibe permisos DML.
    DORNA_DB_USER = $database.UserName
    DORNA_DB_PASSWORD = $database.GetNetworkCredential().Password
    DORNA_JWT_SECRET = (Import-Clixml -LiteralPath (Join-Path $local 'dorna_dev-signing.credential.xml')).GetNetworkCredential().Password
    DORNA_TLS_PASSWORD = [guid]::NewGuid().ToString('N')
    DORNA_TLS_KEYSTORE = (Join-Path $run 'localhost.p12')
    DORNA_BIND_ADDRESS = '127.0.0.1'
    DORNA_HTTPS_PORT = '8443'
    DORNA_MANAGEMENT_PORT = '9091'
    DORNA_SMOKE_BASE = 'https://localhost:8443'
    DORNA_SMOKE_MANAGEMENT = 'http://127.0.0.1:9091'
    DORNA_SMOKE_CA = (Join-Path $run 'localhost.pem')
    DORNA_SMOKE_PASSWORD = (Import-Clixml -LiteralPath (Join-Path $local 'demo-app.credential.xml')).GetNetworkCredential().Password
}
$saved = @{}
if ($UsuariosIniciales) {
    $clavesUsuarios = @{}
    foreach ($nombre in @('ADMIN','SOCIO','ADMIN-DORNA','ADMIN-SOLUTION','ADMIN-ANROD','ADMIN-KELTOR')) {
        $credencialUsuario = Import-Clixml -LiteralPath (Join-Path $local "usuarios/$nombre.credential.xml")
        $clavesUsuarios[$nombre] = $credencialUsuario.GetNetworkCredential().Password
    }
    $environment['DORNA_SMOKE_USERS_JSON'] = ($clavesUsuarios | ConvertTo-Json -Compress)
    $environment['DORNA_SMOKE_USER'] = 'ADMIN-DORNA'
    $environment['DORNA_SMOKE_PASSWORD'] = $clavesUsuarios['ADMIN-DORNA']
    $clavesUsuarios.Clear(); $credencialUsuario = $null
}
$application = $null
try {
    foreach ($key in $environment.Keys) {
        $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $environment[$key], 'Process')
    }
    $ErrorActionPreference = 'Continue'
    & "$JavaHome/bin/keytool.exe" -genkeypair -alias localhost -keyalg RSA -keysize 2048 -validity 2 -dname 'CN=localhost' -ext 'SAN=dns:localhost,ip:127.0.0.1' -storetype PKCS12 -keystore $env:DORNA_TLS_KEYSTORE -storepass:env DORNA_TLS_PASSWORD *> (Join-Path $run 'certificado.log')
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar el certificado temporal.' }
    $ErrorActionPreference = 'Continue'
    & "$JavaHome/bin/keytool.exe" -exportcert -rfc -alias localhost -keystore $env:DORNA_TLS_KEYSTORE -storepass:env DORNA_TLS_PASSWORD -file $env:DORNA_SMOKE_CA *>> (Join-Path $run 'certificado.log')
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo exportar el certificado publico.' }
    $application = Start-Process -FilePath "$JavaHome/bin/java.exe" -ArgumentList @('-jar', ('"' + $jar + '"'), '--spring.profiles.active=despliegue') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $run 'app.log') -RedirectStandardError (Join-Path $run 'app-error.log')
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($application.HasExited) { throw "La aplicacion no arranco. Revisar $run" }
        try {
            $health = Invoke-RestMethod 'http://127.0.0.1:9091/actuator/health/readiness' -TimeoutSec 1
            if ($health.status -eq 'UP') { $ready = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (!$ready) { throw "Readiness no disponible. Revisar $run" }
    & node (Join-Path $PSScriptRoot 'comprobar-despliegue.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la comprobacion de despliegue.' }
    Write-Host "Ensayo correcto. Registros privados: $run"
} finally {
    if ($application -and !$application.HasExited) { Stop-Process -Id $application.Id; $application.WaitForExit() }
    foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
}
