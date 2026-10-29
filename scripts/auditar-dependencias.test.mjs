import { test } from 'node:test';
import assert from 'node:assert/strict';
import { dependencias, consultar } from './auditar-dependencias.mjs';

const node = (artifactId, scope = 'compile', children = []) => ({ groupId: 'org.example', artifactId, version: '1.0', scope, children });
const pkg = { name: 'org.example:lib', version: '1.0', scopes: ['compile'] };

test('incluye transitivas y pruebas, agrupa versiones y no envia el proyecto raiz', () => {
  const tree = node('privado', '', [node('lib', 'compile', [node('transitiva', 'runtime')]), node('lib', 'test')]);
  const result = dependencias(tree);
  assert.equal(result.length, 2);
  assert.deepEqual(result[0].scopes, ['compile', 'test']);
  assert.equal(result[1].name, 'org.example:transitiva');
  assert.equal(result.some(item => item.name.endsWith(':privado')), false);
});
test('inventarios vacios, incompletos o malformados no pasan como limpios', () => {
  for (const tree of [node('root'), { children: [] }, node('root', '', [{ ...node('lib'), version: '' }]), node('root', '', [node('lib', 'desconocido')])]) {
    assert.throws(() => dependencias(tree));
  }
});
test('paginacion conserva la correspondencia y elimina avisos duplicados', async () => {
  let calls = 0;
  const result = await consultar([pkg, { ...pkg, name: 'org.example:otra' }], async body => {
    if (calls++ === 0) return { results: [{ vulns: [{ id: 'GHSA-demo' }], next_page_token: 'siguiente' }, {}] };
    assert.equal(body.queries.length, 1);
    assert.equal(body.queries[0].package.name, pkg.name);
    assert.equal(body.queries[0].page_token, 'siguiente');
    return { results: [{ vulns: [{ id: 'GHSA-demo' }, { id: 'CVE-2026-12345' }] }] };
  });
  assert.equal(calls, 2);
  assert.equal(result[0].avisos.length, 2);
  assert.deepEqual(result[1].avisos, []);
});
test('respuesta truncada o malformada no se interpreta como ausencia de avisos', async () => {
  for (const response of [{}, { results: [] }, { results: [null] }, { results: [{ error: 'failed' }] }, { results: [{ vulns: [{}] }] }]) {
    await assert.rejects(consultar([pkg], async () => response));
  }
});
test('paginacion repetida y fallo de red invalidan la auditoria', async () => {
  await assert.rejects(consultar([pkg], async () => ({ results: [{ next_page_token: 'igual' }] })), /Paginacion/);
  await assert.rejects(consultar([pkg], async () => { throw new Error('sin red'); }), /sin red/);
});
test('lotes grandes mantienen todos los paquetes y sus resultados', async () => {
  const packages = Array.from({ length: 205 }, (_, index) => ({ ...pkg, name: `org.example:lib${index}` }));
  const sizes = [];
  const result = await consultar(packages, async body => {
    sizes.push(body.queries.length);
    return { results: body.queries.map(query => ({ vulns: [{ id: query.package.name.replace(':', '-') }] })) };
  });
  assert.deepEqual(sizes, [100, 100, 5]);
  assert.equal(result[204].avisos[0].id, 'org.example-lib204');
});
