param(
    [string]$PostgresHome = $env:DORNA_PG_HOME,
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$local = Join-Path $project '.local'
$shell = Join-Path $PSHOME 'powershell.exe'
$utf8 = New-Object System.Text.UTF8Encoding($false)
if (!$JavaHome) {
    $candidate = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.jdks') -Directory |
        Where-Object { $_.Name -match '21' -and (Test-Path (Join-Path $_.FullName 'bin/javac.exe')) } |
        Select-Object -First 1
    if ($candidate) { $JavaHome = $candidate.FullName }
}
if (!$JavaHome -or !(Test-Path (Join-Path $JavaHome 'bin/javac.exe'))) {
    throw 'Se necesita un JDK 21; indicar -JavaHome.'
}
[void](Get-Command node -ErrorAction Stop)
$openApi = Join-Path $project 'src/main/resources/static/api/v1/openapi.json'
$openApiDocumentado = Join-Path $project 'docs/openapi_dorna_api5.json'
if (!(Test-Path -LiteralPath $openApiDocumentado) -or
    (Get-FileHash -LiteralPath $openApi -Algorithm SHA256).Hash -ne
    (Get-FileHash -LiteralPath $openApiDocumentado -Algorithm SHA256).Hash) {
    throw 'La copia documental de OpenAPI no coincide con el contrato publicado.'
}
& node (Join-Path $PSScriptRoot 'inventariar-arquitectura.mjs') --check
if ($LASTEXITCODE -ne 0) { throw 'El inventario de arquitectura no es reproducible.' }
# Este cierre requiere el entorno y las seis cuentas ya preparados. No crea claves.
foreach ($name in @('ADMIN','SOCIO','ADMIN-DORNA','ADMIN-SOLUTION','ADMIN-ANROD','ADMIN-KELTOR')) {
    if (!(Test-Path -LiteralPath (Join-Path $local "usuarios/$name.credential.xml"))) {
        throw 'Preparar antes los usuarios locales con scripts/crear-usuarios-locales.ps1.'
    }
}
$run = Join-Path $local ('entrega-' + [guid]::NewGuid().ToString('N'))
[void](New-Item -ItemType Directory -Path $run)
$manifest = [ordered]@{
    inicioUtc = [DateTime]::UtcNow.ToString('o')
    estado = 'en_curso'
    alcance = 'DEMO local Windows; no acredita ERP, Android ni Docker/Linux'
    etapas = @()
}
function Save-Manifest {
    [IO.File]::WriteAllText((Join-Path $run 'resumen.json'), ($manifest | ConvertTo-Json -Depth 6), $utf8)
}
function Invoke-Stage([string]$Name, [string]$Script, [string[]]$Options = @()) {
    $log = Join-Path $run "$Name.log"
    $stage = [ordered]@{ nombre = $Name; estado = 'en_curso'; registro = "$Name.log" }
    $manifest.etapas += $stage
    Save-Manifest
    Write-Host "Comprobando $Name..."
    # El proceso separado aísla los exit y las variables de los scripts existentes.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $shell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot $Script) @Options > $log 2>&1
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    $stage.codigoSalida = $code
    $stage.estado = if ($code -eq 0) { 'correcta' } else { 'fallida' }
    Save-Manifest
    if ($code -ne 0) { throw "Fallo en $Name. Revisar $log" }
}

try {
    $testsStarted = [DateTime]::UtcNow
    if ($PostgresHome) { $env:DORNA_PG_HOME = $PostgresHome }
    Invoke-Stage 'pruebas-jar' 'entorno-local.ps1' @('-Accion','verificar','-JavaHome',$JavaHome)
    $reports = @(Get-ChildItem -LiteralPath (Join-Path $project 'target/surefire-reports') -Filter 'TEST-*.xml')
    if (!$reports.Count) { throw 'No hay informes de pruebas.' }
    $totals = [ordered]@{ casos = 0; fallos = 0; errores = 0; omitidas = 0; suites = $reports.Count }
    foreach ($report in $reports) {
        if ($report.LastWriteTimeUtc -lt $testsStarted) { throw "Informe antiguo: $($report.Name). No se mezclan ejecuciones." }
        $suite = ([xml][IO.File]::ReadAllText($report.FullName)).testsuite
        $totals.casos += [int]$suite.tests
        $totals.fallos += [int]$suite.failures
        $totals.errores += [int]$suite.errors
        $totals.omitidas += [int]$suite.skipped
    }
    if ($totals.casos -eq 0 -or $totals.fallos -ne 0 -or $totals.errores -ne 0 -or $totals.omitidas -ne 0) {
        throw 'La entrega requiere pruebas ejecutadas sin fallos, errores ni omisiones.'
    }
    $manifest.pruebas = $totals
    $manifest.sha256Jar = (Get-FileHash -LiteralPath (Join-Path $project 'target/api-0.0.1-SNAPSHOT.jar') -Algorithm SHA256).Hash
    Invoke-Stage 'https-usuarios' 'ensayar-despliegue.ps1' @('-UsuariosIniciales','-JavaHome',$JavaHome)
    Invoke-Stage 'base-nueva' 'ensayar-preparacion-ci.ps1' @('-JavaHome',$JavaHome)
    Copy-Item -LiteralPath (Join-Path $local 'preparacion-ci-final.json') -Destination (Join-Path $run 'base-nueva.json')
    Invoke-Stage 'recuperacion' 'ensayar-recuperacion.ps1'
    $manifest.estado = 'correcta'
    Write-Host "Entrega local correcta: $($totals.casos) pruebas. Resumen: $run/resumen.json"
} catch {
    $manifest.estado = 'fallida'
    $manifest.error = $_.Exception.Message
    throw
} finally {
    $manifest.finUtc = [DateTime]::UtcNow.ToString('o')
    Save-Manifest
}
