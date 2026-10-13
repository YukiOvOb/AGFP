import assert from 'node:assert/strict';
const base='http://127.0.0.1:8789';
const get=route=>fetch(base+route,{signal:AbortSignal.timeout(10000)});
const health=await get('/api/v1/health');
assert.equal(health.status,200);assert.equal((await health.json()).status,'UP');
for(const route of ['/','/equipment','/reservations','/maintenance','/loans','/notifications']){
 const response=await get(route);assert.equal(response.status,200);
 const html=await response.text();assert.match(html,/<div id="root"><\/div>/);
 const asset=html.match(/src="(\/assets\/[^"]+\.js)"/)[1];
 assert.equal((await get(asset)).status,200);
}
const anonymous=await get('/api/v1/auth/me');
assert.equal(anonymous.status,401);
assert.ok((await anonymous.json()).type.endsWith('/problems/unauthenticated'));
const csrf=await get('/api/v1/auth/csrf');assert.equal(csrf.status,204);
assert.ok(csrf.headers.getSetCookie().some(c=>c.startsWith('XSRF-TOKEN=')&&/; Secure/i.test(c)));
console.log('Production Compose smoke passed: real database health, six React routes/assets, API proxy, anonymous denial and Secure CSRF cookie');
