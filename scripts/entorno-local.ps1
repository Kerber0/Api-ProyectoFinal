param([ValidateSet('comprobar','ejecutar')][string]$Accion = 'comprobar')
$ErrorActionPreference = 'Stop'
$required = @('DORNA_DEV_DB_URL','DORNA_DEV_DB_USER','DORNA_DEV_DB_PASSWORD','DORNA_JWT_SECRET')
foreach ($name in $required) {
    if (![Environment]::GetEnvironmentVariable($name)) { throw "Falta la variable $name." }
}
if ($env:DORNA_DEV_DB_URL -notmatch '^jdbc:postgresql://(?:127\.0\.0\.1|localhost):[0-9]+/[a-zA-Z0-9_]+(?:\?.*)?$') {
    throw 'Desarrollo local requiere PostgreSQL en loopback.'
}
if ($Accion -eq 'comprobar') {
    [pscustomobject]@{ Perfil='dev'; BaseConfigurada=$true; SecretoJwtConfigurado=$true }
    return
}
Push-Location (Join-Path $PSScriptRoot '..')
try {
    & .\mvnw.cmd -B spring-boot:run '-Dspring-boot.run.profiles=dev'
    if ($LASTEXITCODE -ne 0) { throw 'La API terminó con error.' }
} finally { Pop-Location }
