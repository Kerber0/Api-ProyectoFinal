# Copia de los objetos referenciados en una instantanea de metadatos.
# El llamador debe detener escrituras y guardar tambien el dump de PostgreSQL.
function Assert-DocumentPath([string]$Path) {
    $resolved=[IO.Path]::GetFullPath($Path)
    $current=$resolved
    while ($current) {
        if (Test-Path -LiteralPath $current) {
            if ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw 'No se admiten enlaces o junctions en el almacen documental.'
            }
        }
        $parent=[IO.Path]::GetDirectoryName($current)
        if ($parent -eq $current) { break }
        $current=$parent
    }
    return $resolved
}
function Copy-DocumentSnapshot([object[]]$Documentos,[string]$Origen,[string]$Destino) {
    $sourceRoot=Assert-DocumentPath $Origen
    $targetRoot=Assert-DocumentPath $Destino
    if ($sourceRoot -eq $targetRoot -or (Test-Path -LiteralPath $targetRoot)) { throw 'El destino documental debe ser nuevo e independiente.' }
    [void](New-Item -ItemType Directory -Path $targetRoot)
    $manifest=@()
    foreach ($documento in $Documentos) {
        if ($documento.proveedor -cne 'local' -or $documento.clave -cnotmatch '^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$' -or
            $documento.sha256 -cnotmatch '^[a-f0-9]{64}$' -or [long]$documento.tamano -lt 1 -or [long]$documento.tamano -gt 5242880) {
            throw 'Metadatos documentales no compatibles con la recuperacion local.'
        }
        $sourceFile=Assert-DocumentPath (Join-Path $sourceRoot $documento.clave)
        $targetFile=Join-Path $targetRoot $documento.clave
        if (!(Test-Path -LiteralPath $sourceFile -PathType Leaf) -or (Get-Item -LiteralPath $sourceFile).Length -ne $documento.tamano -or
            (Get-FileHash -LiteralPath $sourceFile -Algorithm SHA256).Hash.ToLowerInvariant() -cne $documento.sha256) {
            throw 'Falta un archivo referenciado o no coincide con sus metadatos.'
        }
        Copy-Item -LiteralPath $sourceFile -Destination $targetFile -ErrorAction Stop
        if ((Get-FileHash -LiteralPath $targetFile -Algorithm SHA256).Hash.ToLowerInvariant() -cne $documento.sha256 -or
            (Get-Item -LiteralPath $targetFile).Length -ne $documento.tamano) { throw 'Copia documental incompleta.' }
        $manifest+=@{id=$documento.id; clave=$documento.clave; tamano=$documento.tamano; sha256=$documento.sha256; proveedor=$documento.proveedor}
    }
    return $manifest
}
