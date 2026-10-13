import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';

export const requiredChecks = [
  'Secrets scan (gitleaks)', 'Vulnerability scan (Trivy)', 'Dependency review',
  'database-test', 'java-tests', 'frontend', 'notification-contract', 'checkout-return',
  'collaboration-docs', 'release-config', 'release-images',
];

export function validateContext({ref, event, sha, ready}) {
  assert.equal(ref, 'refs/heads/main', 'Releases require the main branch');
  assert.ok(['push', 'workflow_dispatch'].includes(event), 'PR/fork/workflow-run payloads cannot release');
  assert.match(sha ?? '', /^[a-f0-9]{40}$/, 'Release revision must be a full commit SHA');
  assert.equal(ready, 'true', 'Complete server inventory, backup/restore rehearsal and local/member acceptance before enabling SERMS_PRODUCTION_READY');
}

export function evaluateChecks(sha, mainSha, checks) {
  assert.equal(sha, mainSha, 'Superseded main revisions cannot release');
  return requiredChecks.map(name => {
    const latest = checks.filter(c => c.name === name && c.head_sha === sha && c.app?.slug === 'github-actions')
      .sort((a, b) => b.id - a.id)[0];
    if (!latest || latest.status !== 'completed') return {name, state: 'pending'};
    return {name, state: latest.conclusion === 'success' ? 'success' : 'failed'};
  });
}

export async function verifyRelease(env = process.env) {
  validateContext({ref: env.GITHUB_REF, event: env.GITHUB_EVENT_NAME, sha: env.GITHUB_SHA, ready: env.SERMS_PRODUCTION_READY});
  assert.match(env.GITHUB_REPOSITORY ?? '', /^[\w.-]+\/[\w.-]+$/);
  assert.ok(env.GITHUB_TOKEN, 'A read-only GitHub token is required');
  const api = env.GITHUB_API_URL || 'https://api.github.com';
  const get = async route => {
    const response = await fetch(`${api}/repos/${env.GITHUB_REPOSITORY}/${route}`, {
      headers: {Authorization: `Bearer ${env.GITHUB_TOKEN}`, Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28'},
      signal: AbortSignal.timeout(15000),
    });
    assert.ok(response.ok, `GitHub verification request failed (${response.status})`);
    return response.json();
  };
  const deadline = Date.now() + 15 * 60 * 1000;
  while (Date.now() < deadline) {
    const main = await get('git/ref/heads/main');
    const checks = [];
    for (let page = 1; ; page++) {
      const data = await get(`commits/${env.GITHUB_SHA}/check-runs?per_page=100&page=${page}`);
      checks.push(...data.check_runs);
      if (data.check_runs.length < 100) break;
    }
    const states = evaluateChecks(env.GITHUB_SHA, main.object.sha, checks);
    assert.ok(!states.some(c => c.state === 'failed'), `Release check failed: ${states.filter(c => c.state === 'failed').map(c => c.name).join(', ')}`);
    if (states.every(c => c.state === 'success')) { console.log(`Verified current main ${env.GITHUB_SHA}: ${states.length} successful GitHub Actions checks`); return; }
    console.log(`Waiting for: ${states.filter(c => c.state === 'pending').map(c => c.name).join(', ')}`);
    await new Promise(resolve => setTimeout(resolve, 30000));
  }
  throw new Error('Release checks did not all succeed before the deadline; no SSH deployment permitted');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href)
  await verifyRelease();
