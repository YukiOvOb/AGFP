import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, mkdirSync, writeFileSync} from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {determineScope, needsApplicationChecks} from './ci-scope.mjs';

test('only proven documentation changes avoid application execution', () => {
  assert.equal(needsApplicationChecks(['README.md','docs/evidence/run.png','.agents/skills/serms-task/SKILL.md','.gitignore']),false);
  for (const files of [[],['app/pom.xml'],['frontend/pnpm-lock.yaml'],['database/test.sql'],
    ['integration/notifications/pom.xml'],['.github/workflows/ci.yml'],['scripts/ci-scope.mjs'],
    ['docs/report.md','frontend/src/app.tsx'],['.gitattributes'],['unknown.file']])
    assert.equal(needsApplicationChecks(files),true);
});

test('real push and PR merge diffs include deleted and renamed application files', () => {
  mkdirSync('.cache', {recursive:true});
  const repo = mkdtempSync(path.resolve('.cache/ci-scope-fixture-'));
  const git = args => execFileSync('git',args,{cwd:repo,encoding:'utf8',stdio:['ignore','pipe','pipe']});
  git(['init','-q']); git(['config','user.name','CI scope fixture']);
  git(['config','user.email','ci-scope@example.invalid']);
  writeFileSync(path.join(repo,'README.md'),'baseline');
  writeFileSync(path.join(repo,'runtime.js'),'runtime');
  git(['add','.']); git(['commit','-qm','baseline']);
  const base = git(['rev-parse','HEAD']).trim();
  writeFileSync(path.join(repo,'README.md'),'documentation update');
  git(['add','.']); git(['commit','-qm','docs']);
  const docs = git(['rev-parse','HEAD']).trim();
  const eventFile = path.join(repo,'event.json');
  const context = {GITHUB_EVENT_NAME:'push', GITHUB_SHA:docs, GITHUB_EVENT_PATH:eventFile};
  writeFileSync(eventFile,JSON.stringify({before:base}));
  assert.equal(determineScope(context,git).run,false);
  assert.equal(determineScope({...context,GITHUB_SHA:base},git).run,true);
  writeFileSync(eventFile,JSON.stringify({before:'0'.repeat(40)}));
  assert.equal(determineScope(context,git).run,true);
  writeFileSync(eventFile,JSON.stringify({before:'f'.repeat(40)}));
  assert.equal(determineScope(context,git).run,true);
  git(['switch','-qc','proposal',base]);
  git(['mv','runtime.js','runtime.md']); git(['commit','-qm','rename executable into documentation']);
  const proposal = git(['rev-parse','HEAD']).trim();
  writeFileSync(eventFile,JSON.stringify({before:base}));
  assert.equal(determineScope({...context,GITHUB_SHA:proposal},git).run,true);
  git(['switch','-qc','target',docs]);
  git(['merge','--no-ff','-qm','test merge','proposal']);
  const merged = git(['rev-parse','HEAD']).trim();
  assert.equal(determineScope({...context,GITHUB_EVENT_NAME:'pull_request',GITHUB_SHA:merged},git).run,true);
  git(['switch','-qc','docs-proposal',base]);
  writeFileSync(path.join(repo,'README.md'),'PR prose');
  git(['add','README.md']); git(['commit','-qm','PR docs']);
  git(['switch','-qc','docs-target',base]);
  writeFileSync(path.join(repo,'runtime.js'),'updated target implementation');
  git(['add','runtime.js']); git(['commit','-qm','target code']);
  git(['merge','--no-ff','-qm','merge doc proposal','docs-proposal']);
  assert.equal(determineScope({...context,GITHUB_EVENT_NAME:'pull_request',
    GITHUB_SHA:git(['rev-parse','HEAD']).trim()},git).run,false);
  assert.equal(determineScope({...context,GITHUB_EVENT_NAME:'workflow_dispatch'},git).run,true);
});

test('malformed/missing payloads and non-merge PR checkouts run all tests', () => {
  assert.equal(determineScope({GITHUB_SHA:'a'.repeat(40)},()=>{throw new Error('unavailable')}).run,true);
});
