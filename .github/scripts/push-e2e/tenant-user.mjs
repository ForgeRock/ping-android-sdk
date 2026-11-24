#!/usr/bin/env node
/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

// Per-run disposable test user for the push e2e (create / delete / sweep).
//
// Why: sharing one tenant user across runs forces a server-side device cleanup at the
// start of every run (a DELETE that currently answers HTTP 500 from an AM bug), makes
// concurrent runs trample each other's devices, and leaves a leftover device that makes
// the registration journey skip the QR. A brand-new user per run starts with no devices
// and is deleted at the end, so none of that applies.
//
// Usage:
//   node tenant-user.mjs create   sweep stale users, then create push-e2e-<tag>-<epoch>
//   node tenant-user.mjs delete   delete the user in PUSH_E2E_RUN_USER_ID (never fails the job)
//   node tenant-user.mjs sweep    only delete stale push-e2e-* users
//
// Inputs (env):
//   SVCACCT_ID, SVCACCT_JWK   service account used to mint a token (needs the fr:idm:* scope)
//   AIC_HOST                  tenant host (PUSH_E2E_HOST is accepted as a fallback)
//   PUSH_E2E_REALM            realm, default alpha (users live in the <realm>_user managed object)
//   PUSH_E2E_RUN_USER_ID      for `delete`: the managed object id returned by `create`
//
// Output of `create`:
//   - in GitHub Actions (GITHUB_ENV set): PUSH_E2E_RUN_USER / PUSH_E2E_RUN_PASSWORD (masked) /
//     PUSH_E2E_RUN_USER_ID are written to GITHUB_ENV and user_id to GITHUB_OUTPUT.
//   - locally: `export ...` lines on stdout, for `eval "$(node tenant-user.mjs create)"`.
// Diagnostics go to stderr. Neither the token nor the password is ever logged.

import { execFile } from 'node:child_process';
import { randomInt } from 'node:crypto';
import { appendFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

const PREFIX = 'push-e2e-';
// userName = push-e2e-<tag>-<epoch seconds>; only names of exactly this shape are ever swept.
const OWN_NAME = /^push-e2e-(?:local|\d+-\d+)-(\d{10})$/;
const STALE_AFTER_HOURS = 6;

const HOST = process.env.AIC_HOST || process.env.PUSH_E2E_HOST;
const REALM = process.env.PUSH_E2E_REALM || 'alpha';
const BASE = `https://${HOST}/openidm/managed/${REALM}_user`;

function log(message) {
  console.error(`[tenant-user] ${message}`);
}

function fail(message) {
  console.error(`[tenant-user] ERROR: ${message}`);
  process.exit(1);
}

// Mint a token with ONLY the IDM scope (least privilege for user management).
async function mintToken() {
  const script = fileURLToPath(new URL('./mint-admin-token.mjs', import.meta.url));
  try {
    const { stdout } = await execFileAsync(process.execPath, [script], {
      env: { ...process.env, AIC_HOST: HOST, SVCACCT_SCOPE: 'fr:idm:*' },
      maxBuffer: 1 << 20,
    });
    return stdout.trim();
  } catch (error) {
    // One meaningful line (not a stack trace). Node's key errors quote the message
    // with ANSI styling; strip quotes and color codes first.
    const lines = String(error.stderr || error.message)
      .trim()
      // eslint-disable-next-line no-control-regex
      .replace(/\x1b\[[0-9;]*m/g, '')
      .replace(/^['"`]+|['"`]+$/g, '')
      .split('\n');
    const line = lines.find((l) => /Error|failed|Missing|invalid/i.test(l)) ?? lines.at(-1) ?? 'unknown error';
    throw new Error(`token exchange failed: ${line.trim().slice(0, 300)}`);
  }
}

async function idm(token, path, init = {}) {
  const response = await fetch(`${BASE}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      'Accept-API-Version': 'resource=1.0',
      'Content-Type': 'application/json',
      ...(init.headers ?? {}),
    },
    signal: AbortSignal.timeout(30_000),
  });
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    // non-JSON body: keep the raw text for the error message
  }
  return { status: response.status, json, text };
}

function describe(res) {
  return (res.json?.message || res.text || '(empty body)').toString().slice(0, 200);
}

// 24 chars with every character class, from a set that is safe in headers and shells.
function randomPassword() {
  const sets = ['ABCDEFGHJKLMNPQRSTUVWXYZ', 'abcdefghijkmnopqrstuvwxyz', '23456789', '#%+-_='];
  const all = sets.join('');
  const chars = sets.map((set) => set[randomInt(set.length)]);
  while (chars.length < 24) {
    chars.push(all[randomInt(all.length)]);
  }
  for (let i = chars.length - 1; i > 0; i -= 1) {
    const j = randomInt(i + 1);
    [chars[i], chars[j]] = [chars[j], chars[i]];
  }
  return chars.join('');
}

// Delete push-e2e-* users left behind by runs that were killed before their teardown ran.
async function sweep(token) {
  const query = new URLSearchParams({ _queryFilter: `userName sw "${PREFIX}"`, _fields: 'userName' });
  const res = await idm(token, `?${query}`);
  if (res.status !== 200) {
    log(`sweep skipped: query returned HTTP ${res.status} (${describe(res)})`);
    return 0;
  }
  const cutoff = Date.now() / 1000 - STALE_AFTER_HOURS * 3600;
  let removed = 0;
  for (const user of res.json?.result ?? []) {
    const match = OWN_NAME.exec(String(user.userName));
    if (!match || Number(match[1]) > cutoff) {
      continue; // not one of ours, or still fresh (possibly another run in flight)
    }
    const del = await idm(token, `/${user._id}`, { method: 'DELETE', headers: { 'If-Match': '*' } });
    if (del.status === 200 || del.status === 204 || del.status === 404) {
      removed += 1;
      log(`swept stale user ${user.userName}`);
    } else {
      log(`could not sweep ${user.userName}: HTTP ${del.status} (${describe(del)})`);
    }
  }
  return removed;
}

async function createUser(token) {
  const tag = process.env.GITHUB_RUN_ID
    ? `${process.env.GITHUB_RUN_ID}-${process.env.GITHUB_RUN_ATTEMPT || 1}`
    : 'local';
  const userName = `${PREFIX}${tag}-${Math.floor(Date.now() / 1000)}`;
  const password = randomPassword();
  const body = JSON.stringify({
    userName,
    givenName: 'Push',
    sn: 'E2E',
    mail: `${userName}@example.invalid`,
    password,
    description: 'Disposable push e2e test user - deleted automatically at the end of the run',
  });

  let res = await idm(token, '?_action=create', { method: 'POST', body });
  if (res.status >= 500) {
    log(`create returned HTTP ${res.status}; retrying once`);
    await new Promise((resolve) => setTimeout(resolve, 2_000));
    res = await idm(token, '?_action=create', { method: 'POST', body });
  }
  if ((res.status !== 201 && res.status !== 200) || !res.json?._id) {
    throw new Error(`create failed: HTTP ${res.status} (${describe(res)})`);
  }
  return { userName, password, userId: res.json._id };
}

function publish({ userName, password, userId }) {
  if (process.env.GITHUB_ENV) {
    console.log(`::add-mask::${password}`); // before the value is written anywhere
    appendFileSync(
      process.env.GITHUB_ENV,
      `PUSH_E2E_RUN_USER=${userName}\nPUSH_E2E_RUN_PASSWORD=${password}\nPUSH_E2E_RUN_USER_ID=${userId}\n`,
    );
    if (process.env.GITHUB_OUTPUT) {
      appendFileSync(process.env.GITHUB_OUTPUT, `user_id=${userId}\n`);
    }
  } else {
    console.log(`export PUSH_E2E_RUN_USER='${userName}'`);
    console.log(`export PUSH_E2E_RUN_PASSWORD='${password}'`);
    console.log(`export PUSH_E2E_RUN_USER_ID='${userId}'`);
  }
  log(`created test user ${userName} (${userId})`);
}

async function deleteUser(token, userId) {
  let last = '';
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    const res = await idm(token, `/${userId}`, { method: 'DELETE', headers: { 'If-Match': '*' } });
    if (res.status === 200 || res.status === 204 || res.status === 404) {
      log(`deleted test user ${userId} (HTTP ${res.status})`);
      return true;
    }
    last = `HTTP ${res.status} (${describe(res)})`;
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }
  console.error(`::warning::could not delete test user ${userId}: ${last}; the next run's sweep will remove it`);
  return false;
}

async function main() {
  const command = process.argv[2];
  if (!['create', 'delete', 'sweep'].includes(command)) {
    fail('usage: tenant-user.mjs create|delete|sweep');
  }
  if (!HOST) {
    fail('AIC_HOST (or PUSH_E2E_HOST) is not set');
  }
  if (!process.env.SVCACCT_ID || !process.env.SVCACCT_JWK) {
    fail('SVCACCT_ID and SVCACCT_JWK are required');
  }

  if (command === 'delete') {
    // Teardown must never fail the job: a leaked user is swept by the next run.
    const userId = process.env.PUSH_E2E_RUN_USER_ID;
    if (!userId) {
      log('no PUSH_E2E_RUN_USER_ID: nothing to delete');
      return;
    }
    try {
      await deleteUser(await mintToken(), userId);
    } catch (error) {
      console.error(`::warning::test user teardown failed: ${error.message}`);
    }
    return;
  }

  try {
    const token = await mintToken();
    const removed = await sweep(token);
    if (command === 'sweep') {
      log(`sweep done: ${removed} stale user(s) removed`);
      return;
    }
    publish(await createUser(token));
  } catch (error) {
    fail(error.message);
  }
}

await main();
