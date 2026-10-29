param([string]$JavaHome = (Join-Path $env:USERPROFILE '.jdks/jbr-21.0.9'))
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'postgresql-local.ps1')
$project = Split-Path -Parent $PSScriptRoot
$local = Join-Path $project '.local'
$jar = Join-Path $project 'target/api-0.0.1-SNAPSHOT.jar'
function New-RandomHex {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [BitConverter]::ToString($bytes).Replace('-', '')
}
if (!(Test-Path -LiteralPath $jar)) { throw 'Ejecutar entorno-local.ps1 -Accion verificar para generar el JAR.' }
# Comprueba la identidad de PostgreSQL y aplica solo migraciones nuevas.
& "$env:SystemRoot/System32/WindowsPowerShell/v1.0/powershell.exe" -NoProfile -File (Join-Path $PSScriptRoot 'entorno-local.ps1') -Accion preparar
if ($LASTEXITCODE -ne 0) { throw 'No se pudo preparar la instancia local.' }
$directory = Join-Path $local 'usuarios'
[void](New-Item -ItemType Directory -Force -Path $directory)
$claves = @{}
foreach ($nombre in @('ADMIN','SOCIO','ADMIN-DORNA','ADMIN-SOLUTION','ADMIN-ANROD','ADMIN-KELTOR')) {
    $path = Join-Path $directory "$nombre.credential.xml"
    if (!(Test-Path -LiteralPath $path)) {
        $clave = New-RandomHex
        $credential = New-Object Management.Automation.PSCredential($nombre, (ConvertTo-SecureString $clave -AsPlainText -Force))
        $credential | Export-Clixml -LiteralPath $path
    }
    $credential = Import-Clixml -LiteralPath $path
    if ($credential.UserName -cne $nombre) { throw "Credencial local inesperada para $nombre" }
    $claves[$nombre] = $credential.GetNetworkCredential().Password
}
$database = Get-PgCredential 'dorna_dev'
$environment = @{
    DORNA_DEV_DB_URL = 'jdbc:postgresql://127.0.0.1:15432/dorna_dev'
    DORNA_DEV_DB_USER = $database.UserName
    DORNA_DEV_DB_PASSWORD = $database.GetNetworkCredential().Password
    # Firma efimera: este proceso solo provisiona y no emite sesiones.
    DORNA_JWT_SECRET = (New-RandomHex)
    DORNA_INITIAL_USERS_JSON = ($claves | ConvertTo-Json -Compress)
}
$saved = @{}
try {
    foreach ($key in $environment.Keys) {
        $saved[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $environment[$key], 'Process')
    }
    $ErrorActionPreference = 'Continue'
    & "$JavaHome/bin/java.exe" -jar $jar '--spring.profiles.active=provisionar' *> (Join-Path $local 'usuarios-provision.log')
    $result = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($result -ne 0) { throw 'No se pudieron provisionar las cuentas. Revisar .local/usuarios-provision.log.' }
    Write-Host 'Usuarios preparados. ADMIN: control total; SOCIO: lectura global; ADMIN-<EMPRESA>: lectura de su empresa.'
    Write-Host 'Credenciales individuales cifradas con Windows en .local/usuarios/; no se imprimen ni se incluyen en Git.'
} finally {
    foreach ($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key, $saved[$key], 'Process') }
    $claves.Clear(); $environment.Clear(); $credential = $database = $clave = $null
}
