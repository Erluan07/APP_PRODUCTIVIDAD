// Pruebas de la lógica del PC. Ejecutar:  node tools/dance-qr/test.mjs
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const core = createRequire(import.meta.url)('./dance-core.js');
const enc = (s) => new TextEncoder().encode(s);

// SHA-256 y HMAC con vectores oficiales (NIST / RFC 4231).
assert.equal(core.hex(core.sha256(enc('abc'))), 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
assert.equal(core.hex(core.hmacSha256(new Uint8Array(20).fill(0x0b), enc('Hi There'))),
  'b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7');
assert.equal(core.hex(core.sha256(enc('a'.repeat(1000)))).length, 64); // mensajes de varios bloques

// Mismo vector que RulesTest.danceQrMatchesTheSharedTestVector (Kotlin).
const secret = core.textToSecret('AAAQ-EAYE-AUDA-OCAJ-BIFQ-YDIO-B4IB-CEQT');
assert.deepEqual([...secret], [...Array(20).keys()]);
assert.deepEqual([...core.textToSecret('aaaq eaye-auda-ocaj-bifq-ydio-b4ib-ceqt')], [...Array(20).keys()]);
assert.equal(core.textToSecret('AAA1-0000'), null);           // 1 y 0 no existen en base32
assert.equal(core.mac(secret, 29000000), '87659411df99');
assert.equal(core.payload(secret, 29000000 * 60000 + 5000), 'KRONOSD1|29000000|87659411df99');

// Detección de baile con fotogramas sintéticos 64x48.
const W = 64, H = 48;
const still = new Uint8Array(W * H).fill(100);
assert.equal(core.analyze(still, still, W, H).active, false);                   // nada se mueve
const wave = new Uint8Array(still); for (let y = 0; y < 10; y++) for (let x = 0; x < 10; x++) wave[y * W + x] = 220;
assert.equal(core.analyze(still, wave, W, H).active, false);                    // solo una mano en una esquina: no basta
const dance = new Uint8Array(W * H); for (let i = 0; i < dance.length; i++) dance[i] = (i % 7) * 30;
const r = core.analyze(still, dance, W, H);
assert.equal(r.active, true); assert.ok(r.cells >= 4);                          // movimiento por toda la imagen
const m = new core.DanceMeter(30);
for (let i = 0; i < 250; i++) m.update(true, 100);                              // 25 s bailando
assert.equal(m.update(false, 5000), false);                                      // parar no suma ni reinicia
assert.equal(m.update(true, 5000), true);                                        // llega a 30 s
console.log('OK: pruebas del QR bailable (PC) pasadas');
