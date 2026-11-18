/*
 * Lógica del QR bailable (sin dependencias). Debe calcular EXACTAMENTE lo mismo que
 * app/src/main/java/com/kronos/focus/domain/alarm/DanceQr.kt. test.mjs usa el mismo vector de prueba que RulesTest.kt.
 */
(function (root) {
  'use strict';

  var ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

  /** Clave en texto (ABCD-EFGH-…, base32) -> bytes. null si no es válida. */
  function textToSecret(text) {
    var clean = String(text).toUpperCase().replace(/[-\s]/g, '');
    if (!clean.length) return null;
    var out = [], buffer = 0, bits = 0;
    for (var i = 0; i < clean.length; i++) {
      var v = ALPHABET.indexOf(clean[i]);
      if (v < 0) return null;
      buffer = ((buffer << 5) | v) & 0xFFFFFF; bits += 5;
      if (bits >= 8) { out.push((buffer >> (bits - 8)) & 0xFF); bits -= 8; }
    }
    return new Uint8Array(out);
  }

  // ---- SHA-256 / HMAC-SHA256 (puro JS: funciona también abriendo el archivo sin servidor) ----
  var K = new Uint32Array([
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2]);

  function sha256(bytes) {
    var h = new Uint32Array([0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]);
    var len = bytes.length, total = (((len + 9 + 63) >> 6) << 6);
    var m = new Uint8Array(total);
    m.set(bytes); m[len] = 0x80;
    var bitLen = len * 8;
    var dv = new DataView(m.buffer);
    dv.setUint32(total - 8, Math.floor(bitLen / 0x100000000));
    dv.setUint32(total - 4, bitLen >>> 0);
    var w = new Uint32Array(64);
    for (var off = 0; off < total; off += 64) {
      for (var t = 0; t < 16; t++) w[t] = dv.getUint32(off + t * 4);
      for (t = 16; t < 64; t++) {
        var x = w[t - 15], y = w[t - 2];
        var s0 = ((x >>> 7) | (x << 25)) ^ ((x >>> 18) | (x << 14)) ^ (x >>> 3);
        var s1 = ((y >>> 17) | (y << 15)) ^ ((y >>> 19) | (y << 13)) ^ (y >>> 10);
        w[t] = (w[t - 16] + s0 + w[t - 7] + s1) | 0;
      }
      var a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7];
      for (t = 0; t < 64; t++) {
        var S1 = ((e >>> 6) | (e << 26)) ^ ((e >>> 11) | (e << 21)) ^ ((e >>> 25) | (e << 7));
        var ch = (e & f) ^ (~e & g);
        var t1 = (hh + S1 + ch + K[t] + w[t]) | 0;
        var S0 = ((a >>> 2) | (a << 30)) ^ ((a >>> 13) | (a << 19)) ^ ((a >>> 22) | (a << 10));
        var maj = (a & b) ^ (a & c) ^ (b & c);
        var t2 = (S0 + maj) | 0;
        hh = g; g = f; f = e; e = (d + t1) | 0; d = c; c = b; b = a; a = (t1 + t2) | 0;
      }
      h[0] = (h[0] + a) | 0; h[1] = (h[1] + b) | 0; h[2] = (h[2] + c) | 0; h[3] = (h[3] + d) | 0;
      h[4] = (h[4] + e) | 0; h[5] = (h[5] + f) | 0; h[6] = (h[6] + g) | 0; h[7] = (h[7] + hh) | 0;
    }
    var out = new Uint8Array(32), odv = new DataView(out.buffer);
    for (var i = 0; i < 8; i++) odv.setUint32(i * 4, h[i]);
    return out;
  }

  function utf8(s) {
    var out = [];
    for (var i = 0; i < s.length; i++) {
      var c = s.charCodeAt(i);
      if (c < 0x80) out.push(c);
      else if (c < 0x800) out.push(0xc0 | (c >> 6), 0x80 | (c & 63));
      else out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
    }
    return new Uint8Array(out);
  }

  function hmacSha256(key, msg) {
    if (key.length > 64) key = sha256(key);
    var ipad = new Uint8Array(64 + msg.length), opad = new Uint8Array(96);
    for (var i = 0; i < 64; i++) { var k = i < key.length ? key[i] : 0; ipad[i] = k ^ 0x36; opad[i] = k ^ 0x5c; }
    ipad.set(msg, 64);
    opad.set(sha256(ipad), 64);
    return sha256(opad);
  }

  function hex(bytes) {
    var s = '';
    for (var i = 0; i < bytes.length; i++) s += (bytes[i] < 16 ? '0' : '') + bytes[i].toString(16);
    return s;
  }

  /** mac de 12 hex de un minuto (igual que DanceQr.mac en Kotlin). */
  function mac(secret, minute) {
    return hex(hmacSha256(secret, utf8('kronos-dance|' + minute))).slice(0, 12);
  }

  /** Texto del QR para el instante nowMs. Vale unos 3 minutos y solo si la alarma empezó a sonar antes. */
  function payload(secret, nowMs) {
    var minute = Math.floor(nowMs / 60000);
    return 'KRONOSD1|' + minute + '|' + mac(secret, minute);
  }

  // ---- Detección de baile por movimiento de la cámara ----
  /**
   * Compara dos fotogramas en grises (w x h). Cuenta cuánto de la imagen se mueve y en cuántas zonas de una
   * cuadrícula 4x3: un baile mueve el cuerpo en varias zonas; agitar solo una mano no basta.
   */
  function analyze(prev, cur, w, h, opts) {
    opts = opts || {};
    var th = opts.pixelThreshold || 28, minRatio = opts.minRatio || 0.02, minCells = opts.minCells || 4;
    var gx = 4, gy = 3, cells = new Array(gx * gy).fill(0), moving = 0;
    for (var y = 0; y < h; y++) {
      var cy = Math.min(gy - 1, Math.floor(y * gy / h));
      for (var x = 0; x < w; x++) {
        var i = y * w + x, d = cur[i] - prev[i];
        if (d < 0) d = -d;
        if (d > th) { moving++; cells[cy * gx + Math.min(gx - 1, Math.floor(x * gx / w))]++; }
      }
    }
    var cellPixels = (w * h) / (gx * gy), cellsActive = 0;
    for (var c = 0; c < cells.length; c++) if (cells[c] / cellPixels > 0.02) cellsActive++;
    var ratio = moving / (w * h);
    return { ratio: ratio, cells: cellsActive, active: ratio >= minRatio && cellsActive >= minCells };
  }

  /** Acumula segundos de baile activo hasta la meta (los descansos no suman, pero no reinician). */
  function DanceMeter(goalSec) { this.goal = goalSec || 30; this.sec = 0; }
  DanceMeter.prototype.update = function (active, dtMs) {
    if (active) this.sec = Math.min(this.goal, this.sec + dtMs / 1000);
    return this.sec >= this.goal;
  };
  DanceMeter.prototype.progress = function () { return this.sec / this.goal; };

  var api = { textToSecret: textToSecret, sha256: sha256, hmacSha256: hmacSha256, hex: hex, utf8: utf8, mac: mac,
              payload: payload, analyze: analyze, DanceMeter: DanceMeter };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else root.DanceCore = api;
})(typeof self !== 'undefined' ? self : this);
