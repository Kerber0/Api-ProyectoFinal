$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'archivos-locales.ps1')
$project=Split-Path -Parent $PSScriptRoot
$run=Join-Path $project ('.local/archivos-ensayo-'+[guid]::NewGuid().ToString('N'))
$source=Join-Path $run 'origen'
[void](New-Item -ItemType Directory -Path $source)
$key=[guid]::NewGuid().ToString()
$file=Join-Path $source $key
[IO.File]::WriteAllBytes($file,[Text.Encoding]::ASCII.GetBytes("%PDF-1.7`nEnsayo de recuperacion`n%%EOF`n"))
$document=@{id=1;clave=$key;proveedor='local';tamano=(Get-Item -LiteralPath $file).Length;sha256=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()}
$documents=ConvertFrom-Json ('['+($document|ConvertTo-Json)+']')
$copy=@(Copy-DocumentSnapshot $documents $source (Join-Path $run 'copia'))
$restored=@(Copy-DocumentSnapshot @($document) (Join-Path $run 'copia') (Join-Path $run 'restaurado'))
if ($copy.Count -ne 1 -or $restored.Count -ne 1 -or (Get-FileHash -LiteralPath (Join-Path $run "restaurado/$key")).Hash -ne (Get-FileHash -LiteralPath $file).Hash) { throw 'Recuperacion fallida.' }
$empty=ConvertFrom-Json '[]'
$emptyCopy=@(Copy-DocumentSnapshot $empty $source (Join-Path $run 'vacio'))
if ($emptyCopy.Count -ne 0) { throw 'El catalogo vacio invento archivos.' }
function Assert-Rejected([scriptblock]$Action) {
    $rejected=$false
    try { & $Action } catch { $rejected=$true }
    if (!$rejected) { throw 'Se acepto una copia que debia rechazarse.' }
}
Assert-Rejected { Copy-DocumentSnapshot @($document) $source (Join-Path $run 'copia') }
$bad=$document.Clone(); $bad.clave='../fuera'
Assert-Rejected { Copy-DocumentSnapshot @($bad) $source (Join-Path $run 'rechazo-ruta') }
$bad=$document.Clone(); $bad.proveedor='desconocido'
Assert-Rejected { Copy-DocumentSnapshot @($bad) $source (Join-Path $run 'rechazo-proveedor') }
$bad=$document.Clone(); $bad.sha256=('0'*64)
Assert-Rejected { Copy-DocumentSnapshot @($bad) $source (Join-Path $run 'rechazo-integridad') }
$bad=$document.Clone(); $bad.clave=[guid]::NewGuid().ToString()
Assert-Rejected { Copy-DocumentSnapshot @($bad) $source (Join-Path $run 'rechazo-ausente') }
Write-Host "Recuperacion documental con contenido, catalogo JSON vacio y cinco rechazos correctos. Evidencia conservada: $run"
