import {test} from 'node:test';
import assert from 'node:assert/strict';
import {validateContext, evaluateChecks, requiredChecks, verifyRelease} from './verify-release.mjs';

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

const env = {GITHUB_REF:context.ref,GITHUB_EVENT_NAME:context.event,GITHUB_SHA:sha,
  SERMS_PRODUCTION_READY:'true',GITHUB_REPOSITORY:'example/serms',GITHUB_TOKEN:'fixture-only'};
const response = data => ({ok:true,status:200,json:async()=>data});
test('release verification uses two read requests when current-main checks pass', async () => {
  const routes = [];
  await verifyRelease(env,{request:async(url,options)=>{
    assert.equal(options.method,undefined); // Fetch default GET; never dispatch or mutate.
    routes.push(url);
    return response(url.endsWith('git/ref/heads/main')?{object:{sha}}:{check_runs:successful()});
  },wait:async()=>assert.fail('completed checks must not poll'),log:()=>{}});
  assert.equal(routes.length,2);
});
test('missing checks exhaust a finite budget and wait one minute between polls', async () => {
  let calls=0, waits=0;
  await assert.rejects(verifyRelease(env,{request:async url=>{
    calls++; return response(url.endsWith('git/ref/heads/main')?{object:{sha}}:{check_runs:[]});
  },wait:async ms=>{assert.equal(ms,60000);waits++;},log:()=>{}}),/deadline/);
  assert.equal(calls,30); assert.equal(waits,14);
});
test('403 and 429 stop immediately without retry', async () => {
  for(const status of [403,429]){
    let calls=0;
    await assert.rejects(verifyRelease(env,{request:async()=>{calls++;return {ok:false,status};},
      wait:async()=>assert.fail('rate limits must not be retried'),log:()=>{}}),new RegExp(String(status)));
    assert.equal(calls,1);
  }
});
test('unbounded check pagination fails closed within four requests', async () => {
  let calls=0;
  await assert.rejects(verifyRelease(env,{request:async url=>{
    calls++;return response(url.endsWith('git/ref/heads/main')?{object:{sha}}:
      {check_runs:Array.from({length:100},()=>successful()[0])});
  },wait:async()=>assert.fail('overflow must stop immediately'),log:()=>{}}),/pagination/);
  assert.equal(calls,4);
});
test('disabled readiness never makes an API request', async () => {
  await assert.rejects(verifyRelease({...env,SERMS_PRODUCTION_READY:'false'},
    {request:async()=>assert.fail('disabled releases must not request GitHub')}),/SERMS_PRODUCTION_READY/);
});
