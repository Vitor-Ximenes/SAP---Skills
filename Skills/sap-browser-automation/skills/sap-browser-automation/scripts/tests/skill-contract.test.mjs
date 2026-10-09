import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const skillRoot = new URL('../../', import.meta.url);

async function read(relativePath) {
  return readFile(new URL(relativePath, skillRoot), 'utf8');
}

test('main skill routes standalone automation through bundled helpers', async () => {
  const skill = await read('SKILL.md');

  assert.match(skill, /scripts\/edge-profile\.ps1/);
  assert.match(skill, /scripts\/cdp-agent\.mjs/);
  assert.match(skill, /Node(?:\.js)? 22/i);
});

test('auth workflow captures volatile state before closing and cloning Edge', async () => {
  const skill = await read('SKILL.md');
  const capture = skill.indexOf('export-auth');
  const close = skill.indexOf('Close normal Edge', capture);
  const clone = skill.indexOf('CloneLaunch', close);

  assert.ok(capture >= 0, 'missing export-auth step');
  assert.ok(close > capture, 'normal Edge must close after auth export');
  assert.ok(clone > close, 'profile cloning must follow normal Edge shutdown');
});

test('auth reference uses current CDP cookie methods and executable commands', async () => {
  const auth = await read('references/auth-state-bootstrap.md');

  assert.match(auth, /Storage\.getCookies/);
  assert.match(auth, /Storage\.setCookies/);
  assert.doesNotMatch(auth, /Network\.getAllCookies/);
  assert.match(auth, /cdp-agent\.mjs export-auth/);
  assert.match(auth, /cdp-agent\.mjs import-auth/);
});

test('Edge reference preserves a selected profile and uses dynamic ports', async () => {
  const edge = await read('references/edge-cdp-control.md');

  assert.match(edge, /edge-profile\.ps1 -Action CloneLaunch/);
  assert.match(edge, /-ProfileName ['"]Profile 2['"]/);
  assert.match(edge, /remote-debugging-port=0/);
  assert.doesNotMatch(edge, /--profile-directory=Default/);
});

test('in-app validation is explicitly deferred to desktop runtime', async () => {
  const inApp = await read('references/in-app-browser-auth.md');

  assert.match(inApp, /Codex or Claude Desktop/i);
  assert.match(inApp, /runtime validation is deferred/i);
});

test('explicit browser requests stay on the requested surface and require a live MCP check', async () => {
  const skill = await read('SKILL.md');

  assert.match(skill, /use only that\s+browser and connection method/i);
  assert.match(skill, /active tool registry/i);
  assert.match(skill, /live .*handshake/i);
  assert.match(skill, /restart Codex|open a new task/i);
  assert.match(skill, /Do not silently switch to the In-app Browser/i);
});

test('SAC test automation preserves the same browser routing contract', async () => {
  const skill = await readFile(
    new URL('../../../../../../plugins/sap-sac-test-automation/skills/sap-sac-test-automation/SKILL.md', import.meta.url),
    'utf8',
  );
  const chromeReference = await readFile(
    new URL('../../../../../../plugins/sap-sac-test-automation/skills/sap-sac-test-automation/references/chrome-devtools-mcp.md', import.meta.url),
    'utf8',
  );

  assert.match(skill, /explicitly named.*do not switch to another browser surface/i);
  assert.match(skill, /active registry/i);
  assert.match(skill, /live `list_pages`.*handshake/i);
  assert.match(chromeReference, /DevToolsActivePort.*browser window alone is\s+not proof/i);
  assert.match(chromeReference, /NPM_CONFIG_CACHE/);
  assert.match(chromeReference, /RemoteDebuggingAllowed/);
});
