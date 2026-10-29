import assert from 'node:assert/strict';
import https from 'node:https';
import http from 'node:http';
import { readFileSync } from 'node:fs';
import { performance } from 'node:perf_hooks';
import { setTimeout as delay } from 'node:timers/promises';
const base = new URL(process.env.DORNA_SMOKE_BASE || 'https://localhost:8443');
assert.equal(base.protocol, 'https:');
const ca = process.env.DORNA_SMOKE_CA ? readFileSync(process.env.DORNA_SMOKE_CA) : undefined;
const password = process.env.DORNA_SMOKE_PASSWORD;
const usuarioPrincipal = process.env.DORNA_SMOKE_USER || 'demo';
assert.ok(password, 'Se necesita la clave de una cuenta DEMO autorizada.');
const management = process.env.DORNA_SMOKE_MANAGEMENT || 'http://127.0.0.1:9091';
function requestOnce(url, {method = 'GET', token, body, chunked = false, contentType = 'application/json'} = {}) {
  return new Promise((resolve, reject) => {
    const headers = {};
    if (token) headers.Authorization = 'Bearer ' + token;
    if (body !== undefined) {
      headers['Content-Type'] = contentType;
      if (!chunked) headers['Content-Length'] = Buffer.byteLength(body);
    }
    const target = new URL(url, base);
    const req = (target.protocol === 'https:' ? https : http).request(target,
        {method, headers, ca, timeout: 10000}, res => {
          const chunks = [];
          res.on('data', chunk => chunks.push(chunk));
          res.on('end', () => {
            const text = Buffer.concat(chunks).toString('utf8');
            let json;
            try { json = JSON.parse(text); } catch { /* HTTP sin TLS puede devolver texto. */ }
            resolve({status:res.statusCode, headers:res.headers, json, text});
          });
        });
    req.on('error', reject);
    req.on('timeout', () => req.destroy(new Error('Tiempo de espera agotado')));
    if (chunked && body !== undefined) req.write(body);
    req.end(chunked ? undefined : body);
  });
}
async function request(url, options) {
  const result = await requestOnce(url, options);
  if (result.status !== 429) return result;
  // El recorrido funcional respeta el cupo real; el ensayo de 429 usa requestOnce.
  assert.equal(result.json.codigo, 'LIMITE_SOLICITUDES');
  const seconds = Number(result.headers['retry-after']);
  assert.ok(Number.isInteger(seconds) && seconds > 0 && seconds <= 60);
  console.log(`Cupo HTTPS alcanzado; reintento funcional en ${seconds} s.`);
  await delay(seconds * 1000);
  return requestOnce(url, options);
}
let token;
try {
  for (const path of ['/actuator/health/liveness', '/actuator/health/readiness']) {
    const result = await request(management + path);
    assert.equal(result.status, 200);
    assert.deepEqual(result.json, {status:'UP'});
  }
  assert.equal((await request(management + '/actuator/metrics')).status, 200);
  assert.notEqual((await request(management + '/actuator/env')).status, 200);
  assert.notEqual((await request('/actuator/metrics')).status, 200);
  const unauthenticated = await request('/api/v1/empresas');
  assert.equal(unauthenticated.status, 401);
  assert.match(unauthenticated.headers['strict-transport-security'], /max-age=/);
  assert.equal(unauthenticated.json.requestId, unauthenticated.headers['x-request-id']);
  const login = await request('/api/v1/auth/login', {method:'POST',body:JSON.stringify({usuario:usuarioPrincipal,password})});
  assert.equal(login.status, 200);
  token = login.json.accessToken;
  assert.equal(login.headers['cache-control'], 'no-store');
  assert.equal(login.json.tokenType, 'Bearer');
  assert.equal(typeof login.json.expiresIn, 'number');
  const spec = await request('/api/v1/openapi.json', {token});
  assert.equal(spec.status, 200);
  assert.equal(spec.json.openapi, '3.1.1');
  assert.equal(spec.json.info.version, '0.20.5-candidato');
  assert.ok(spec.json.paths['/auditoria']?.get);
  assert.ok(spec.json.paths['/empresas/{empresa}/tesoreria/{tipo}/movimientos']?.post);
  assert.ok(spec.json.paths['/empresas/{empresa}/tesoreria/{tipo}/movimientos/{id}/reversion']?.put);
  assert.ok(spec.json.paths['/empresas/{empresa}/facturacion/{tipo}/documentos/{id}/emision']?.put);
  assert.ok(spec.json.paths['/empresas/{empresa}/facturacion/{tipo}/documentos/{id}/rectificacion']?.post);
  assert.ok(spec.json.paths['/empresas/{empresa}/obras/{obra}/observaciones']?.post);
  assert.ok(spec.json.paths['/empresas/{empresa}/obras/{obra}/observaciones/{id}/resolucion']?.put);
  assert.ok(spec.json.paths['/empresas/{empresa}/mi-trabajo/obras/{obra}/observaciones']?.post);
  assert.ok(spec.json.paths['/empresas/{empresa}/ficha']?.get);
  assert.ok(spec.json.paths['/empresas/{empresa}/clientes/{id}/visibilidad/{destino}']?.put);
  assert.ok(spec.json.paths['/empresas/{empresa}/proveedores/{id}/visibilidad/{destino}']?.put);
  assert.ok(spec.json.paths['/empresas/{empresa}/dashboard']?.get);
  assert.ok(spec.json.paths['/empresas/{empresa}/tesoreria/prevision']?.get);
  assert.ok(spec.json.paths['/empresas/{empresa}/gestion/{tipo}/{id}/contactos']?.post);
  assert.ok(spec.json.paths['/empresas/{empresa}/gestion/{tipo}/{id}/direcciones']?.post);
  assert.ok(spec.json.paths['/empresas/{empresa}/gestion/incidencias']?.post);
  assert.ok(spec.json.paths['/empresas/{empresa}/gestion/tareas']?.post);
  assert.ok(spec.json.paths['/empresas/{empresa}/gestion/eventos']?.post);
  if (process.env.DORNA_SMOKE_USERS_JSON) {
    const claves = JSON.parse(process.env.DORNA_SMOKE_USERS_JSON);
    const empresas = {'ADMIN':[1,2,3,4], 'SOCIO':[1,2,3,4], 'ADMIN-DORNA':[1],
      'ADMIN-SOLUTION':[2], 'ADMIN-ANROD':[3], 'ADMIN-KELTOR':[4]};
    for (const [usuario, ids] of Object.entries(empresas)) {
      const sesion = await request('/api/v1/auth/login', {method:'POST',body:JSON.stringify({usuario,password:claves[usuario]})});
      assert.equal(sesion.status, 200, `Login de ${usuario}`);
      const tokenUsuario = sesion.json.accessToken;
      try {
        const auditoria = await request('/api/v1/auditoria?size=1', {token:tokenUsuario});
        assert.equal(auditoria.status, usuario === 'ADMIN' ? 200 : 404);
        assert.match(auditoria.headers['cache-control'], /no-store/);
        if (auditoria.status === 200) assert.ok(Array.isArray(auditoria.json.items));
        const seriesGestion = await request('/api/v1/empresas/1/gestion/series/OBRA?size=1', {token:tokenUsuario});
        assert.equal(seriesGestion.status, usuario === 'ADMIN' ? 200 : 404, `Gestion privada para ${usuario}`);
        if (seriesGestion.status === 200) assert.ok(Array.isArray(seriesGestion.json.items));
        const fichaGestion = await request('/api/v1/empresas/1/gestion/clientes/101', {token:tokenUsuario});
        assert.equal(fichaGestion.status, usuario === 'ADMIN' ? 200 : 404);
        assert.match(fichaGestion.headers['cache-control'], /no-store/);
        if (fichaGestion.status === 200) assert.equal(typeof fichaGestion.json.version, 'number');
        if (usuario !== 'ADMIN') {
          const rechazoGestion = await request('/api/v1/empresas/1/gestion/clientes', {
            method:'POST',token:tokenUsuario,body:JSON.stringify({codigo:'RECHAZADO',identidad:{tipo:'EMPRESA',razonSocial:'Ensayo',nif:'RECHAZADO'},descuento:'0'})
          });
          assert.equal(rechazoGestion.status, 404, `Escritura de gestion sin permiso para ${usuario}`);
        }
        const lista = await request('/api/v1/empresas', {token:tokenUsuario});
        assert.equal(lista.status, 200);
        assert.deepEqual(lista.json.map(e=>e.id), ids, `Empresas de ${usuario}`);
        const usuarios = await request('/api/v1/usuarios?size=1', {token:tokenUsuario});
        assert.equal(usuarios.status, ['ADMIN','SOCIO'].includes(usuario) ? 200 : 404);
        if (usuarios.status === 200) {
          assert.deepEqual(Object.keys(usuarios.json.items[0]).sort(), ['activo','id','usuario']);
        }
        const efectivos = await request(`/api/v1/empresas/${ids[0]}/permisos`, {token:tokenUsuario});
        assert.equal(efectivos.status, 200);
        assert.equal(efectivos.json.find(p => p.permiso === 'USUARIOS').nivel, usuario === 'ADMIN' ? 'ADMIN' : 'LECTURA');
        const asignaciones = await request('/api/v1/empresas/1/operarios/301/asignaciones?desde=2026-10-01&hasta=2026-10-31', {token:tokenUsuario});
        assert.equal(asignaciones.status, ids.includes(1) ? 200 : 404, `Asignaciones para ${usuario}`);
        if (asignaciones.status === 200) assert.ok(Array.isArray(asignaciones.json.items));
        const ausencias = await request('/api/v1/empresas/1/operarios/301/ausencias?desde=2026-10-01&hasta=2026-10-31', {token:tokenUsuario});
        assert.equal(ausencias.status, usuario === 'ADMIN' ? 200 : 404, `Privacidad de ausencias para ${usuario}`);
        if (ausencias.status === 200) assert.ok(Array.isArray(ausencias.json.items));
        const observaciones = await request('/api/v1/empresas/1/obras/1001/observaciones?estado=ABIERTA&size=1', {token:tokenUsuario});
        const documentos = await request('/api/v1/empresas/1/documentos/obras/1001?size=1', {token:tokenUsuario});
        assert.equal(documentos.status, ids.includes(1) ? 200 : 404, `Documentos para ${usuario}`);
        if (documentos.status === 200) assert.ok(Array.isArray(documentos.json.items));
        if (usuario !== 'ADMIN') {
          const rechazo = await request('/api/v1/empresas/1/documentos/obras/1001?nombre=ensayo.pdf', {
            method:'POST', token:tokenUsuario, contentType:'application/pdf', body:'%PDF-1.7\n' + ' '.repeat(20000) + '\n%%EOF\n'
          });
          assert.equal(rechazo.status, 404, `Subida binaria sin escritura para ${usuario}`);
        }
        for (const tipo of ['CLIENTE','PROVEEDOR']) {
          for (const recurso of ['vencimientos','movimientos']) {
            const tesoreria = await request(`/api/v1/empresas/1/tesoreria/${tipo}/${recurso}?size=1`, {token:tokenUsuario});
            assert.equal(tesoreria.status, ids.includes(1) ? 200 : 404, `Tesoreria ${tipo}/${recurso} para ${usuario}`);
            if (tesoreria.status === 200) assert.ok(Array.isArray(tesoreria.json.items));
          }
          if (usuario !== 'ADMIN') {
            const rechazo = await request(`/api/v1/empresas/1/tesoreria/${tipo}/movimientos`, {
              method:'POST',token:tokenUsuario,body:JSON.stringify({clave:'00000000-0000-0000-0000-000000000001',fecha:'2026-10-15',importe:'1.00',metodo:'EFECTIVO',aplicaciones:[{vencimientoId:1,importe:'1.00'}]})
            });
            assert.equal(rechazo.status,404, `Rechazo de escritura de tesoreria para ${usuario}`);
          }
          for (const recurso of ['series','documentos']) {
            const gestion = await request(`/api/v1/empresas/1/facturacion/${tipo}/${recurso}?size=1`, {token:tokenUsuario});
            assert.equal(gestion.status, ids.includes(1) ? 200 : 404, `Facturacion ${tipo}/${recurso} para ${usuario}`);
            if (gestion.status === 200) assert.ok(Array.isArray(gestion.json.items));
          }
        }
        assert.equal(observaciones.status, ids.includes(1) ? 200 : 404, `Observaciones para ${usuario}`);
        if (observaciones.status === 200) {
          assert.ok(Array.isArray(observaciones.json.items));
          for (const item of observaciones.json.items) {
            assert.equal(item.estado, 'ABIERTA');
            assert.equal(Object.hasOwn(item, 'texto'), false);
            assert.equal(Object.hasOwn(item, 'resolucion'), false);
          }
        }
        if (usuario !== 'ADMIN') {
          // Cuerpos validos para verificar autorizacion; no se crean datos con las cuentas lectoras.
          assert.equal((await request('/api/v1/empresas/1/obras/1001/observaciones', {
            method:'POST',token:tokenUsuario,body:JSON.stringify({texto:'Ensayo sin permiso de escritura'})
          })).status, 404);
        }
        const perfil = await request('/api/v1/auth/me', {token:tokenUsuario});
        assert.equal(perfil.status, 200);
        const lecturaGlobal = ['ADMIN','SOCIO'].includes(usuario);
        for (const path of [`/usuarios/${perfil.json.id}`, `/usuarios/${perfil.json.id}/accesos`, '/roles', '/permisos']) {
          const result = await request('/api/v1' + path, {token:tokenUsuario});
          assert.equal(result.status, lecturaGlobal ? 200 : 404, `${path} para ${usuario}`);
          if (!lecturaGlobal) continue;
          if (path === `/usuarios/${perfil.json.id}`) {
            assert.deepEqual(result.json, {id:perfil.json.id, usuario:perfil.json.usuario, activo:true});
          } else {
            assert.ok(Array.isArray(result.json) && result.json.length > 0, path);
          }
          if (path === '/roles') {
            const trabajador = result.json.find(rol => rol.nombre === 'TRABAJADOR');
            assert.equal(trabajador?.global, false);
            assert.deepEqual(trabajador.permisos, [{permiso:'EMPRESA', nivel:'LECTURA'}, {permiso:'OBSERVACIONES', nivel:'ESCRITURA'}, {permiso:'TRABAJO_PROPIO', nivel:'LECTURA'}]);
          }
          if (path === '/permisos') {
            assert.ok(result.json.some(permiso => permiso.nombre === 'USUARIOS' && permiso.activo));
          }
        }
        // Escritura idempotente sobre el estado actual: no cambia cuentas ni claves locales.
        const cuenta = await request(`/api/v1/usuarios/${perfil.json.id}`, {
          method:'PUT', token:tokenUsuario, body:JSON.stringify({usuario:perfil.json.usuario,activo:true})
        });
        assert.equal(cuenta.status, usuario === 'ADMIN' ? 200 : 404);
        for (const empresa of [1,2,3,4]) {
          const result = await request('/api/v1/empresas/'+empresa, {token:tokenUsuario});
          assert.equal(result.status, ids.includes(empresa) ? 200 : 404, `Ambito de ${usuario}`);
          const ficha = await request('/api/v1/empresas/'+empresa+'/ficha', {token:tokenUsuario});
          assert.equal(ficha.status, ids.includes(empresa) ? 200 : 404, `Ficha fiscal de ${usuario}`);
          if (ids.includes(empresa)) {
            assert.equal(ficha.json.id, empresa);
            assert.equal(ficha.json.datosFiscales.nifCif, 'DEMO-EMP-' + String(empresa).padStart(3, '0'));
          }
        }
        // Concesiones ya existentes: comprueban el PUT real sin ampliar la visibilidad DEMO.
        for (const [recurso, id] of [['clientes', 101], ['proveedores', 201]]) {
          const path = `/api/v1/empresas/1/${recurso}/${id}/visibilidad/1`;
          for (let intento = 0; intento < 2; intento++) {
            const concesion = await request(path, {method:'PUT', token:tokenUsuario});
            assert.equal(concesion.status, usuario === 'ADMIN' ? 204 : 404, `Visibilidad de ${recurso} para ${usuario}`);
            if (usuario === 'ADMIN') assert.equal(concesion.text, '');
          }
          assert.equal((await request(path, {method:'PUT'})).status, 401);
        }
      } finally {
        assert.equal((await request('/api/v1/auth/logout', {method:'POST',token:tokenUsuario})).status, 204);
        delete claves[usuario];
      }
    }
    console.log('Seis usuarios iniciales: login, fichas, empresas autorizadas, aislamiento, cuentas, asignaciones de roles y obras, privacidad de ausencias, observaciones, facturacion, tesoreria y rechazo de escritura lectora, roles, permisos efectivos, visibilidad, idempotencia y logout correctos por HTTPS.');
  }
  for (const [tipo, total, importe, saldo] of [['cliente','4235.00','1605.00','2630.00'], ['proveedor','1694.00','742.00','952.00']]) {
    for (const [ruta, campo, esperado] of [['facturacion-'+tipo,'total',total], [tipo === 'cliente' ? 'cobros' : 'pagos','importe',importe], ['saldos-'+tipo,'saldoAritmetico',saldo]]) {
      const result = await request('/api/v1/empresas/1/informes/'+ruta+'?desde=2026-09-01&hasta=2026-10-31', {token});
      assert.equal(result.status, 200);
      assert.equal(result.json.totales[campo], esperado);
      assert.equal(result.json.meses.length, 2);
      assert.equal(result.json.desde, '2026-09-01');
    }
  }
  const dashboard = await request('/api/v1/empresas/1/dashboard?desde=2026-09-01&hasta=2026-10-31', {token});
  assert.equal(dashboard.status, 200);
  assert.equal(dashboard.json.facturacionClientes.total, '4235.00');
  assert.equal(dashboard.json.facturacionProveedores.total, '1694.00');
  assert.equal(dashboard.json.cobrosRegistrados.importe, '1605.00');
  assert.equal(dashboard.json.pagosRegistrados.importe, '742.00');
  assert.equal(dashboard.json.obrasTotales, 2);
  assert.equal(dashboard.json.presupuestosTotales, 2);
  const prevision = await request('/api/v1/empresas/1/tesoreria/prevision?desde=2026-01-01&hasta=2026-12-31', {token});
  assert.equal(prevision.status, 200);
  assert.equal(prevision.json.totalRegistros, 0);
  assert.equal(prevision.json.saldoFirmadoTotal, '0.00');
  assert.deepEqual(prevision.json.items, []);
  assert.equal((await request('/api/v1/empresas/2/obras', {token})).status, 404);
  const page = await request('/api/v1/empresas/1/obras?size=1', {token});
  assert.equal(page.json.items.length, 1); assert.equal(page.json.totalElements, 2);
  const company = '/api/v1/empresas/1';
  const get = async path => {
    const result = await request(company + path, {token});
    assert.equal(result.status, 200, path);
    return result.json;
  };
  const stock = await get('/almacenes/101/stock?q=DEMO-ART-001');
  assert.equal(stock.items[0].cantidad, '6.50');
  assert.equal((await get('/pedidos-proveedor/98001')).total, '484.00');
  assert.equal((await get('/facturas-proveedor/6001/albaranes')).totalElements, 2);
  assert.equal((await get('/facturas-cliente/5001/albaranes')).items[0].facturaId, 5001);
  assert.equal((await get('/albaranes-proveedor?factura=6001')).totalElements, 2);
  assert.equal((await get('/albaranes-cliente/99002')).facturaId, null);
  assert.equal((await get('/albaranes-cliente/99001')).total, '242.00');
  assert.equal((await get('/albaranes-proveedor/99105/lineas/99305')).importe, null);
  assert.equal((await get('/incidencias/99502')).operarioId, null);
  assert.equal((await get('/obras/1001/incidencias?prioridad=ALTA')).totalElements, 2);
  const eventos = await get('/eventos?desde=2026-09-12&hasta=2026-09-12');
  assert.equal(eventos.totalElements, 1);
  assert.equal(eventos.items[0].id, 99601);
  assert.equal(eventos.items[0].fechaInicio, '2026-09-10T08:00:00');
  assert.equal(Object.hasOwn(eventos.items[0], 'descripcion'), false);
  const evento = await get('/eventos/99602');
  assert.equal(evento.fechaFin, null); assert.equal(evento.descripcion, null);
  assert.equal((await get('/obras/1001/eventos')).totalElements, 2);
  assert.equal((await get('/tareas?desde=2026-09-10&hasta=2026-09-10')).totalElements, 2);
  const tarea = await get('/tareas/99702');
  assert.equal(tarea.fechaLimite, null); assert.equal(tarea.operarioId, null);
  assert.equal((await get('/obras/1001/tareas')).totalElements, 2);
  assert.equal((await get('/operarios/301/tareas?estado=FINALIZADA')).items[0].id, 99704);
  for (const path of ['/pedidos-proveedor/98003', '/albaranes-cliente/99003', '/incidencias/99503', '/eventos/99603', '/tareas/99703']) {
    assert.equal((await request(company + path, {token})).status, 404, path);
  }
  for (const path of ['/eventos?desde=2026-02-30', '/obras/1001/eventos?obra=1001', '/tareas?prioridad=ALTA&prioridad=BAJA']) {
    assert.equal((await request(company + path, {token})).status, 400, path);
  }
  console.log('Modulos del bloque 9 por HTTPS: stock, compras, albaranes, incidencias, calendario y tareas correctos.');
  const samples = [];
  await Promise.all(Array.from({length:4}, async () => {
    for (let i=0; i<25; i++) {
      const start = performance.now();
      assert.equal((await request('/api/v1/auth/me', {token})).status, 200);
      samples.push(performance.now()-start);
    }
  }));
  samples.sort((a,b)=>a-b);
  console.log(`Carga local: 100 consultas HTTPS, concurrencia 4; p95=${samples[94].toFixed(1)} ms, max=${samples[99].toFixed(1)} ms.`);
  for (const chunked of [false, true]) {
    const result = await request('/api/v1/auth/refresh', {method:'POST',body:'x'.repeat(16385),chunked});
    assert.equal(result.status, 413);
    assert.equal(result.json.codigo, 'CUERPO_DEMASIADO_GRANDE');
    assert.equal(result.json.requestId, result.headers['x-request-id']);
  }
  const refreshed = await request('/api/v1/auth/refresh', {method:'POST', body:JSON.stringify({refreshToken:login.json.refreshToken})});
  assert.equal(refreshed.status, 200);
  const previous = token;
  token = refreshed.json.accessToken;
  assert.equal((await request('/api/v1/auth/me', {token:previous})).status, 401);
  assert.equal((await request('/api/v1/auth/logout', {method:'POST',token})).status, 204);
  assert.equal((await request('/api/v1/auth/me', {token})).status, 401);
  for (const path of ['/incidencias', '/eventos', '/tareas']) {
    assert.equal((await request(company + path, {token})).status, 401, path);
  }
  token = undefined;
  let throttled = false;
  for (let i=0; i<25; i++) {
    const result = await requestOnce('/api/v1/auth/refresh', {method:'POST',body:'{"refreshToken":"invalido"}'});
    if (result.status === 429) {
      assert.ok(Number(result.headers['retry-after']) > 0);
      assert.equal(result.json.codigo, 'LIMITE_SOLICITUDES');
      throttled = true; break;
    }
    assert.equal(result.status, 401);
  }
  assert.ok(throttled, 'Debe activarse el limite de escrituras.');
  const plain = new URL(base); plain.protocol = 'http:';
  try { assert.notEqual((await request(plain)).status, 200); }
  catch (error) { if (!['ECONNRESET','EPROTO'].includes(error.code)) throw error; }
  console.log('HTTPS con certificado validado, salud privada, contrato JSON, permisos, limites 413/429 y sesiones: correcto.');
} finally {
  if (token) await request('/api/v1/auth/logout', {method:'POST',token}).catch(()=>{});
}
