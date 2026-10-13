import {test} from 'node:test';
import assert from 'node:assert/strict';
import {validateContext, evaluateChecks, requiredChecks} from './verify-release.mjs';

const sha = 'a'.repeat(40);
const context = {ref:'refs/heads/main', event:'push', sha, ready:'true'};
const successful = () => requiredChecks.map((name, id) => ({id, name, head_sha:sha, status:'completed', conclusion:'success', app:{slug:'github-actions'}}));
test('allows push and manual current-main release after explicit readiness', () => {
  validateContext(context); validateContext({...context, event:'workflow_dispatch'});
  assert.ok(evaluateChecks(sha, sha, successful()).every(c => c.state === 'success'));
});
test('rejects manual feature, tag and PR refs', () => {
  for (const ref of ['refs/heads/feature/demo','refs/tags/v1','refs/pull/11/merge'])
    assert.throws(() => validateContext({...context, ref, event:'workflow_dispatch'}));
});
test('rejects untrusted triggers, short revisions and incomplete handover', () => {
  for (const event of ['pull_request','pull_request_target','workflow_run']) assert.throws(() => validateContext({...context,event}));
  assert.throws(() => validateContext({...context,sha:'aaaaaaa'}));
  assert.throws(() => validateContext({...context,ready:undefined}));
});
test('rejects superseded main and does not reuse checks from another SHA or app', () => {
  assert.throws(() => evaluateChecks(sha, 'b'.repeat(40), successful()));
  for (const change of [{head_sha:'b'.repeat(40)}, {app:{slug:'external-ci'}}]) {
    const checks = successful(); checks[0] = {...checks[0], ...change};
    assert.equal(evaluateChecks(sha,sha,checks)[0].state,'pending');
  }
});
test('missing and running checks block release; failure and skipped are not success', () => {
  assert.equal(evaluateChecks(sha,sha,[]).filter(c => c.state === 'pending').length,requiredChecks.length);
  for (const change of [{status:'in_progress'}, {conclusion:'failure'}, {conclusion:'skipped'}, {conclusion:'cancelled'}]) {
    const checks = successful(); checks[0] = {...checks[0],...change};
    assert.notEqual(evaluateChecks(sha,sha,checks)[0].state,'success');
  }
});
test('latest rerun supersedes an earlier successful check', () => {
  const checks = successful(); checks.push({...checks[0],id:1000,conclusion:'failure'});
  assert.equal(evaluateChecks(sha,sha,checks)[0].state,'failed');
});
