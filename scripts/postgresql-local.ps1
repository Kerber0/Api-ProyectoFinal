param(
    [ValidateSet('comprobar','crear')][string]$Accion = 'comprobar',
    [Parameter(Mandatory=$true)][string]$BaseDatos,
    [string]$Servidor = '127.0.0.1',
    [int]$Puerto = 5432,
    [string]$Usuario = $env:DORNA_DB_ADMIN_USER,
    [switch]$DatosPrueba,
    [switch]$ConfirmarBaseNueva
)
$ErrorActionPreference = 'Stop'
if ($Servidor -notin @('127.0.0.1','localhost')) { throw 'Este script solo admite PostgreSQL local.' }
if ($BaseDatos -notmatch '^[a-zA-Z][a-zA-Z0-9_]{2,62}$') { throw 'Nombre de base de datos no válido.' }
if (!$Usuario) { throw 'Falta DORNA_DB_ADMIN_USER o el parámetro -Usuario.' }

$bin = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot '..\.local\postgres-dist') -Recurse -File -Filter psql.exe -ErrorAction SilentlyContinue |
    Where-Object FullName -Match '\\pgsql\\bin\\psql\.exe$' | Select-Object -First 1
$psql = if ($bin) { $bin.FullName } else { (Get-Command psql -ErrorAction Stop).Source }
$createdb = Join-Path (Split-Path $psql) 'createdb.exe'
$base = Join-Path $PSScriptRoot '..\src\main\resources\db\base de datos.sql'
$demo = Join-Path $PSScriptRoot '..\src\main\resources\db\base de datos de prueba.sql'
if (!(Test-Path -LiteralPath $base) -or !(Test-Path -LiteralPath $demo)) { throw 'Faltan los SQL de Dorna.' }

$previousPassword = $env:PGPASSWORD
try {
    $env:PGPASSWORD = $env:DORNA_DB_ADMIN_PASSWORD
    if ($Accion -eq 'crear') {
        if (!$ConfirmarBaseNueva) { throw 'Crear requiere -ConfirmarBaseNueva.' }
        if ($BaseDatos -in @('postgres','template0','template1','dorna_dev','dorna_test')) { throw 'Nombre protegido; utilice una base nueva.' }
        $exists = & $psql -X -h $Servidor -p $Puerto -U $Usuario -d postgres -Atc "SELECT 1 FROM pg_database WHERE datname='$BaseDatos'"
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo comprobar el servidor PostgreSQL.' }
        if ($exists -eq '1') { throw 'La base ya existe; no se modificará.' }
        & $createdb -h $Servidor -p $Puerto -U $Usuario $BaseDatos
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base.' }
        & $psql -X -v ON_ERROR_STOP=1 -h $Servidor -p $Puerto -U $Usuario -d $BaseDatos -f $base
        if ($LASTEXITCODE -ne 0) { throw 'Falló la creación de la estructura.' }
        if ($DatosPrueba) {
            & $psql -X -v ON_ERROR_STOP=1 -h $Servidor -p $Puerto -U $Usuario -d $BaseDatos -f $demo
            if ($LASTEXITCODE -ne 0) { throw 'Falló la carga de datos de prueba.' }
        }
    }
    $result = & $psql -X -h $Servidor -p $Puerto -U $Usuario -d $BaseDatos -Atc "SELECT current_database(),to_regclass('public.api_empresas') IS NOT NULL,(SELECT count(*) FROM dorna_demo_metadata)"
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo validar la base.' }
    $result
} finally {
    $env:PGPASSWORD = $previousPassword
}
