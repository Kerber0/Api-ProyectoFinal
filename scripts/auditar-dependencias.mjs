// Consulta publica de coordenadas Maven; nunca envia fuentes, datos ni credenciales.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

export function dependencias(tree) {
  const packages = new Map();
  function visit(node, root = false) {
    if (!node || typeof node !== 'object' || Array.isArray(node)) throw new Error('Nodo Maven invalido.');
    for (const key of ['groupId', 'artifactId', 'version']) {
      if (typeof node[key] !== 'string' || !node[key].trim()) throw new Error('Coordenadas Maven incompletas.');
    }
    if (node.children !== undefined && !Array.isArray(node.children)) throw new Error('Arbol Maven invalido.');
    if (!root) {
      if (!['compile', 'runtime', 'test', 'provided', 'system'].includes(node.scope)) throw new Error('Ambito Maven desconocido.');
      const name = `${node.groupId}:${node.artifactId}`;
      const key = `${name}@${node.version}`;
      const item = packages.get(key) || { name, version: node.version, scopes: [] };
      if (!item.scopes.includes(node.scope)) item.scopes.push(node.scope);
      packages.set(key, item);
    }
    for (const child of node.children || []) visit(child);
  }
  visit(tree, true);
  if (!packages.size) throw new Error('Inventario vacio: no se acredita una auditoria.');
  return [...packages.values()].sort((a, b) => `${a.name}@${a.version}`.localeCompare(`${b.name}@${b.version}`));
}

export async function consultar(packages, post) {
  const results = packages.map(item => ({ ...item, avisos: [] }));
  for (let start = 0; start < packages.length; start += 100) {
    let pending = packages.slice(start, start + 100).map((item, index) => ({
      index: start + index, seen: new Set(),
      query: { package: { ecosystem: 'Maven', name: item.name }, version: item.version }
    }));
    for (let page = 0; pending.length; page++) {
      if (page >= 100) throw new Error('Paginacion OSV incompleta.');
      const response = await post({ queries: pending.map(item => item.query) });
      if (!Array.isArray(response?.results) || response.results.length !== pending.length) {
        throw new Error('Respuesta OSV incompleta.');
      }
      const next = [];
      response.results.forEach((result, index) => {
        if (!result || typeof result !== 'object' || Array.isArray(result) || result.error) throw new Error('Resultado OSV invalido.');
        const item = pending[index];
        if (result.vulns !== undefined && !Array.isArray(result.vulns)) throw new Error('Avisos OSV invalidos.');
        for (const vuln of result.vulns || []) {
          if (typeof vuln?.id !== 'string' || !/^[A-Za-z0-9._-]+$/.test(vuln.id)) throw new Error('Identificador OSV invalido.');
          if (!results[item.index].avisos.some(existing => existing.id === vuln.id)) {
            results[item.index].avisos.push({ id: vuln.id, url: `https://osv.dev/vulnerability/${vuln.id}` });
          }
        }
        if (result.next_page_token) {
          if (typeof result.next_page_token !== 'string' || item.seen.has(result.next_page_token)) throw new Error('Paginacion OSV repetida o invalida.');
          item.seen.add(result.next_page_token);
          next.push({ ...item, query: { ...item.query, page_token: result.next_page_token } });
        }
      });
      pending = next;
    }
  }
  return results;
}

async function main() {
  const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const target = resolve(root, 'target');
  mkdirSync(target, { recursive: true });
  const output = resolve(target, 'auditoria-dependencias.json');
  const report = {
    fechaUtc: new Date().toISOString(), estado: 'incompleto',
    fuente: 'https://api.osv.dev/v1/querybatch',
    alcance: 'Dependencias Maven resueltas, incluidas pruebas. Excluye plugins de build, JDK, PostgreSQL y sistema operativo.',
    limite: 'Sin avisos conocidos no significa ausencia de vulnerabilidades ni confirma la existencia de la version en el repositorio publico.'
  };
  const save = () => writeFileSync(output, JSON.stringify(report, null, 2) + '\n');
  save(); // Un fallo nunca deja un resultado anterior como si fuera vigente.
  try {
    const inventory = readFileSync(resolve(target, 'dependencias.json'));
    report.sha256Inventario = createHash('sha256').update(inventory).digest('hex');
    report.sha256Pom = createHash('sha256').update(readFileSync(resolve(root, 'pom.xml'))).digest('hex');
    const packages = dependencias(JSON.parse(inventory.toString('utf8')));
    report.paquetes = await consultar(packages, async body => {
      const response = await fetch(report.fuente, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body), signal: AbortSignal.timeout(30000)
      });
      if (!response.ok) throw new Error(`OSV devolvio HTTP ${response.status}.`);
      return response.json();
    });
    report.totalPaquetes = report.paquetes.length;
    report.paquetesConAvisos = report.paquetes.filter(item => item.avisos.length).length;
    report.estado = report.paquetesConAvisos ? 'requiere-revision' : 'sin-avisos-conocidos';
    save();
    console.log(`OSV: ${report.totalPaquetes} paquetes; ${report.paquetesConAvisos} con avisos. Informe: target/auditoria-dependencias.json`);
    if (report.paquetesConAvisos) process.exitCode = 1;
  } catch (error) {
    report.error = error.message;
    save();
    console.error(`Auditoria incompleta: ${error.message}`);
    process.exitCode = 2;
  }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
