import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { randomUUID, createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { repo, runtime, baseURL, demoPassword, state } from './lib/local-demo-client.mjs';

// Actual React/CSRF/cookie sessions/Spring/PostgreSQL; no API interception or SQL mutations.
assert.equal(state.data, path.join(runtime, 'pg18'));
const require = createRequire(path.join(repo, 'frontend/package.json'));
const { chromium, expect } = require('@playwright/test');
const output=path.join(runtime, 'admin-security-browser-'+Date.now()); fs.mkdirSync(output,{recursive:true});
const summary={status:'running', mocked:false, startedAt:new Date().toISOString(), checks:[],
  startingHead:execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim(),
  fetchedMain:execFileSync('git',['rev-parse','origin/main'],{cwd:repo,encoding:'utf8'}).trim(),
  sourceHashes:['scripts/check-admin-security-browser.mjs','frontend/src/features/admin/AccountSecurityDialog.tsx',
    'frontend/src/features/admin/UsersPage.tsx','app/src/main/java/sg/edu/nus/serms/shared/security/SessionVersionFilter.java',
    'app/src/main/java/sg/edu/nus/serms/shared/config/SecurityConfig.java',
    'app/src/main/java/sg/edu/nus/serms/shared/security/SermsUserPrincipal.java',
    'app/src/main/java/sg/edu/nus/serms/identity/repository/UserAccountRepository.java',
    'app/src/main/java/sg/edu/nus/serms/identity/repository/AdminUserRepository.java',
    'app/src/main/java/sg/edu/nus/serms/identity/service/AdminUserService.java',
    'app/src/main/resources/db/migration/V007__identity_session_version.sql'].map(file=>({path:file,
      sha256:createHash('sha256').update(fs.readFileSync(path.join(repo,file))).digest('hex')}))};
const browser=await chromium.launch({headless:true});
async function signIn(page,email,password,success=true) {
  await page.goto(baseURL+'/login');
  await expect(page.getByRole('button',{name:'Sign in',exact:true})).toBeVisible();
  await page.getByLabel('Email').fill(email); await page.getByLabel('Password',{exact:true}).fill(password);
  await page.getByRole('button',{name:'Sign in',exact:true}).click();
  if(success) await expect(page.getByRole('button',{name:'Sign out'})).toBeVisible();
  else await expect(page.getByText('Unable to sign in with these credentials.',{exact:true})).toBeVisible();
}
async function expired(page) {
  const result=await page.evaluate(async()=>{
    const response=await fetch('/api/v1/auth/me');
    return {status:response.status,type:(await response.json()).type};
  });
  assert.equal(result.status,401); assert.equal(result.type,'/problems/unauthenticated');
}
async function save(page,operation,fill,button) {
  await page.getByRole('button',{name:new RegExp('^Manage '+operation+' for ')}).click();
  const dialog=page.getByRole('dialog');
  if(fill) await fill(dialog);
  await dialog.getByRole('button',{name:button,exact:true}).click();
  await expect(dialog).toHaveCount(0);
  // Wait for the directory refetch before opening another version-guarded mutation.
  await expect(page.getByRole('table',{name:'User accounts'})).toBeVisible();
}
try {
  for(const [name,viewport] of Object.entries({desktop:{width:1440,height:1000},mobile:{width:390,height:844}})) {
    const contexts=await Promise.all([browser.newContext({viewport}),browser.newContext({viewport}),browser.newContext({viewport})]);
    try {
      const [operator,first,second]=await Promise.all(contexts.map(c=>c.newPage()));
      await signIn(operator,'admin@example.test',demoPassword);
      await operator.getByRole('link',{name:'Users and roles',exact:true}).click();
      const email='security-browser-'+randomUUID()+'@example.test', password=randomUUID(), replacement=randomUUID();
      await operator.getByLabel('Email').fill(email);
      await operator.getByLabel('Display name',{exact:true}).fill('Security browser User');
      await operator.getByLabel('Initial password').fill(password);
      await operator.getByRole('button',{name:'Create account',exact:true}).click();
      await expect(operator.getByText('Account created. The user can now sign in.')).toBeVisible();
      await expect(operator.getByLabel('Initial password')).toHaveValue('');
      await operator.getByLabel('Search users').fill(email); await operator.getByRole('button',{name:'Search',exact:true}).click();
      await expect(operator.getByRole('cell',{name:email,exact:true})).toBeVisible();
      await signIn(first,email,password); await signIn(second,email,password);
      await save(operator,'roles',async dialog=>{
        await dialog.getByLabel('Assigned Approver').check();
        await operator.screenshot({path:path.join(output,name+'-roles.png'),fullPage:true});
      },'Save roles');
      await expect(operator.getByRole('cell',{name:'Borrower · Approver',exact:true})).toBeVisible();
      await expired(first); await expired(second);
      await signIn(first,email,password); await signIn(second,email,password);
      await expect(first.getByRole('link',{name:'Approvals',exact:true})).toBeVisible();
      await save(operator,'status',null,'Confirm disable');
      await expect(operator.getByRole('cell',{name:'Disabled',exact:true})).toBeVisible();
      await expired(first); await signIn(first,email,password,false);
      await save(operator,'status',null,'Confirm activation');
      await expect(operator.getByRole('cell',{name:'Active',exact:true})).toBeVisible();
      await expired(second); // Disable followed by re-enable must not resurrect an unvisited old session.
      await signIn(first,email,password); await signIn(second,email,password);
      await save(operator,'password',async dialog=>{
        await dialog.getByLabel('New password').fill(replacement);
      },'Save new password');
      await expired(first); await expired(second);
      await signIn(first,email,password,false); await signIn(first,email,replacement);
      assert.ok(await operator.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2));
      await operator.screenshot({path:path.join(output,name+'-security.png'),fullPage:true});
      const records=await operator.evaluate(async email=>{
        const directory=await (await fetch('/api/v1/admin/users?search='+encodeURIComponent(email))).json();
        const id=directory.content[0].userId;
        const audit=await (await fetch('/api/v1/admin/audit?entityType=User&size=100')).json();
        return {version:directory.content[0].version,actions:audit.content.filter(row=>row.entityId===id).map(row=>row.action)};
      },email);
      assert.equal(records.version,4);
      for(const action of ['USER_CREATED','USER_ROLES_CHANGED','USER_DEACTIVATED','USER_ACTIVATED','USER_PASSWORD_RESET'])
        assert.equal(records.actions.filter(a=>a===action).length,1,'One audit per committed action: '+action);
      summary.checks.push(`${name}: UI create/explicit roles/disable/activate/password reset; two old cookie sessions revoked; disabled/old-password login denied; new login succeeds; reactivation does not resurrect sessions; five audits; version4; no overflow`);
      console.log('PASS administrator security browser: '+name);
    } finally { await Promise.all(contexts.map(c=>c.close())); }
  }
  summary.status='passed';
} catch(failure) { summary.status='failed'; summary.failure=failure.message; throw failure; }
finally {
  summary.finishedAt=new Date().toISOString(); fs.writeFileSync(path.join(output,'verification.json'),JSON.stringify(summary,null,2)+'\n');
  await browser.close(); console.log('Admin security evidence: '+output);
}
