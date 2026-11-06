#!/usr/bin/env node
/*
 * Copyright (c) 2026 Ping Identity Corporation. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

// Mint an AIC admin bearer token from a service account's private JWK.
//
// The flow (verified against frodo-lib's exchange with this tenant):
//   1. Sign an RS256 JWT with the service account's private key:
//        iss = sub = <service account UUID>
//        aud = https://<host>:443/am/oauth2/access_token
//        exp = now + 5 minutes, jti = random UUID
//   2. POST https://<host>/am/oauth2/access_token
//        grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer
//        client_id=service-account
//        assertion=<jwt>
//   3. Response JSON contains access_token (bearer) and expires_in.
//
// Inputs (env):
//   SVCACCT_ID    service account UUID (the "Frodo-SA-..." identity's id)
//   SVCACCT_JWK   the service account's private JWK as a JSON string
//                 (in frodo's Connections.json this is "svcacctJwk", stored
//                 encrypted; decrypt once locally and paste the plaintext JSON)
//   AIC_HOST      tenant host, e.g. openam-sdks.forgeblocks.com
//
// Output (stdout): the bearer access token. All diagnostics go to stderr.
// Exit 1 on failure.

import { createSign, createPrivateKey } from 'node:crypto';
import { randomUUID } from 'node:crypto';

const SVCACCT_ID = required('SVCACCT_ID');
const SVCACCT_JWK = required('SVCACCT_JWK');
const AIC_HOST = required('AIC_HOST');

function required(name) {
  const value = process.env[name];
  if (!value) {
    console.error(`Missing required env ${name}`);
    process.exit(2);
  }
  return value;
}

function base64url(input) {
  return Buffer.from(input).toString('base64url');
}

// Sign the JWT with the JWK's private key (RS256, matching frodo's exchange).
function signJwt(jwk, claims) {
  const header = { alg: 'RS256', typ: 'JWT' };
  if (jwk.kid) {
    header.kid = jwk.kid;
  }
  const signingInput = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claims))}`;
  const keyObject = createPrivateKey({ key: jwk, format: 'jwk' });
  const signature = createSign('RSA-SHA256').update(signingInput).sign(keyObject);
  return `${signingInput}.${signature.toString('base64url')}`;
}

const jwk = JSON.parse(SVCACCT_JWK);
const now = Math.floor(Date.now() / 1000);
const claims = {
  iss: SVCACCT_ID,
  sub: SVCACCT_ID,
  aud: `https://${AIC_HOST}:443/am/oauth2/access_token`,
  exp: now + 300,
  jti: randomUUID(),
};

const assertion = signJwt(jwk, claims);

const body = new URLSearchParams({
  grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
  client_id: 'service-account',
  // The exchange declines without an explicit scope (authorization_declined);
  // fr:am:* covers the admin device API used by the cleanup step. SVCACCT_SCOPE
  // overrides it (space separated) to experiment with other token scopes.
  scope: process.env.SVCACCT_SCOPE || 'fr:am:*',
  assertion,
});

const response = await fetch(`https://${AIC_HOST}/am/oauth2/access_token`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
    'Accept-API-Version': 'protocol=1.0,resource=1.0',
  },
  body,
});

const json = await response.json();
if (!response.ok || !json.access_token) {
  console.error(`Token exchange failed: HTTP ${response.status} ${JSON.stringify(json).slice(0, 300)}`);
  process.exit(1);
}

console.error(`Minted admin token for service account ${SVCACCT_ID} (expires_in=${json.expires_in ?? 'unknown'}s)`);
process.stdout.write(json.access_token);
