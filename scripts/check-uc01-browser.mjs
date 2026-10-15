// UC01 acceptance in a real browser against this checkout's local demo (React → Spring Boot →
// PostgreSQL 18, nothing mocked). Two borrowers share one search link and compete for one slot,
// then the item limit, approval routing, retired-item hiding and cancellation are exercised.
// Start the demo first (docs/local-demo.md); every reservation this script makes is cancelled.
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {repo, runtime, baseURL, demoPassword} from './lib/local-demo-client.mjs';

const require = createRequire(path.join(repo, 'frontend/package.json'));
const {chromium, expect} = require('@playwright/test');
const evidence = path.join(repo, 'docs/evidence/uc01');
fs.mkdirSync(evidence, {recursive:true});
const checks = [], errors = [];
const summary = {status:'running', baseURL, checks, startedAt:new Date().toISOString(), mocked:false};
const pass = name => {checks.push(name); console.log(`PASS ${name}`);};
const HOUR = 3600000;
// datetime-local value in Singapore time (UTC+8, no daylight saving).
const sgt = ms => new Date(ms + 8 * HOUR).toISOString().slice(0, 16);
// SERMS_CHROMIUM points at a preinstalled browser when Playwright's own download is unavailable.
const browser = await chromium.launch({headless:true, executablePath:process.env.SERMS_CHROMIUM || undefined});

async function signIn(context, email) {
  const page = await context.newPage();
  page.setDefaultTimeout(30000);
  page.on('pageerror', error => errors.push(`${email}: ${error.message}`));
  await page.goto(baseURL + '/login', {waitUntil:'domcontentloaded'});
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', {exact:true}).fill(demoPassword);
  await page.getByRole('button', {name:'Sign in', exact:true}).click();
  await page.getByRole('heading', {level:1, name:/^Welcome/}).waitFor();
  return page;
}

async function search(page, {category, from, to}) {
  await page.getByRole('link', {name:'Find equipment', exact:true}).first().click();
  await page.getByLabel('Category').selectOption(category);
  await page.getByLabel('From (SGT)').fill(sgt(from));
  await page.getByLabel('Until (SGT)').fill(sgt(to));
  await page.getByRole('button', {name:'Search', exact:true}).click();
  await page.waitForURL(/from=/);
}

async function reserve(page, name, from, to, purpose) {
  await page.goto(baseURL + '/equipment', {waitUntil:'domcontentloaded'});
  await page.getByLabel('Name or asset tag').fill(name);
  await page.getByRole('button', {name:'Search', exact:true}).click();
  await page.getByRole('link', {name:`Reserve ${name}`, exact:true}).click();
  await page.getByLabel('Start (SGT)').fill(sgt(from));
  await page.getByLabel('End (SGT)').fill(sgt(to));
  await page.getByLabel('Purpose (optional)').fill(purpose);
  await page.getByRole('button', {name:/^(Reserve|Request reservation)$/}).click();
}

async function cancel(page, purpose, name) {
  await page.goto(baseURL + '/reservations', {waitUntil:'domcontentloaded'});
  const row = page.getByRole('row').filter({hasText:purpose});
  await row.getByRole('button', {name:`Cancel reservation for ${name}`}).click();
  await row.getByRole('button', {name:'Yes, cancel'}).click();
  await expect(row).toContainText('Cancelled');
}

const created = [];
try {
  // A fresh hour-aligned window two to nine days ahead, so reruns do not collide.
  const base = Math.floor((Date.now() + 2 * 86400000) / HOUR) * HOUR
    + Math.floor(Math.random() * 7 * 24) * HOUR;
  const viewports = [['desktop', {width:1440, height:1000}], ['mobile', {width:390, height:844}]];
  for (const [index, [name, viewport]] of viewports.entries()) {
    const start = base + index * 6 * HOUR, end = start + 2 * HOUR;
    const run = `${name} UC01 acceptance ${Date.now()}`;
    const contextA = await browser.newContext({viewport}), contextB = await browser.newContext({viewport});
    const a = await signIn(contextA, 'borrower@example.test');
    const b = await signIn(contextB, 'borrower2@example.test');

    await search(a, {category:'Photography', from:start, to:end});
    const tripodA = a.getByRole('row').filter({hasText:'Professional tripod'});
    await expect(tripodA).toContainText('Free');
    await expect(a.getByRole('row').filter({hasText:'Retired camera'})).toHaveCount(0);
    const shared = a.url();
    pass(`${name}: category and window search shows the tripod free and hides the retired camera`);

    await tripodA.getByRole('link', {name:'Reserve Professional tripod', exact:true}).click();
    await expect(a.getByLabel('Start (SGT)')).toHaveValue(sgt(start));
    await a.getByLabel('Purpose (optional)').fill(`${run} A`);
    await a.getByRole('button', {name:'Reserve', exact:true}).click();
    await a.waitForURL('**/reservations');
    await expect(a.getByRole('row').filter({hasText:`${run} A`})).toContainText('Confirmed');
    created.push([a, `${run} A`, 'Professional tripod']);
    await a.screenshot({path:path.join(evidence, `real-${name}-01-confirmed.png`), fullPage:true});
    pass(`${name}: borrower A reserves the window from the search result and sees it confirmed`);

    await b.goto(shared, {waitUntil:'domcontentloaded'});
    await expect(b.getByRole('row').filter({hasText:'Professional tripod'})).toContainText('Not free');
    await b.screenshot({path:path.join(evidence, `real-${name}-02-shared-link-not-free.png`), fullPage:true});
    pass(`${name}: borrower B opens the shared link and sees the tripod taken`);

    await reserve(b, 'Professional tripod', start + HOUR, end + HOUR, `${run} B overlap`);
    await expect(b.getByRole('alert')).toContainText('already reserved for part of that time');
    await expect(b.getByRole('list', {name:'Taken time slots'})).toBeVisible();
    await b.screenshot({path:path.join(evidence, `real-${name}-03-conflict.png`), fullPage:true});
    pass(`${name}: an overlapping request is refused with a conflict message and no booking`);

    await b.getByLabel('Start (SGT)').fill(sgt(end));
    await b.getByLabel('End (SGT)').fill(sgt(end + HOUR));
    await b.getByLabel('Purpose (optional)').fill(`${run} B adjacent`);
    await b.getByRole('button', {name:'Reserve', exact:true}).click();
    await b.waitForURL('**/reservations');
    await expect(b.getByRole('row').filter({hasText:`${run} B adjacent`})).toContainText('Confirmed');
    created.push([b, `${run} B adjacent`, 'Professional tripod']);
    await expect(b.getByRole('row').filter({hasText:`${run} B overlap`})).toHaveCount(0);
    pass(`${name}: the adjacent slot starting when A's ends is accepted (half-open intervals)`);

    await reserve(b, 'Sony mirrorless camera', start, start + 4 * 24 * HOUR, `${run} B too long`);
    await expect(b.getByText('This item can be reserved for at most 3 days.')).toBeVisible();
    await expect(b.locator('#endAt-error')).toBeVisible();
    await expect(b).toHaveURL(/\/reserve/);
    await b.screenshot({path:path.join(evidence, `real-${name}-04-item-limit.png`), fullPage:true});
    pass(`${name}: a four-day request for a three-day item is stopped before it is sent`);

    await b.getByLabel('End (SGT)').fill(sgt(start + 2 * HOUR));
    await b.getByLabel('Purpose (optional)').fill(`${run} B camera`);
    await b.getByRole('button', {name:'Request reservation', exact:true}).click();
    await b.waitForURL('**/reservations');
    await expect(b.getByRole('row').filter({hasText:`${run} B camera`})).toContainText('Waiting for approval');
    created.push([b, `${run} B camera`, 'Sony mirrorless camera']);
    await b.screenshot({path:path.join(evidence, `real-${name}-05-pending-approval.png`), fullPage:true});
    pass(`${name}: approval-required equipment is requested and waits for an approver`);

    while (created.length) {
      const [page, purpose, item] = created.pop();
      await cancel(page, purpose, item);
    }
    await b.goto(shared, {waitUntil:'domcontentloaded'});
    await expect(b.getByRole('row').filter({hasText:'Professional tripod'})).toContainText('Free');
    pass(`${name}: cancelling releases the slots and the shared search shows the tripod free again`);
    await contextA.close(); await contextB.close();
  }
  if (errors.length) throw new Error(`Browser errors: ${errors.join(' | ')}`);
  summary.status = 'passed';
} catch (error) {
  summary.status = 'failed';
  summary.error = String(error?.stack ?? error);
  throw error;
} finally {
  for (const [page, purpose, item] of created) await cancel(page, purpose, item).catch(() => {});
  summary.finishedAt = new Date().toISOString();
  fs.writeFileSync(path.join(runtime, 'uc01-browser-summary.json'), JSON.stringify(summary, null, 2));
  await browser.close();
}
