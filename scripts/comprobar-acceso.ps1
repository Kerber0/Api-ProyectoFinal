# Requiere entorno-local.ps1 ejecutar activo en otra terminal.
# Solo usa la API de loopback; no muestra claves ni tokens.
$ErrorActionPreference = 'Stop'
$baseUri = 'http://127.0.0.1:8080/api/v1'
$credentialPath = Join-Path (Split-Path -Parent $PSScriptRoot) '.local/demo-app.credential.xml'
if (!(Test-Path -LiteralPath $credentialPath)) { throw 'Primero ejecutar entorno-local.ps1 -Accion ejecutar.' }

function Assert-HttpStatus([string]$Path, [int]$Expected, [hashtable]$Headers = @{}) {
    $status = 0
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUri/$Path" -Headers $Headers -TimeoutSec 10
        $status = [int]$response.StatusCode
    } catch {
        if (!$_.Exception.Response) { throw 'No se pudo conectar con la API local.' }
        $status = [int]$_.Exception.Response.StatusCode
    }
    if ($status -ne $Expected) { throw "Respuesta inesperada para $Path : $status (esperado $Expected)." }
    Write-Host "$Path : HTTP $status correcto."
}

$credential = Import-Clixml -LiteralPath $credentialPath
$tokens = $null
try {
    Assert-HttpStatus 'empresas' 401
    $loginBody = @{ usuario = 'demo'; password = $credential.GetNetworkCredential().Password } | ConvertTo-Json -Compress
    $tokens = Invoke-RestMethod -Method Post -Uri "$baseUri/auth/login" -ContentType 'application/json' -Body $loginBody -TimeoutSec 10
    $headers = @{ Authorization = "Bearer $($tokens.accessToken)" }
    $perfil = Invoke-RestMethod -Uri "$baseUri/auth/me" -Headers $headers -TimeoutSec 10
    if ($perfil.usuario -ne 'demo') { throw 'Perfil inesperado.' }
    $empresas = @(Invoke-RestMethod -Uri "$baseUri/empresas" -Headers $headers -TimeoutSec 10)
    if ($empresas.Count -ne 1 -or $empresas[0].id -ne 1) { throw 'La cuenta DEMO no tiene la asignacion esperada.' }
    Write-Host 'Login y consulta: solo Dorna Metal [DEMO].'
    Assert-HttpStatus 'empresas/2' 404 $headers
    $clientes = Invoke-RestMethod -Uri "$baseUri/empresas/1/clientes?size=1" -Headers $headers -TimeoutSec 10
    if ($clientes.totalElements -ne 1 -or $clientes.items[0].id -ne 101) { throw 'Listado de clientes inesperado.' }
    $obras = Invoke-RestMethod -Uri "$baseUri/empresas/1/obras?size=1&sort=codigo,desc" -Headers $headers -TimeoutSec 10
    if ($obras.totalElements -ne 2 -or $obras.items[0].id -ne 1002) { throw 'Paginacion de obras inesperada.' }
    Assert-HttpStatus 'empresas/1/obras/1001' 200 $headers
    Assert-HttpStatus 'empresas/1/clientes/101/contactos' 200 $headers
    Assert-HttpStatus 'empresas/1/clientes/102' 404 $headers
    Assert-HttpStatus 'empresas/2/obras' 404 $headers
    Assert-HttpStatus 'empresas/1/obras?size=101' 400 $headers
    $presupuestos = Invoke-RestMethod -Uri "$baseUri/empresas/1/presupuestos?size=1&sort=numero,desc" -Headers $headers -TimeoutSec 10
    if ($presupuestos.totalElements -ne 2 -or $presupuestos.items[0].id -ne 4002) { throw 'Listado de presupuestos inesperado.' }
    $presupuesto = Invoke-RestMethod -Uri "$baseUri/empresas/1/presupuestos/4001" -Headers $headers -TimeoutSec 10
    if ($presupuesto.total -cne '3630.00' -or $presupuesto.formatoLineas -ne 'PLANO') { throw 'Detalle de presupuesto inesperado.' }
    $lineas = Invoke-RestMethod -Uri "$baseUri/empresas/1/presupuestos/4001/lineas?size=1" -Headers $headers -TimeoutSec 10
    if ($lineas.totalElements -ne 2) { throw 'Lineas de presupuesto inesperadas.' }
    Assert-HttpStatus "empresas/1/presupuestos/4001/lineas/$($lineas.items[0].id)" 200 $headers
    Assert-HttpStatus 'empresas/1/presupuestos/4003' 404 $headers
    Assert-HttpStatus 'empresas/1/presupuestos?size=101' 400 $headers
    Assert-HttpStatus 'empresas/1/obras/1001/documentos' 200 $headers
    Assert-HttpStatus 'empresas/1/obras/1001/documentos/7001' 200 $headers
    $descarga = Invoke-WebRequest -UseBasicParsing -Uri "$baseUri/empresas/1/obras/1001/documentos/7001/contenido" -Headers $headers -TimeoutSec 10
    $fixture = Join-Path (Split-Path -Parent $PSScriptRoot) 'src/main/resources/demo/documentos/1/7001/c58f5263-bde1-41d9-a592-f909e0dfb871'
    $esperado = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($fixture))
    if ([Convert]::ToBase64String([byte[]]$descarga.Content) -cne $esperado) { throw 'El contenido descargado no coincide con el archivo DEMO.' }
    if ($descarga.Headers['Content-Type'] -ne 'application/octet-stream' -or
        $descarga.Headers['Content-Disposition'] -ne 'attachment; filename="documento-7001"' -or
        $descarga.Headers['Cache-Control'] -ne 'no-store' -or
        $descarga.Headers['X-Content-Type-Options'] -ne 'nosniff') { throw 'Cabeceras de descarga inesperadas.' }
    Write-Host 'Descarga privada: bytes y cabeceras correctos.'
    Assert-HttpStatus 'empresas/1/obras/1001/documentos/7002/contenido' 404 $headers
    Assert-HttpStatus 'empresas/1/obras/1002/documentos/7001/contenido' 404 $headers
    Assert-HttpStatus 'empresas/1/obras/1001/documentos/7001/contenido' 401
    Assert-HttpStatus 'openapi.json' 200 $headers
    $operarios = Invoke-RestMethod -Uri "$baseUri/empresas/1/operarios?q=Uno&sort=nombre,asc" -Headers $headers -TimeoutSec 10
    if ($operarios.totalElements -ne 1 -or $operarios.items[0].id -ne 301) { throw 'Listado de operarios inesperado.' }
    $operario = Invoke-RestMethod -Uri "$baseUri/empresas/1/operarios/301" -Headers $headers -TimeoutSec 10
    $camposOperario = @('id', 'codigo', 'nombre', 'categoria', 'fechaAlta', 'fechaBaja')
    if (Compare-Object $camposOperario @($operario.PSObject.Properties.Name)) { throw 'La ficha laboral expone campos inesperados.' }
    $partes = Invoke-RestMethod -Uri "$baseUri/empresas/1/partes-mano-obra?desde=2026-09-10&hasta=2026-09-10&obra=1001&operario=301" -Headers $headers -TimeoutSec 10
    if ($partes.totalElements -ne 1 -or $partes.items[0].horasTrabajadas -cne '8.00') { throw 'Historico laboral inesperado.' }
    $parte = Invoke-RestMethod -Uri "$baseUri/empresas/1/partes-mano-obra/$($partes.items[0].id)" -Headers $headers -TimeoutSec 10
    $camposParte = @('id', 'obraId', 'operarioId', 'fecha', 'horaInicio', 'horaFin', 'horasTrabajadas')
    if (Compare-Object $camposParte @($parte.PSObject.Properties.Name)) { throw 'El parte expone campos inesperados.' }
    Assert-HttpStatus 'empresas/1/operarios/301/partes' 200 $headers
    Assert-HttpStatus 'empresas/1/obras/1001/partes' 200 $headers
    Assert-HttpStatus 'empresas/1/operarios/302' 404 $headers
    Assert-HttpStatus 'empresas/1/partes-mano-obra/81003' 404 $headers
    Assert-HttpStatus 'empresas/1/partes-mano-obra?desde=2026-02-30' 400 $headers
    Write-Host 'Consulta laboral: ficha minima, horas historicas y aislamiento correctos.'
    foreach ($caso in @(
        @{ Tipo = 'cliente'; Movimientos = 'cobros'; Primera = 5001; Ajena = 5004; MovimientoAjeno = 95004; Saldo = '1420.00'; MovimientoOctubre = '600.00' },
        @{ Tipo = 'proveedor'; Movimientos = 'pagos'; Primera = 6001; Ajena = 6004; MovimientoAjeno = 96004; Saldo = '468.00'; MovimientoOctubre = '200.00' }
    )) {
        $ruta = "empresas/1/facturas-$($caso.Tipo)"
        $parcial = $caso.Primera + 1
        $facturas = Invoke-RestMethod -Uri "$baseUri/${ruta}?size=1&sort=numero,desc" -Headers $headers -TimeoutSec 10
        if ($facturas.totalElements -ne 3 -or $facturas.items[0].id -ne ($caso.Primera + 2)) { throw 'Listado de facturas inesperado.' }
        Assert-HttpStatus "$ruta/$($caso.Primera)" 200 $headers
        $lineasFactura = Invoke-RestMethod -Uri "$baseUri/$ruta/$($caso.Primera)/lineas" -Headers $headers -TimeoutSec 10
        if ($lineasFactura.totalElements -ne 1) { throw 'Lineas de factura inesperadas.' }
        Assert-HttpStatus "$ruta/$($caso.Primera)/lineas/$($lineasFactura.items[0].id)" 200 $headers
        $saldo = Invoke-RestMethod -Uri "$baseUri/$ruta/$parcial/saldo" -Headers $headers -TimeoutSec 10
        if ($saldo.saldoAritmetico -cne $caso.Saldo -or $saldo.regla -ne 'DEMO_TOTAL_MENOS_MOVIMIENTOS') { throw 'Saldo aritmetico inesperado.' }
        $movimientos = Invoke-RestMethod -Uri "$baseUri/empresas/1/$($caso.Movimientos)?desde=2026-10-01&hasta=2026-10-01&factura=$parcial" -Headers $headers -TimeoutSec 10
        if ($movimientos.totalElements -ne 1 -or $movimientos.items[0].importe -cne $caso.MovimientoOctubre) { throw 'Movimiento efectivo inesperado.' }
        Assert-HttpStatus "empresas/1/$($caso.Movimientos)/$($movimientos.items[0].id)" 200 $headers
        Assert-HttpStatus "$ruta/$parcial/$($caso.Movimientos)" 200 $headers
        $vencimientos = Invoke-RestMethod -Uri "$baseUri/empresas/1/vencimientos-$($caso.Tipo)?desde=2026-10-15&hasta=2026-10-15" -Headers $headers -TimeoutSec 10
        if ($vencimientos.totalElements -ne 1 -or $vencimientos.items[0].saldoAritmetico -cne $caso.Saldo) { throw 'Vencimiento o acumulado inesperado.' }
        Assert-HttpStatus "$ruta/$($caso.Ajena)/saldo" 404 $headers
        Assert-HttpStatus "empresas/1/$($caso.Movimientos)/$($caso.MovimientoAjeno)" 404 $headers
    }
    Write-Host 'Economia: facturas, lineas, movimientos, vencimientos y saldos DEMO correctos.'
    foreach ($caso in @(
        @{ Tipo = 'cliente'; Movimientos = 'cobros'; Total = '4235.00'; Registrado = '1605.00'; Saldo = '2630.00'; Octubre = '600.00' },
        @{ Tipo = 'proveedor'; Movimientos = 'pagos'; Total = '1694.00'; Registrado = '742.00'; Saldo = '952.00'; Octubre = '200.00' }
    )) {
        $periodo = '?desde=2026-09-01&hasta=2026-10-31'
        $facturacion = Invoke-RestMethod -Uri "$baseUri/empresas/1/informes/facturacion-$($caso.Tipo)$periodo" -Headers $headers -TimeoutSec 10
        if ($facturacion.totales.facturas -ne 3 -or $facturacion.totales.total -cne $caso.Total -or
            $facturacion.meses.Count -ne 2 -or $facturacion.meses[1].valores.total -cne '0.00') { throw 'Informe de facturacion inesperado.' }
        $serie = Invoke-RestMethod -Uri "$baseUri/empresas/1/informes/$($caso.Movimientos)$periodo" -Headers $headers -TimeoutSec 10
        if ($serie.totales.movimientos -ne 3 -or $serie.totales.importe -cne $caso.Registrado -or
            $serie.meses[1].valores.importe -cne $caso.Octubre) { throw 'Informe de movimientos inesperado.' }
        $saldos = Invoke-RestMethod -Uri "$baseUri/empresas/1/informes/saldos-$($caso.Tipo)$periodo" -Headers $headers -TimeoutSec 10
        if ($saldos.totales.saldoAritmetico -cne $caso.Saldo -or $saldos.totales.movimientosRegistrados -cne $caso.Registrado -or
            $saldos.criterio -ne 'DEMO_FACTURAS_EMITIDAS_MENOS_MOVIMIENTOS_COMPLETOS') { throw 'Informe de saldos inesperado.' }
        Assert-HttpStatus "empresas/2/informes/facturacion-$($caso.Tipo)$periodo" 404 $headers
    }
    Assert-HttpStatus 'empresas/1/informes/cobros?desde=2026-09-01' 400 $headers
    Assert-HttpStatus 'empresas/1/informes/saldos-cliente?desde=2024-01-01&hasta=2026-01-01' 400 $headers
    Write-Host 'Informes: series mensuales, totales, meses vacios y aislamiento correctos.'
    $articulos = Invoke-RestMethod -Uri "$baseUri/empresas/1/articulos?size=1&sort=referencia,desc" -Headers $headers -TimeoutSec 10
    if ($articulos.totalElements -ne 3 -or $articulos.items[0].referencia -ne 'DEMO-ART-004') { throw 'Catalogo de articulos inesperado.' }
    Assert-HttpStatus 'empresas/1/articulos/DEMO-ART-001' 200 $headers
    Assert-HttpStatus 'empresas/1/almacenes' 200 $headers
    Assert-HttpStatus 'empresas/1/almacenes/101' 200 $headers
    foreach ($caso in @(
        @{ Almacen = 101; Referencia = 'DEMO-ART-001'; Cantidad = '6.50' },
        @{ Almacen = 102; Referencia = 'DEMO-ART-001'; Cantidad = '2.00' },
        @{ Almacen = 101; Referencia = 'DEMO-ART-004'; Cantidad = '0.00' }
    )) {
        $stock = Invoke-RestMethod -Uri "$baseUri/empresas/1/almacenes/$($caso.Almacen)/stock?q=$($caso.Referencia)" -Headers $headers -TimeoutSec 10
        if ($stock.totalElements -ne 1 -or $stock.items[0].cantidad -cne $caso.Cantidad -or
            !$stock.items[0].calculable -or $stock.items[0].movimientosPendientes -ne 0) { throw 'Saldo por almacen inesperado.' }
    }
    $traspasos = Invoke-RestMethod -Uri "$baseUri/empresas/1/almacenes/102/movimientos" -Headers $headers -TimeoutSec 10
    if ($traspasos.totalElements -ne 1 -or $traspasos.items[0].id -ne 97001 -or
        $traspasos.items[0].almacenDestinoId -ne 102 -or $traspasos.items[0].efectoOrigen -cne '-2.00') { throw 'Traspaso inesperado.' }
    Assert-HttpStatus 'empresas/1/movimientos-stock/97001' 200 $headers
    $ajustes = Invoke-RestMethod -Uri "$baseUri/empresas/1/movimientos-stock?desde=2026-09-13&hasta=2026-09-13" -Headers $headers -TimeoutSec 10
    if ($ajustes.totalElements -ne 1 -or $ajustes.items[0].cantidad -cne '-0.50') { throw 'Ajuste o filtro temporal inesperado.' }
    Assert-HttpStatus 'empresas/1/articulos/DEMO-ART-003' 404 $headers
    Assert-HttpStatus 'empresas/1/almacenes/201/stock' 404 $headers
    Assert-HttpStatus 'empresas/1/movimientos-stock/97003' 404 $headers
    Assert-HttpStatus 'empresas/1/movimientos-stock?desde=2026-02-30' 400 $headers
    Write-Host 'Stock: catalogos, saldos por almacen, ajuste, traspaso y aislamiento correctos.'
    $proveedores = Invoke-RestMethod -Uri "$baseUri/empresas/1/proveedores?q=Taller&sort=nombre,asc" -Headers $headers -TimeoutSec 10
    if ($proveedores.totalElements -ne 1 -or $proveedores.items[0].id -ne 201) { throw 'Catalogo de proveedores inesperado.' }
    $proveedor = Invoke-RestMethod -Uri "$baseUri/empresas/1/proveedores/201" -Headers $headers -TimeoutSec 10
    if (Compare-Object @('id', 'codigo', 'nombre', 'tipo', 'activo') @($proveedor.PSObject.Properties.Name)) { throw 'La ficha de proveedor expone campos inesperados.' }
    $pedidos = Invoke-RestMethod -Uri "$baseUri/empresas/1/pedidos-proveedor?proveedor=201&size=1&sort=numero,desc" -Headers $headers -TimeoutSec 10
    if ($pedidos.totalElements -ne 3 -or $pedidos.items[0].id -ne 98004) { throw 'Listado de pedidos inesperado.' }
    $pedido = Invoke-RestMethod -Uri "$baseUri/empresas/1/pedidos-proveedor/98002" -Headers $headers -TimeoutSec 10
    if ($pedido.total -cne '217.80' -or $pedido.descuento -cne '20.00' -or $pedido.proveedor.id -ne 201) { throw 'Cabecera de pedido inesperada.' }
    $pedidosProveedor = Invoke-RestMethod -Uri "$baseUri/empresas/1/proveedores/201/pedidos?desde=2026-09-15&hasta=2026-09-15&estado=ACEPTADO" -Headers $headers -TimeoutSec 10
    if ($pedidosProveedor.totalElements -ne 1 -or $pedidosProveedor.items[0].id -ne 98002) { throw 'Filtro de pedidos por proveedor inesperado.' }
    $lineasPedido = Invoke-RestMethod -Uri "$baseUri/empresas/1/pedidos-proveedor/98001/lineas?size=1&sort=id,desc" -Headers $headers -TimeoutSec 10
    if ($lineasPedido.totalElements -ne 2 -or $lineasPedido.items[0].id -ne 98102) { throw 'Lineas del pedido inesperadas.' }
    $lineaPedido = Invoke-RestMethod -Uri "$baseUri/empresas/1/pedidos-proveedor/98001/lineas/98101" -Headers $headers -TimeoutSec 10
    if ($lineaPedido.cantidad -cne '2.00' -or $lineaPedido.precioCoste -cne '100.00' -or $lineaPedido.importe -cne '200.00') { throw 'Importes historicos de linea inesperados.' }
    Assert-HttpStatus 'empresas/1/proveedores/202' 404 $headers
    Assert-HttpStatus 'empresas/1/pedidos-proveedor/98003' 404 $headers
    Assert-HttpStatus 'empresas/1/pedidos-proveedor/98001/lineas/98104' 404 $headers
    Assert-HttpStatus 'empresas/1/pedidos-proveedor?proveedor=0' 400 $headers
    Write-Host 'Compras: proveedores minimos, pedidos, lineas historicas, filtros y aislamiento correctos.'
    foreach ($caso in @(
        @{ Tipo = 'cliente'; Primero = 99001; Ajeno = 99003; Linea = 99201; LineaAjena = 99204; Total = 3; Precio = '150.00' },
        @{ Tipo = 'proveedor'; Primero = 99101; Ajeno = 99103; Linea = 99301; LineaAjena = 99304; Total = 4; Precio = '100.00' }
    )) {
        $rutaAlbaran = "empresas/1/albaranes-$($caso.Tipo)"
        $albaranes = Invoke-RestMethod -Uri "$baseUri/${rutaAlbaran}?size=1&sort=numero,asc" -Headers $headers -TimeoutSec 10
        if ($albaranes.totalElements -ne $caso.Total -or $albaranes.items[0].id -ne $caso.Primero) { throw 'Listado de albaranes inesperado.' }
        $albaran = Invoke-RestMethod -Uri "$baseUri/$rutaAlbaran/$($caso.Primero)" -Headers $headers -TimeoutSec 10
        if ($albaran.total -cne '242.00' -or $albaran.tipo -cne $caso.Tipo.ToUpperInvariant()) { throw 'Cabecera de albaran inesperada.' }
        $lineasAlbaran = Invoke-RestMethod -Uri "$baseUri/$rutaAlbaran/$($caso.Primero)/lineas?size=1" -Headers $headers -TimeoutSec 10
        if ($lineasAlbaran.totalElements -ne 2) { throw 'Lineas de albaran inesperadas.' }
        $lineaAlbaran = Invoke-RestMethod -Uri "$baseUri/$rutaAlbaran/$($caso.Primero)/lineas/$($caso.Linea)" -Headers $headers -TimeoutSec 10
        if ($lineaAlbaran.precio -cne $caso.Precio -or $lineaAlbaran.cantidad -cne '1.00') { throw 'Importes historicos de albaran inesperados.' }
        Assert-HttpStatus "$rutaAlbaran/$($caso.Ajeno)" 404 $headers
        Assert-HttpStatus "$rutaAlbaran/$($caso.Primero)/lineas/$($caso.LineaAjena)" 404 $headers
    }
    $albaranesFacturaProveedor = Invoke-RestMethod -Uri "$baseUri/empresas/1/facturas-proveedor/6001/albaranes?sort=fecha,asc" -Headers $headers -TimeoutSec 10
    if ($albaranesFacturaProveedor.totalElements -ne 2 -or $albaranesFacturaProveedor.items[0].facturaId -ne 6001) { throw 'Relacion albaran-factura proveedor inesperada.' }
    $albaranesFacturaCliente = Invoke-RestMethod -Uri "$baseUri/empresas/1/facturas-cliente/5001/albaranes" -Headers $headers -TimeoutSec 10
    if ($albaranesFacturaCliente.totalElements -ne 1 -or $albaranesFacturaCliente.items[0].facturaId -ne 5001) { throw 'Relacion albaran-factura cliente inesperada.' }
    $lineaIncompleta = Invoke-RestMethod -Uri "$baseUri/empresas/1/albaranes-proveedor/99105/lineas/99305" -Headers $headers -TimeoutSec 10
    if ($null -ne $lineaIncompleta.importe -or $null -ne $lineaIncompleta.referenciaArticulo) { throw 'La linea incompleta no conserva sus nulos.' }
    Assert-HttpStatus 'empresas/1/albaranes-cliente?desde=2026-02-30' 400 $headers
    Assert-HttpStatus 'empresas/1/albaranes-proveedor?factura=0' 400 $headers
    Write-Host 'Albaranes: documentos y lineas historicas, relaciones con obra/pedido, nulos y aislamiento correctos.'

    $incidencias = Invoke-RestMethod -Uri "$baseUri/empresas/1/incidencias?size=1&page=1&sort=fecha,desc" -Headers $headers -TimeoutSec 10
    if ($incidencias.totalElements -ne 3 -or $incidencias.items[0].id -ne 99502 -or
            $incidencias.items[0].PSObject.Properties.Name -contains 'descripcion') { throw 'Resumen de incidencias inesperado.' }
    $incidencia = Invoke-RestMethod -Uri "$baseUri/empresas/1/incidencias/99501" -Headers $headers -TimeoutSec 10
    if ($incidencia.estado -cne 'ABIERTA' -or $incidencia.prioridad -cne 'ALTA' -or $incidencia.operarioId -ne 301 -or
            $incidencia.descripcion -cne 'Revisar el ajuste del cierre de la puerta [DEMO].') { throw 'Detalle de incidencia inesperado.' }
    $sinOperario = Invoke-RestMethod -Uri "$baseUri/empresas/1/incidencias/99502" -Headers $headers -TimeoutSec 10
    if ($null -ne $sinOperario.operarioId) { throw 'La incidencia sin operario debe conservar null.' }
    $incidenciasObra = Invoke-RestMethod -Uri "$baseUri/empresas/1/obras/1001/incidencias?prioridad=ALTA&operario=301" -Headers $headers -TimeoutSec 10
    if ($incidenciasObra.totalElements -ne 2) { throw 'Incidencias por obra inesperadas.' }
    $incidenciasOperario = Invoke-RestMethod -Uri "$baseUri/empresas/1/operarios/301/incidencias?estado=ABIERTA&desde=2026-09-10&hasta=2026-09-10" -Headers $headers -TimeoutSec 10
    if ($incidenciasOperario.totalElements -ne 1 -or $incidenciasOperario.items[0].id -ne 99501) { throw 'Incidencias por operario inesperadas.' }
    $obraSinIncidencias = Invoke-RestMethod -Uri "$baseUri/empresas/1/obras/1002/incidencias" -Headers $headers -TimeoutSec 10
    if ($obraSinIncidencias.totalElements -ne 0) { throw 'La obra debe tener una pagina vacia de incidencias.' }
    Assert-HttpStatus 'empresas/1/incidencias/99503' 404 $headers
    Assert-HttpStatus 'empresas/2/incidencias' 404 $headers
    Assert-HttpStatus 'empresas/1/operarios/302/incidencias' 404 $headers
    Assert-HttpStatus 'empresas/1/incidencias?desde=2026-02-30' 400 $headers
    Assert-HttpStatus 'empresas/1/incidencias?prioridad=ALTA&prioridad=BAJA' 400 $headers
    Assert-HttpStatus 'empresas/1/obras/1001/incidencias?obra=1001' 400 $headers
    Write-Host 'Incidencias: resumen minimo, descripcion de detalle, operario nulo, filtros, relaciones y aislamiento correctos.'

    $eventos = Invoke-RestMethod -Uri "$baseUri/empresas/1/eventos?desde=2026-09-12&hasta=2026-09-12" -Headers $headers -TimeoutSec 10
    if ($eventos.totalElements -ne 1 -or $eventos.items[0].id -ne 99601 -or
            $eventos.items[0].fechaInicio -cne '2026-09-10T08:00:00' -or
            $eventos.items[0].PSObject.Properties.Name -contains 'descripcion') { throw 'Periodo o resumen de eventos inesperado.' }
    $evento = Invoke-RestMethod -Uri "$baseUri/empresas/1/eventos/99602" -Headers $headers -TimeoutSec 10
    if ($null -ne $evento.fechaFin -or $null -ne $evento.obraId -or $null -ne $evento.descripcion) { throw 'El evento debe conservar nulos.' }
    $eventosObra = Invoke-RestMethod -Uri "$baseUri/empresas/1/obras/1001/eventos" -Headers $headers -TimeoutSec 10
    if ($eventosObra.totalElements -ne 2) { throw 'Eventos por obra inesperados.' }
    $tareas = Invoke-RestMethod -Uri "$baseUri/empresas/1/tareas?desde=2026-09-10&hasta=2026-09-10" -Headers $headers -TimeoutSec 10
    if ($tareas.totalElements -ne 2 -or $tareas.items[0].PSObject.Properties.Name -contains 'descripcion') { throw 'Listado de tareas inesperado.' }
    $tarea = Invoke-RestMethod -Uri "$baseUri/empresas/1/tareas/99702" -Headers $headers -TimeoutSec 10
    if ($null -ne $tarea.fechaLimite -or $null -ne $tarea.operarioId -or $null -ne $tarea.descripcion) { throw 'La tarea debe conservar nulos.' }
    $tareasObra = Invoke-RestMethod -Uri "$baseUri/empresas/1/obras/1001/tareas" -Headers $headers -TimeoutSec 10
    if ($tareasObra.totalElements -ne 2) { throw 'Tareas por obra inesperadas.' }
    $tareasOperario = Invoke-RestMethod -Uri "$baseUri/empresas/1/operarios/301/tareas?estado=FINALIZADA" -Headers $headers -TimeoutSec 10
    if ($tareasOperario.totalElements -ne 1 -or $tareasOperario.items[0].id -ne 99704) { throw 'Tareas por operario inesperadas.' }
    Assert-HttpStatus 'empresas/1/eventos/99603' 404 $headers
    Assert-HttpStatus 'empresas/1/tareas/99703' 404 $headers
    Assert-HttpStatus 'empresas/1/eventos?desde=2026-02-30' 400 $headers
    Assert-HttpStatus 'empresas/1/obras/1001/eventos?obra=1001' 400 $headers
    Write-Host 'Calendario y tareas: periodos, horas locales, detalle, nulos, padres e aislamiento correctos.'
    $anterior = $headers
    $refreshBody = @{ refreshToken = $tokens.refreshToken } | ConvertTo-Json -Compress
    $tokens = Invoke-RestMethod -Method Post -Uri "$baseUri/auth/refresh" -ContentType 'application/json' -Body $refreshBody -TimeoutSec 10
    $headers = @{ Authorization = "Bearer $($tokens.accessToken)" }
    Assert-HttpStatus 'auth/me' 401 $anterior
    Assert-HttpStatus 'auth/me' 200 $headers
    [void](Invoke-RestMethod -Method Post -Uri "$baseUri/auth/logout" -Headers $headers -TimeoutSec 10)
    $tokens = $null
    Assert-HttpStatus 'auth/me' 401 $headers
    Assert-HttpStatus 'empresas/1/obras/1001/documentos/7001/contenido' 401 $headers
    Assert-HttpStatus 'empresas/1/partes-mano-obra' 401 $headers
    Assert-HttpStatus 'empresas/1/facturas-cliente/5002/saldo' 401 $headers
    Assert-HttpStatus 'empresas/1/informes/cobros?desde=2026-09-01&hasta=2026-10-31' 401 $headers
    Assert-HttpStatus 'empresas/1/almacenes/101/stock' 401 $headers
    Assert-HttpStatus 'empresas/1/pedidos-proveedor' 401 $headers
    Assert-HttpStatus 'empresas/1/albaranes-cliente' 401 $headers
    Assert-HttpStatus 'empresas/1/albaranes-proveedor' 401 $headers
    Assert-HttpStatus 'empresas/1/incidencias' 401 $headers
    Assert-HttpStatus 'empresas/1/incidencias/99501' 401 $headers
    Assert-HttpStatus 'empresas/1/eventos' 401 $headers
    Assert-HttpStatus 'empresas/1/tareas' 401 $headers
    Write-Host 'Comprobacion HTTP completa: consultas DEMO de los bloques 2 a 7 y 9, calendario, tareas, aislamiento, renovacion y cierre de sesion.'
} finally {
    if ($tokens) {
        try {
            [void](Invoke-RestMethod -Method Post -Uri "$baseUri/auth/logout" -Headers @{ Authorization = "Bearer $($tokens.accessToken)" } -TimeoutSec 10)
        } catch { Write-Warning 'No se pudo cerrar la sesion de comprobacion.' }
    }
    $credential = $null
    $tokens = $null
    $loginBody = $null
    $refreshBody = $null
    $headers = $null
    $anterior = $null
}
