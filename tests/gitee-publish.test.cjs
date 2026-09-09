// Runs the actual workflow body with fake HTTP responses and real Bash/jq.
// No network calls or release publishing occur in these tests.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const workflow = fs.readFileSync(path.join(__dirname, '../.github/workflows/android-build.yml'), 'utf8');
const section = workflow.split('      - name: Publish Gitee release assets')[1];
const body = section.split('        run: |')[1].split(/\r?\n/).slice(1)
  .filter(line => line.startsWith('          ') || !line.trim())
  .map(line => line.slice(10)).join('\n');
const quote = value => "'" + String(value).replaceAll("'", "'\\''") + "'";
const bashPath = value => value.replaceAll('\\', '/');
const release = { id: 123, tag_name: 'v2.0.24', assets: [
  'app-release.apk', 'app-release.apk.sha256', 'update.json'
].map(name => ({ name, browser_download_url: `https://gitee.com/test/${name}` })) };

function run(overrides = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gitee-publish-test-'));
  const summary = path.join(dir, 'summary.txt');
  fs.mkdirSync(path.join(dir, 'release'));
  for (const asset of release.assets) fs.writeFileSync(path.join(dir, 'release', asset.name), 'fixture');
  const responses = {
    Look: [200, null], Create: [201, release],
    Upload: [201, { browser_download_url: 'https://gitee.com/test/asset' }],
    Verify: [200, release], ...overrides
  };
  const cases = Object.entries(responses).map(([stage, [status, json]]) =>
    `${stage}*) printf '%s' ${quote(JSON.stringify(json))} > "$output"; printf '%s' ${quote(status)} ;;`).join('\n');
  const preamble = `
    jq() { command ${quote(bashPath(process.env.JQ_EXE || 'jq'))} "$@"; }
    curl() {
      local output=""
      while [ "$#" -gt 0 ]; do
        if [ "$1" = "--output" ]; then output="$2"; shift; fi
        shift
      done
      printf '%s\n' "$STAGE" >> requests.txt
      case "$STAGE" in
        ${cases}
        *) return 99 ;;
      esac
    }
  `;
  try {
    const result = spawnSync(process.env.BASH_EXE || 'bash', ['-e'], {
      cwd: dir, input: preamble + body, encoding: 'utf8', timeout: 30000,
      env: { ...process.env, GITEE_TOKEN: 'fake-test-token', RUN_NUMBER: '24',
        GITHUB_STEP_SUMMARY: bashPath(summary) }
    });
    if (result.error) throw result.error;
    return { ...result, summary: fs.existsSync(summary) ? fs.readFileSync(summary, 'utf8') : '',
      calls: fs.readFileSync(path.join(dir, 'requests.txt'), 'utf8') };
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

test('HTTP 200 + null creates the missing release and verifies three uploads', () => {
  const r = run();
  assert.equal(r.status, 0, r.stderr + r.stdout);
  assert.match(r.calls, /Create v2.0.24/);
  assert.equal((r.calls.match(/Upload /g) || []).length, 3);
  assert.match(r.summary, /verified all three/);
});
test('HTTP 404 also creates a release', () => {
  const r = run({ Look: [404, { message: 'Not Found' }] });
  assert.equal(r.status, 0, r.stderr);
  assert.match(r.calls, /Create/);
});
test('an existing release is reused', () => {
  const r = run({ Look: [200, release] });
  assert.equal(r.status, 0, r.stderr);
  assert.doesNotMatch(r.calls, /Create/);
});
test('authentication failure does not create a release and reports its stage', () => {
  const r = run({ Look: [401, { message: 'Unauthorized fake-test-token' }] });
  assert.notEqual(r.status, 0);
  assert.doesNotMatch(r.calls, /Create|Upload/);
  assert.match(r.summary, /HTTP 401/);
  assert.match(r.stdout, /::error::.*Look up/);
  assert.doesNotMatch(r.stdout + r.summary, /fake-test-token/);
});
test('an unexpected object is not mistaken for a missing release', () => {
  const r = run({ Look: [200, { message: 'API error' }] });
  assert.notEqual(r.status, 0);
  assert.doesNotMatch(r.calls, /Create|Upload/);
});
test('null from creation is an explicit failure before upload', () => {
  const r = run({ Create: [200, null] });
  assert.notEqual(r.status, 0);
  assert.doesNotMatch(r.calls, /Upload/);
  assert.match(r.summary, /Create v2.0.24/);
});
test('upload HTTP errors and null responses have actionable diagnostics', () => {
  for (const response of [[403, { message: 'Forbidden' }], [200, null]]) {
    const r = run({ Upload: response });
    assert.notEqual(r.status, 0);
    assert.match(r.summary, /Upload app-release.apk/);
    assert.doesNotMatch(r.calls, /Verify/);
  }
});
test('a missing published attachment cannot report success', () => {
  const r = run({ Verify: [200, { ...release, assets: release.assets.slice(0, 2) }] });
  assert.notEqual(r.status, 0);
  assert.match(r.summary, /Verify published attachments/);
  assert.doesNotMatch(r.summary, /verified all three/);
});
