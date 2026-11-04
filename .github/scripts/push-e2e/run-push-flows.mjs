#!/usr/bin/env node
/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

// Push e2e flow runner (CI + local): drives the sample app through push
// registration and push authentication against a live AIC tenant, using plain
// adb + uiautomator instead of Appium.
//
// Flow (mirrors the openam#28854 POC, adapted to the sample app's Compose UI):
//
//   1. POST to push-reg-v1 with zero-page login headers -> registration URI
//      from the HiddenValueCallback (IDToken3 / mfaDeviceRegistration)
//   2. POST the URI to the poster service -> QR appears in the virtual scene
//   3. Drive the app: HOME -> "MFA Accounts" -> FAB "Add Account" ->
//      "Scan QR Code" -> camera scans the virtual scene -> app POSTs the
//      registration response to AM -> AM confirms -> tokenId
//   4. POST to push-auth-v1 -> AM sends an FCM push -> approve it in-app
//      (notification drawer Approve action or the in-app response screen)
//   5. Resume the AM transaction -> tokenId
//
// Configuration (env):
//   PUSH_E2E_HOST       tenant host, e.g. openam-sdks.forgeblocks.com   (required)
//   PUSH_E2E_REALM      realm path                                      (default alpha)
//   PUSH_E2E_TREE_REG   registration journey                            (default push-reg-v1)
//   PUSH_E2E_TREE_AUTH  authentication journey                          (default push-auth-v1)
//   PUSH_E2E_USER       zero-page username                              (required)
//   PUSH_E2E_PASSWORD   zero-page password (may be literal "ignored" if the
//                       tree's ZeroPageLoginNode does not validate)     (default ignored)
//   POSTER_SERVICE_URL  poster service base                             (default http://127.0.0.1:4724)
//   ANDROID_SERIAL      pinned emulator serial                          (optional)
//   SAMPLE_APP_PKG      sample app package                              (default com.pingidentity.samples.pingsampleapp)
//   PUSH_E2E_TIMEOUT_MS overall deadline per flow                       (default 120000)

import { execFile as execFileCallback } from 'node:child_process';
import { appendFileSync, mkdirSync, readFileSync } from 'node:fs';
import { promisify } from 'node:util';

const execFile = promisify(execFileCallback);

// --------------------------------------------------------------------------
// Structured run log: every step + every AM HTTP call is appended to
// /tmp/push-e2e-run.log (uploaded as part of the diagnostics artifact) and
// echoed to stdout. The summary report is printed at the end of every run —
// pass or fail.
// --------------------------------------------------------------------------

const RUN_LOG = process.env.PUSH_E2E_RUN_LOG || '/tmp/push-e2e-run.log';
mkdirSync('/tmp', { recursive: true });
appendFileSync(RUN_LOG, `\n==== push e2e run started ${new Date().toISOString()} ====\n`);

function logRun(line) {
  const stamped = `[${new Date().toISOString()}] ${line}`;
  console.log(stamped);
  try {
    appendFileSync(RUN_LOG, `${stamped}\n`);
  } catch {
    // log file is best-effort; never fail the run over it
  }
}

// Wrap fetch so every HTTP call (method, URL, status, duration, and a safe
// summary of the JSON body) lands in the run log. Request headers are NEVER
// logged (they carry the password and the admin token); response bodies are
// truncated and authId/tokenId-style long values are shortened.
let httpSeq = 0;
const origFetch = globalThis.fetch;
globalThis.fetch = async function loggedFetch(url, init = {}) {
  const id = ++httpSeq;
  const method = init.method ?? 'GET';
  const started = Date.now();
  logRun(`http #${id} --> ${method} ${url}`);
  const response = await origFetch(url, init);
  const ms = Date.now() - started;
  let bodyNote = '';
  try {
    const cloned = response.clone();
    const text = await cloned.text();
    bodyNote = summarizeBody(text);
  } catch {
    bodyNote = '<unreadable body>';
  }
  logRun(`http #${id} <-- ${response.status} in ${ms}ms ${bodyNote}`);
  return response;
};

function summarizeBody(text) {
  if (!text) return '(empty body)';
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    return `text[${text.length}] ${text.slice(0, 120).replace(/\s+/g, ' ')}`;
  }
  const parts = [];
  if (json.tokenId) parts.push(`tokenId=${String(json.tokenId).slice(0, 12)}...`);
  if (json.authId) parts.push(`authId=${String(json.authId).slice(0, 12)}...`);
  if (Array.isArray(json.callbacks)) {
    parts.push(`callbacks=[${json.callbacks.map((cb) => cb.type).join(', ')}]`);
  }
  if (json.result) parts.push(`result=${json.result.length} item(s)`);
  if (json.code || json.message) parts.push(`error=${json.code ?? ''} ${json.message ?? ''}`.trim().slice(0, 120));
  if (json.status) parts.push(`status=${json.status}`);
  if (!parts.length) parts.push(`keys=[${Object.keys(json).slice(0, 8).join(',')}]`);
  return `{${parts.join(' ')}}`;
}

const HOST = required('PUSH_E2E_HOST');
const REALM = process.env.PUSH_E2E_REALM || 'alpha';
const TREE_REG = process.env.PUSH_E2E_TREE_REG || 'push-reg-v1';
const TREE_AUTH = process.env.PUSH_E2E_TREE_AUTH || 'push-auth-v1';
const USER = required('PUSH_E2E_USER');
const PASSWORD = process.env.PUSH_E2E_PASSWORD || 'ignored';
const POSTER = process.env.POSTER_SERVICE_URL || 'http://127.0.0.1:4724';
const PKG = process.env.SAMPLE_APP_PKG || 'com.pingidentity.samples.pingsampleapp';
const OVERALL_MS = Number(process.env.PUSH_E2E_TIMEOUT_MS || 120_000);

const ADB_ARGS = process.env.ANDROID_SERIAL ? ['-s', process.env.ANDROID_SERIAL] : [];

function required(name) {
  const value = process.env[name];
  if (!value) {
    console.error(`Missing required env ${name}`);
    process.exit(2);
  }
  return value;
}

async function adb(args, opts = {}) {
  return execFile('adb', [...ADB_ARGS, ...args], {
    encoding: 'utf8',
    maxBuffer: 8_388_608,
    ...opts,
  });
}

async function shell(cmd) {
  const result = await adb(['shell', cmd]);
  return (result.stdout ?? '').replace(/\r/g, '');
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// Dump to /data/local/tmp (shell-user-writable on every API level; /sdcard writes can
// fail depending on FUSE/scoped-storage state — run 5 died exactly there on API 34).
// The dump can transiently fail (accessibility service not settled, screen state);
// its failure mode is "exit 0 + error on stdout + no file", so verify the file exists
// and retry a few times before giving up.
const UI_DUMP_PATH = '/data/local/tmp/push-e2e-window.xml';

async function uiDumpOnce() {
  // Remove a stale dump first so a failed dump can't be mistaken for success.
  await shell(`rm -f ${UI_DUMP_PATH}`);
  const dumpOut = await shell(`uiautomator dump ${UI_DUMP_PATH}`);
  const check = await shell(`[ -s ${UI_DUMP_PATH} ] && echo ok`);
  if (!check.includes('ok')) {
    throw new Error(`uiautomator dump produced no output: ${dumpOut.trim().slice(0, 120)}`);
  }
  return shell(`cat ${UI_DUMP_PATH}`);
}

async function uiDump() {
  let lastError;
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    try {
      return await uiDumpOnce();
    } catch (error) {
      lastError = error;
      await sleep(1_000 * attempt);
    }
  }
  throw lastError;
}

// Center of the first node whose content-desc or text matches `needle`.
// Note: uiautomator XML lists BOTH text and content-desc attributes on every
// node; match per-node on either attribute rather than a single regex over
// attribute pairs (text="" would otherwise shadow a later content-desc hit).
function findNodeBounds(source, needle) {
  const nodeRe = /<node\b[^>]*>/g;
  let node;
  while ((node = nodeRe.exec(source)) !== null) {
    const markup = node[0];
    if (!markup.includes(needle)) {
      continue;
    }
    const desc = markup.match(/content-desc="([^"]*)"/);
    const text = markup.match(/\btext="([^"]*)"/);
    const label = (desc?.[1] ?? '') || (text?.[1] ?? '');
    if (!label.includes(needle)) {
      continue;
    }
    const bounds = markup.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
    if (!bounds) {
      continue;
    }
    const x1 = Number(bounds[1]);
    const y1 = Number(bounds[2]);
    const x2 = Number(bounds[3]);
    const y2 = Number(bounds[4]);
    return { x: Math.round((x1 + x2) / 2), y: Math.round((y1 + y2) / 2) };
  }
  return null;
}

async function tapLabel(needle, { timeoutMs = 10_000, pollMs = 1_000, required = true } = {}) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const source = await uiDump();
    const point = findNodeBounds(source, needle);
    if (point) {
      await adb(['shell', 'input', 'tap', String(point.x), String(point.y)]);
      return point;
    }
    await sleep(pollMs);
  }
  if (required) {
    throw new Error(`UI anchor not found within ${timeoutMs}ms: "${needle}"`);
  }
  return null;
}

async function waitForLabel(needle, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const source = await uiDump();
    if (source.includes(needle)) {
      return source;
    }
    await sleep(1_000);
  }
  throw new Error(`UI state not reached within ${timeoutMs}ms: "${needle}"`);
}

// --------------------------------------------------------------------------
// AM client (zero-page login + callback-echo resume, as in the POC scripts)
// --------------------------------------------------------------------------

function amHeaders(refererTree) {
  return {
    Accept: 'application/json',
    'Accept-API-Version': 'protocol=1.0,resource=2.1',
    'Content-Type': 'application/json',
    Origin: `https://${HOST}`,
    Referer: `https://${HOST}/am/XUI/?realm=/${REALM}&authIndexType=service&authIndexValue=${refererTree}`,
    'X-OpenAM-Username': USER,
    'X-OpenAM-Password': PASSWORD,
    'x-requested-with': 'forgerock-sdk',
  };
}

async function amAuthenticate(tree, { zeroPage = true, cookies = new Map(), body = '' } = {}) {
  const url = `https://${HOST}/am/json/realms/root/realms/${REALM}/authenticate?authIndexType=service&authIndexValue=${tree}`;
  const headers = { ...amHeaders(tree) };
  if (!zeroPage) {
    delete headers['X-OpenAM-Username'];
    delete headers['X-OpenAM-Password'];
  }
  if (cookies.size > 0) {
    headers.Cookie = [...cookies.entries()].map(([k, v]) => `${k}=${v}`).join('; ');
  }

  const response = await fetch(url, { method: 'POST', headers, body, signal: AbortSignal.timeout(30_000) });
  for (const raw of response.headers.getSetCookie?.() ?? []) {
    const [pair] = raw.split(';');
    const idx = pair.indexOf('=');
    if (idx > 0) cookies.set(pair.slice(0, idx).trim(), pair.slice(idx + 1).trim());
  }
  const json = await response.json();
  if (!response.ok) {
    throw new Error(`AM ${tree} failed: HTTP ${response.status} ${JSON.stringify(json).slice(0, 400)} | zeroPage=${zeroPage} cookies=[${[...cookies.keys()].join(',')}] bodyLen=${body.length}`);
  }
  return json;
}

// Never replay zero-page headers on continuations (breaks AM zero-page handling);
// strip TextOutputCallbacks (display-only) from the echo.
function continuationBody(amResponse) {
  return JSON.stringify({
    authId: amResponse.authId,
    callbacks: (amResponse.callbacks ?? []).filter((cb) => cb.type !== 'TextOutputCallback'),
  });
}

async function amResumeUntilToken(tree, amResponse, cookies, { timeoutMs = OVERALL_MS, intervalMs = 2_000 } = {}) {
  const deadline = Date.now() + timeoutMs;
  let payload = continuationBody(amResponse);
  while (Date.now() < deadline) {
    const json = await amAuthenticate(tree, { zeroPage: false, cookies, body: payload });
    if (json.tokenId) {
      return json.tokenId;
    }
    if (!json.callbacks) {
      throw new Error(`AM ${tree}: unexpected continuation response ${JSON.stringify(json).slice(0, 300)}`);
    }
    payload = continuationBody(json);
    await sleep(intervalMs);
  }
  throw new Error(`AM ${tree}: timed out waiting for tokenId`);
}

function extractRegistrationUri(amResponse) {
  const uris = (amResponse.callbacks ?? [])
    .filter((cb) => cb.type === 'HiddenValueCallback')
    .flatMap((cb) => cb.output ?? [])
    .filter((o) => o.name === 'value' && typeof o.value === 'string' && o.value.startsWith('pushauth://'))
    .map((o) => o.value);
  if (uris.length !== 1) {
    throw new Error(`Expected exactly one pushauth:// registration URI, got ${uris.length}`);
  }
  return uris[0];
}

async function posterSwap(uri) {
  const response = await fetch(`${POSTER}/poster`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ uri }),
    signal: AbortSignal.timeout(30_000),
  });
  const body = await response.json();
  if (!response.ok) {
    throw new Error(`Poster swap failed: HTTP ${response.status} ${JSON.stringify(body)}`);
  }
  return body;
}

// --------------------------------------------------------------------------
// App navigation helpers
// --------------------------------------------------------------------------

// Wipe the app's data (accounts, FCM token, settings) so every run starts from
// the same first-launch state. Required between runs: a registered account
// makes the next scan fail with DuplicateCredentialException ("account already
// exists"). Re-grants runtime permissions (pm clear revokes them) and relaunches.
async function resetAppData() {
  console.log('[push-e2e] resetting app data (pm clear + permission re-grant)');
  await adb(['shell', 'pm', 'clear', PKG]);
  await adb(['shell', 'pm', 'grant', PKG, 'android.permission.CAMERA']);
  await adb(['shell', 'pm', 'grant', PKG, 'android.permission.POST_NOTIFICATIONS']);
}

// Delete every push device registered for the test user on the AM side, so the
// registration tree takes the full QR path. Uses the admin token from
// PUSH_E2E_ADMIN_TOKEN (frodo-style bearer); skipped when absent.
async function deleteServerDevices() {
  const token = process.env.PUSH_E2E_ADMIN_TOKEN;
  if (!token) {
    console.log('[push-e2e] PUSH_E2E_ADMIN_TOKEN not set; skipping server-side device cleanup');
    return;
  }
  const baseUrl = `https://${HOST}/am/json/realms/root/realms/${REALM}/users/${USER}/devices/2fa/push`;
  const response = await fetch(`${baseUrl}?_queryFilter=true`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Accept-API-Version': 'protocol=1.0,resource=1.0',
    },
    signal: AbortSignal.timeout(30_000),
  });
  const json = await response.json();
  for (const device of json.result ?? []) {
    const id = device.uuid ?? device._id;
    // 200 with the deleted payload = success on this endpoint; 404 = already gone.
    const del = await fetch(`${baseUrl}/${id}`, {
      method: 'DELETE',
      headers: {
        Authorization: `Bearer ${token}`,
        'Accept-API-Version': 'protocol=1.0,resource=1.0',
      },
      signal: AbortSignal.timeout(30_000),
    });
    if (del.ok || del.status === 404) {
      console.log(`[push-e2e] server device ${id} deleted: HTTP ${del.status}`);
    } else {
      throw new Error(`Failed to delete server device ${id}: HTTP ${del.status}`);
    }
  }
}

async function launchApp() {
  // Bring the sample app to the foreground and VERIFY it owns the screen.
  // Any other app in front (a previous test app, the launcher) would make the
  // UI-dump anchors match against the wrong UI or hang.
  for (let attempt = 0; attempt < 3; attempt += 1) {
    await adb(['shell', 'am', 'force-stop', PKG]);
    await sleep(1_000);
    await adb([
      'shell',
      'monkey',
      '-p',
      PKG,
      '-c',
      'android.intent.category.LAUNCHER',
      '1',
    ]);
    await sleep(4_000);
    if ((await foregroundPackage()) === PKG) {
      return;
    }
  }
  throw new Error(`Sample app ${PKG} did not reach the foreground`);
}

async function foregroundPackage() {
  // API-level dependent: newer builds print "topResumedActivity=", older ones
  // "ResumedActivity:". Both name the package as "<pkg>/<activity>" inside the
  // ActivityRecord; match either separator.
  const out = await shell('dumpsys activity activities | grep -E "ResumedActivity"');
  const match = out.match(/ResumedActivity[:=]\s*ActivityRecord\{[^}]*\s([^\s/]+)\//);
  return match ? match[1] : null;
}

async function navigateToAccounts() {
  // HOME -> MFA section -> "MFA Accounts" row (subtitle "Manage TOPT, HOTP and
  // push accounts"). The subtitle is the unique anchor: a PingOne MFA section
  // item also displays "MFA Accounts" as its title, so match the subtitle text.
  // The row sits below the fold; scroll down until it enters the accessibility
  // tree, then tap it. If the app is already on the Accounts screen (leftover
  // state), skip navigation.
  {
    const source = await uiDump();
    if (source.includes('Add Account') || source.includes('No accounts')) {
      return; // already there
    }
  }
  const deadline = Date.now() + 45_000;
  let tracedAt = 0;
  while (Date.now() < deadline) {
    const source = await uiDump();
    if (source.includes('Add Account') || source.includes('No accounts')) {
      return; // Accounts screen reached (post-tap)
    }
    // Every ~15s, log the visible text labels so a CI failure shows what the
    // app was actually displaying (welcome screen? dialog? empty home?).
    if (Date.now() - tracedAt > 15_000) {
      tracedAt = Date.now();
      const texts = [...source.matchAll(/text="([^"]{1,60})"/g)]
        .map((m) => m[1])
        .filter((t) => t.trim() && !/^\d+$/.test(t))
        .slice(0, 15);
      const descs = [...source.matchAll(/content-desc="([^"]{1,60})"/g)]
        .map((m) => m[1])
        .filter((t) => t.trim())
        .slice(0, 10);
      logRun(`navigateToAccounts waiting; visible text=[${texts.join(' | ')}] desc=[${descs.join(' | ')}]`);
    }
    const point = findNodeBounds(source, 'Manage TOPT');
    if (point) {
      await adb(['shell', 'input', 'tap', String(point.x), String(point.y)]);
      await sleep(2_000);
      continue;
    }
    await adb(['shell', 'input', 'swipe', ...await swipeUpCoords(), '600']);
    await sleep(1_200);
  }
  throw new Error('UI anchor not found within 45000ms: "Manage TOPT" row');
}

// Screen-relative upward swipe: from 78% height to 35% height, horizontally centered.
// Hardcoded pixel coordinates break on the CI's default 320x640 AVD skin (run 8: the
// swipe went off-screen, the MFA section never entered the accessibility tree).
async function swipeUpCoords() {
  if (!swipeUpCoords.cache) {
    const size = await shell('wm size');
    const match = size.match(/(\d+)x(\d+)/);
    const width = match ? Number(match[1]) : 1080;
    const height = match ? Number(match[2]) : 2340;
    const x = Math.round(width / 2);
    swipeUpCoords.cache = [String(x), String(Math.round(height * 0.78)), String(x), String(Math.round(height * 0.35))];
  }
  return swipeUpCoords.cache;
}

async function scanQrViaApp() {
  // Accounts screen: FAB "Add Account" opens the menu with "Scan QR Code".
  await tapLabel('Add Account', { timeoutMs: 15_000 });
  await tapLabel('Scan QR Code', { timeoutMs: 10_000 });
  // Verify the scanner screen actually opened and which state it is in:
  //   "Position QR code within frame" = camera preview live
  //   "Camera permission is required" = permission was not granted
  //   neither + back on Accounts = the scanner exited immediately (crash?)
  const scannerState = await waitForScannerState();
  logRun(`scanner state: ${scannerState}`);
}

// Determines which state the scanner screen reached. Returns a human-readable
// tag; never throws (the caller logs whatever it finds).
async function waitForScannerState({ timeoutMs = 12_000 } = {}) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const source = await uiDump();
    if (source.includes('Position QR code within frame')) {
      return 'camera-preview-live';
    }
    if (source.includes('Camera permission is required')) {
      return 'PERMISSION-REQUIRED (camera permission not granted)';
    }
    if (source.includes('No accounts added yet')) {
      return 'scanner-exited-immediately (back on accounts screen)';
    }
    await sleep(1_000);
  }
  return 'scanner-state-unknown (no marker found within timeout)';
}

async function registeredAccountVisible(timeoutMs = 45_000) {
  // Success signals, in order of decisiveness:
  //   1. "Account added successfully" snackbar (shown right after registration)
  //   2. an account row on the Accounts screen — trusted because the run
  //      starts from a pm-cleared app (empty state)
  // Failure signal: the SDK's DuplicateCredentialException text — means stale
  // app state survived and the reset did not happen.
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const source = await uiDump();
    if (source.includes('Account added successfully')) {
      return true;
    }
    if (source.includes('already exists')) {
      throw new Error(
        'Registration rejected: credential already exists on the device (stale app state — resetAppData did not run?)',
      );
    }
    // Scanner closed and an account row is showing (pm-clear guarantees it's new)
    if (source.includes('ForgeRock') && !source.includes('Scan QR Code')) {
      return true;
    }
    await sleep(2_000);
  }
  return false;
}

async function dismissScannerIfOpen() {
  // After a successful scan the app navigates back to Accounts automatically.
  // If a stale frame produced a decode error dialog, try to dismiss it.
  const source = await uiDump();
  if (source.includes('OK') && source.includes('dialog')) {
    const point = findNodeBounds(source, 'OK');
    if (point) {
      await adb(['shell', 'input', 'tap', String(point.x), String(point.y)]);
    }
  }
}

// --------------------------------------------------------------------------
// Push authentication approval
// --------------------------------------------------------------------------

// The in-app push list opens the response screen with an "Approve" button.
// Alternative: approve via the system notification's action button.
async function approvePush(timeoutMs = 90_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const source = await uiDump();
    // 1) In-app response screen "Approve" button (PushNotificationActivity or
    //    NotificationResponseScreen).
    let point = findNodeBounds(source, 'Approve');
    if (point) {
      await adb(['shell', 'input', 'tap', String(point.x), String(point.y)]);
      return 'in-app';
    }
    // 2) System notification action button.
    point = await notificationApproveAction();
    if (point) {
      await adb(['shell', 'input', 'tap', String(point.x), String(point.y)]);
      return 'notification-action';
    }
    // 3) Pull the notification drawer open once, in case the head-up
    //    notification has receded but the drawer holds the Approve action.
    await openNotificationDrawerOnce(deadline);
    await sleep(2_000);
  }
  throw new Error(`No Approve control appeared within ${timeoutMs}ms`);
}

let drawerOpened = false;
async function openNotificationDrawerOnce(deadline) {
  if (drawerOpened || Date.now() > deadline - 20_000) {
    return;
  }
  drawerOpened = true;
  await adb(['shell', 'cmd', 'statusbar', 'expand-notifications']);
  await sleep(1_500);
}

async function notificationApproveAction() {
  // Notification action buttons are not in the accessibility tree of the app;
  // read the notification's big-content view via dumpsys instead.
  try {
    const out = await shell('dumpsys notification --noredact | grep -A5 "android.text" | head -40');
    if (out.includes('Approve')) {
      // fall through: the drawer tap above brings the buttons on screen
      return null;
    }
  } catch {
    // ignore
  }
  return null;
}

// --------------------------------------------------------------------------
// Main — every step is wrapped in runStep() so the run log (and the summary
// report) shows exactly which steps succeeded before a failure.
// --------------------------------------------------------------------------

async function runStep(name, fn) {
  logRun(`step: ${name} ...`);
  const started = Date.now();
  try {
    const result = await fn();
    logRun(`step: ${name} done in ${Date.now() - started}ms${typeof result === 'string' ? ` (${result})` : ''}`);
    return result;
  } catch (error) {
    logRun(`step: ${name} FAILED after ${Date.now() - started}ms: ${error?.message ?? error}`);
    throw error;
  }
}

async function main() {
  const runStarted = Date.now();
  globalThis.__runStartedAt = runStarted;
  logRun(`tenant=${HOST} realm=${REALM} reg=${TREE_REG} auth=${TREE_AUTH} user=${USER} pkg=${PKG} poster=${POSTER}`);

  // ---------- Cleanup: fresh state on device and server ----------
  await runStep('cleanup: delete server-side push devices', deleteServerDevices);
  await runStep('cleanup: reset app data (pm clear + re-grant)', resetAppData);

  // ---------- Push registration ----------
  // ORDER MATTERS: the AM transaction's authId expires after ~120 s (measured),
  // so the app must already be sitting on the scanner screen BEFORE the
  // transaction starts — the QR lands in the virtual scene seconds later and
  // the always-on scanner decodes it immediately.
  await runStep('app: launch + navigate to accounts', async () => {
    await launchApp();
    await navigateToAccounts();
  });

  const regCookies = new Map();
  const regTx = await runStep(`AM: start registration transaction (${TREE_REG})`, () =>
    amAuthenticate(TREE_REG, { cookies: regCookies }),
  );
  const regUri = await runStep('AM: extract pushauth:// registration URI', () => {
    const uri = extractRegistrationUri(regTx);
    logRun(`registration URI: ${uri.slice(0, 100)}...`);
    return uri;
  });
  await runStep('poster: swap QR into virtual scene', () => posterSwap(regUri));

  await runStep('app: scan QR + wait for registration', () => scanQrViaApp());

  // The run starts from a pm-cleared app (empty Accounts screen), so any
  // account row visible after the scan belongs to this run's registration.
  let visible = await runStep('verify: account appears on device', () => registeredAccountVisible());
  if (!visible) {
    // Stale-frame decode errors are the POC's dominant transient failure; the
    // worker retried once there, we re-run the scan once here.
    logRun('account not visible yet; re-running scan once (stale-frame retry)');
    await runStep('retry: dismiss scanner + re-swap QR + rescan', async () => {
      await dismissScannerIfOpen();
      // Re-swap the same transaction's URI: the QR may have expired server-side.
      await posterSwap(regUri);
      await scanQrViaApp();
    });
    visible = await runStep('verify (retry): account appears on device', () => registeredAccountVisible(30_000));
    if (!visible) {
      throw new Error('Registration did not complete: account never appeared on the Accounts screen');
    }
  }
  logRun('registration confirmed on device; resuming AM transaction');

  const regToken = await runStep('AM: resume registration transaction -> tokenId', () =>
    amResumeUntilToken(TREE_REG, regTx, regCookies),
  );
  logRun(`REGISTRATION OK tokenId=${regToken.slice(0, 16)}...`);

  // ---------- Push authentication ----------
  const authCookies = new Map();
  const authTx = await runStep(`AM: start authentication transaction (${TREE_AUTH})`, () =>
    amAuthenticate(TREE_AUTH, { cookies: authCookies }),
  );

  const how = await runStep('push: wait for FCM push + approve', () => approvePush());
  logRun(`push approved via ${how}`);

  const authToken = await runStep('AM: resume authentication transaction -> tokenId', () =>
    amResumeUntilToken(TREE_AUTH, authTx, authCookies),
  );
  logRun(`AUTH OK tokenId=${authToken.slice(0, 16)}...`);

  printSummary(true, runStarted);
}

// --------------------------------------------------------------------------
// Summary report — printed at the end of EVERY run, pass or fail.
// --------------------------------------------------------------------------

function printSummary(passed, runStarted) {
  const totalSec = Math.round((Date.now() - runStarted) / 1000);
  const lines = [
    '',
    '================ PUSH E2E SUMMARY ================',
    `result:     ${passed ? 'PASSED' : 'FAILED'}`,
    `tenant:     ${HOST} (realm ${REALM}, user ${USER})`,
    `journeys:   reg=${TREE_REG} auth=${TREE_AUTH}`,
    `duration:   ${totalSec}s`,
    ...stepsSummary(),
    `run log:    ${RUN_LOG}`,
    '==================================================',
  ];
  for (const line of lines) {
    console.log(line);
    try { appendFileSync(RUN_LOG, `${line}\n`); } catch { /* best-effort */ }
  }
}

// Reconstructs the step outcomes from the run log so a failure shows which
// steps succeeded before the failing one.
function stepsSummary() {
  try {
    const content = readFileSync(RUN_LOG, 'utf8');
    return content
      .split('\n')
      .filter((l) => / step: .* (done|FAILED) /.test(l))
      .map((l) => `  ${l.replace(/^\[[^\]]+\] /, '')}`);
  } catch {
    return [];
  }
}

main().catch(async (error) => {
  console.error(`[push-e2e] FAILED: ${error?.message ?? error}`);
  printSummary(false, globalThis.__runStartedAt ?? Date.now());
  // Capture what was actually on screen when the run failed: the UI dump is
  // echoed into the run log (and stdout) so a CI failure shows the screen
  // state without needing the diagnostics artifact.
  try {
    const source = await uiDump();
    const texts = [...source.matchAll(/text="([^"]{1,80})"/g)].map((m) => m[1]).filter((t) => t.trim());
    const descs = [...source.matchAll(/content-desc="([^"]{1,80})"/g)].map((m) => m[1]).filter((t) => t.trim());
    logRun(`UI at failure: text=[${texts.slice(0, 20).join(' | ')}] desc=[${descs.slice(0, 12).join(' | ')}]`);
    console.log(`[push-e2e] UI at failure: text=[${texts.slice(0, 20).join(' | ')}] desc=[${descs.slice(0, 12).join(' | ')}]`);
  } catch (dumpError) {
    logRun(`UI at failure: dump unavailable (${dumpError?.message ?? dumpError})`);
    console.log(`[push-e2e] UI at failure: dump unavailable (${dumpError?.message ?? dumpError})`);
  }
  // Screenshot + logcat BEFORE the harness's cleanup trap kills the emulator
  // (the diagnostics step runs after the script, when the emulator is gone).
  try {
    await shell(`screencap -p /data/local/tmp/push-e2e-failure.png`);
    await adb(['pull', '/data/local/tmp/push-e2e-failure.png', 'push-e2e-failure.png']);
    logRun('failure screenshot saved: push-e2e-failure.png');
  } catch (shotError) {
    logRun(`failure screenshot unavailable: ${shotError?.message ?? shotError}`);
  }
  // Camera state evidence: permission grant, CameraX/Camera2/MLKit activity, crashes.
  try {
    const perm = await shell(`dumpsys package ${PKG} | grep -A2 " CAMERA" | head -8`);
    logRun(`camera permission state: ${perm.trim().replace(/\n/g, ' | ')}`);
  } catch { /* best-effort */ }
  try {
    const logs = await shell(
      'logcat -d -t 800 | grep -iE "CameraX|Camera2CameraImpl|CameraDevice|MLKit|barcode|BarcodeScanner|AndroidRuntime|FATAL|virtualscene|emulator.*camera|cameraserver" | tail -60',
    );
    logRun(`camera/crash logcat tail:\n${logs || '(no matching lines)'}`);
  } catch {
    // best-effort
  }
  process.exit(1);
});
