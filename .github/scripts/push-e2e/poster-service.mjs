#!/usr/bin/env node
/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

// Push e2e poster service (CI + local).
//
// Adapted from the openam#28854 POC (tools/authenticator-e2e/docker/poster-service.mjs)
// for this repo's CI: renders a push-registration URI as a QR PNG, writes it to the
// emulator's virtual-scene resources directory, and hot-swaps the "table" poster via
// `adb emu virtualscene-image`, so the app under test can scan it with its camera.
//
// Endpoints:
//   GET  /health  -> 200 "ok"
//   GET  /ready   -> {"status":"ready"} when the app has an FCM token in logcat
//   POST /poster  -> body {"uri": "pushauth://..."}; renders + swaps the poster
//
// Configuration (env):
//   POSTER_PORT            listen port                     (default 4724)
//   EMULATOR_RESOURCES_DIR SDK emulator resources dir      (default /opt/android-sdk/emulator/resources)
//   POSTER_READY_TAG       logcat tag whose output must contain an FCM token (default FirebaseInstanceId)
//   POSTER_LOG_STANZA      logcat marker proving a token exists (default: "onToken:|token_len=")
//   ANDROID_SERIAL         pinned emulator serial (adb -s), optional

import { execFile as execFileCallback } from 'node:child_process';
import { createServer } from 'node:http';
import { stat, writeFile } from 'node:fs/promises';
import { promisify } from 'node:util';
import { randomUUID } from 'node:crypto';

import QRCode from 'qrcode';
import { PNG } from 'pngjs';

const execFile = promisify(execFileCallback);

const HOST = '0.0.0.0';
const PORT = Number(process.env.POSTER_PORT || 4724);
const RESOURCES_DIR =
  process.env.EMULATOR_RESOURCES_DIR || '/opt/android-sdk/emulator/resources';
// The emulator console only addresses posters named `wall` or `table`; the AVD's
// Toren1BD.posters geometry must place `table` in front of the camera.
const POSTER_NAME = 'table';
const MAX_BODY_BYTES = 32_768;
const REGISTRATION_URI_PREFIX = 'pushauth://';
const CONTROL_OR_WHITESPACE = /[\s\u0000-\u001f\u007f-\u009f]/u;
// adb unavailable => 503 so callers can distinguish infra failure from test failure.
const ADB_UNAVAILABLE =
  /(?:cannot connect|device .*?(?:offline|not found|unauthorized)|failed to connect|no devices|more than one device|adb server)/i;

const ADB_ARGS = process.env.ANDROID_SERIAL ? ['-s', process.env.ANDROID_SERIAL] : [];

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
  }
}

class AdbUnavailableError extends Error {
  constructor(message) {
    super(message);
    this.name = 'AdbUnavailableError';
    this.status = 503;
  }
}

class VirtualSceneError extends Error {
  constructor(message) {
    super(message);
    this.name = 'VirtualSceneError';
    this.status = 500;
  }
}

function jsonResponse(response, status, body) {
  const payload = JSON.stringify(body);
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(payload),
  });
  response.end(payload);
}

function commandOutput(error) {
  return [error?.stdout, error?.stderr, error?.message]
    .filter((value) => typeof value === 'string' && value.length > 0)
    .join('\n')
    .trim();
}

function adb(args, opts = {}) {
  return execFile('adb', [...ADB_ARGS, ...args], {
    encoding: 'utf8',
    maxBuffer: 4_194_304,
    ...opts,
  });
}

// QR sizing mirrors the POC: version 25, EC level L, 16 px/module — large enough
// to survive the virtual scene's texture resampling.
function renderRegistrationQr(uri) {
  const qrVersion = 25;
  const errorCorrection = 'L';
  const quietZoneModules = 4;
  const moduleScale = 16;
  const qrModules = (qrVersion - 1) * 4 + 21;
  const imageSize = (qrModules + quietZoneModules * 2) * moduleScale;
  const qr = QRCode.create(uri, { version: qrVersion, errorCorrectionLevel: errorCorrection });
  const png = new PNG({ width: imageSize, height: imageSize });

  png.data.fill(255);
  for (let moduleY = 0; moduleY < qrModules; moduleY += 1) {
    for (let moduleX = 0; moduleX < qrModules; moduleX += 1) {
      if (!qr.modules.get(moduleX, moduleY)) {
        continue;
      }
      const startX = (moduleX + quietZoneModules) * moduleScale;
      const startY = (moduleY + quietZoneModules) * moduleScale;
      for (let y = startY; y < startY + moduleScale; y += 1) {
        for (let x = startX; x < startX + moduleScale; x += 1) {
          const pixelOffset = (imageSize * y + x) << 2;
          png.data[pixelOffset] = 0;
          png.data[pixelOffset + 1] = 0;
          png.data[pixelOffset + 2] = 0;
        }
      }
    }
  }

  return PNG.sync.write(png);
}

function readRequestBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    let settled = false;

    const finishWithError = (error) => {
      if (!settled) {
        settled = true;
        reject(error);
      }
    };

    request.on('data', (chunk) => {
      if (settled) {
        return;
      }
      const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
      size += buffer.length;
      if (size > MAX_BODY_BYTES) {
        request.resume();
        finishWithError(new HttpError(400, 'Request body exceeds 32768 bytes'));
        return;
      }
      chunks.push(buffer);
    });
    request.on('end', () => {
      if (!settled) {
        settled = true;
        resolve(Buffer.concat(chunks).toString('utf8'));
      }
    });
    request.on('error', finishWithError);
  });
}

function parseRegistrationUri(body) {
  let payload;
  try {
    payload = JSON.parse(body);
  } catch {
    throw new HttpError(400, 'Request body must be valid JSON');
  }

  const uri = payload?.uri;
  if (
    typeof uri !== 'string' ||
    !uri.startsWith(REGISTRATION_URI_PREFIX) ||
    CONTROL_OR_WHITESPACE.test(uri)
  ) {
    throw new HttpError(400, 'uri must be a push registration URI without whitespace or control characters');
  }
  return uri;
}

async function verifyWrittenFile(remotePath) {
  try {
    await stat(remotePath);
  } catch (error) {
    throw new VirtualSceneError(`Rendered poster file could not be stat-ed: ${commandOutput(error) || remotePath}`);
  }
}

async function swapPoster(remotePath) {
  let result;
  try {
    result = await adb(['emu', 'virtualscene-image', POSTER_NAME, remotePath]);
  } catch (error) {
    const output = commandOutput(error) || 'no console output';
    if (error?.code === 'ENOENT' || ADB_UNAVAILABLE.test(output)) {
      throw new AdbUnavailableError(`adb virtualscene-image failed: ${output}`);
    }
    throw new VirtualSceneError(output);
  }

  const output = `${result.stdout ?? ''}${result.stderr ?? ''}`.trim();
  if (!output.includes('OK')) {
    throw new VirtualSceneError(output || 'virtualscene-image returned no console output');
  }
  // The emulator can return OK even when its later loadPNG operation fails.
}

async function readLogcat() {
  try {
    const result = await adb(['logcat', '-d', '-t', '800']);
    return result.stdout;
  } catch (error) {
    const output = commandOutput(error) || 'no logcat output';
    throw new AdbUnavailableError(`adb/logcat unavailable: ${output}`);
  }
}

// FCM token liveness: the app logs its token via the Firebase SDK tag on first
// check-in, and our own app logs "FCM token" lines when it fetches/sets a token.
// Either marker proves the app can receive pushes.
function hasFcmToken(logcat) {
  return /FirebaseInstanceId/.test(logcat) || /FCM token/i.test(logcat);
}

function sleep(durationMs) {
  return new Promise((resolve) => setTimeout(resolve, durationMs));
}

async function handleReady(response, readyState) {
  let logcat;
  try {
    logcat = await readLogcat();
  } catch (error) {
    jsonResponse(response, error?.status ?? 500, { status: 'not-ready', reason: error?.message });
    return;
  }

  if (hasFcmToken(logcat)) {
    readyState.ready = true;
    jsonResponse(response, 200, { status: 'ready' });
    return;
  }

  // Nudge the app once per /ready call: relaunching it re-triggers the FCM token
  // fetch (same recovery as the POC's poster service).
  try {
    await adb(['shell', 'am', 'force-stop', process.env.SAMPLE_APP_PKG || 'com.pingidentity.samples.pingsampleapp']);
    await sleep(1_500);
    await adb([
      'shell',
      'monkey',
      '-p',
      process.env.SAMPLE_APP_PKG || 'com.pingidentity.samples.pingsampleapp',
      '-c',
      'android.intent.category.LAUNCHER',
      '1',
    ]);
  } catch (error) {
    const output = commandOutput(error) || 'no output';
    if (error?.code === 'ENOENT' || ADB_UNAVAILABLE.test(output)) {
      jsonResponse(response, 503, { status: 'not-ready', reason: `adb/app launch unavailable: ${output}` });
      return;
    }
  }

  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline) {
    await sleep(3_000);
    const fresh = await readLogcat().catch(() => '');
    if (hasFcmToken(fresh)) {
      readyState.ready = true;
      jsonResponse(response, 200, { status: 'ready' });
      return;
    }
  }

  jsonResponse(response, 200, { status: 'not-ready', reason: 'fcm-token-unavailable' });
}

async function handlePoster(request, response, readyState) {
  const uri = parseRegistrationUri(await readRequestBody(request));
  // The emulator caches scene textures by path: every swap needs a fresh file name.
  const remotePath = `${RESOURCES_DIR}/qr-${Date.now()}-${randomUUID().slice(0, 8)}.png`;

  await writeFile(remotePath, renderRegistrationQr(uri));
  await verifyWrittenFile(remotePath);
  await swapPoster(remotePath);
  readyState.lastPosterSwapAt = Date.now();

  jsonResponse(response, 200, { status: 'ok', remotePath });
}

async function handleRequest(request, response, readyState) {
  const path = (request.url ?? '/').split('?', 1)[0];
  if (request.method === 'GET' && path === '/health') {
    response.writeHead(200, { 'content-type': 'text/plain; charset=utf-8' });
    response.end('ok');
    return;
  }
  if (request.method === 'GET' && path === '/ready') {
    await handleReady(response, readyState);
    return;
  }
  if (request.method === 'POST' && path === '/poster') {
    await handlePoster(request, response, readyState);
    return;
  }

  jsonResponse(response, 404, { error: 'Not found' });
}

const readyState = { ready: false, lastPosterSwapAt: 0 };

const server = createServer((request, response) => {
  handleRequest(request, response, readyState).catch((error) => {
    const status = error?.status ?? 500;
    const message = error instanceof Error ? error.message : String(error);
    if (!response.headersSent) {
      jsonResponse(response, status, { error: message });
    } else {
      response.destroy();
    }
    if (status >= 500) {
      console.error(message);
    }
  });
});

server.on('error', (error) => {
  console.error(error);
  process.exitCode = 1;
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.once(signal, () => {
    server.close(() => process.exit(0));
  });
}

server.listen(PORT, HOST, () => {
  console.log(`Poster service listening on http://${HOST}:${PORT} (resources: ${RESOURCES_DIR})`);
});
