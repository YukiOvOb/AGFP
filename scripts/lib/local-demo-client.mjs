import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

export const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
export const runtime = path.join(repo, '.cache/local-demo');
const read = name => JSON.parse(fs.readFileSync(path.join(runtime, name), 'utf8').replace(/^\uFEFF/, ''));
export const state = read('state.json');
const access = read('access.json');
assert.equal(path.resolve(state.repo), repo, 'Only this checkout\'s isolated demo may be tested');
export const baseURL = `http://127.0.0.1:${state.webPort}`;
export const demoPassword = access.demoPassword;

export class Client {
  cookies = new Map();
  async request(method, route, body, expected = 200, csrf = true) {
    const headers = { Accept: 'application/json', Cookie: [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ') };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (!['GET', 'HEAD'].includes(method) && csrf && this.cookies.has('XSRF-TOKEN'))
      headers['X-XSRF-TOKEN'] = decodeURIComponent(this.cookies.get('XSRF-TOKEN'));
    const response = await fetch(baseURL + route, {
      method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(10000),
    });
    for (const item of response.headers.getSetCookie()) {
      const pair = item.split(';')[0], split = pair.indexOf('=');
      const name = pair.slice(0, split), value = pair.slice(split + 1);
      if (/max-age=0/i.test(item) || !value) this.cookies.delete(name); else this.cookies.set(name, value);
    }
    const text = await response.text();
    let value;
    try { value = text ? JSON.parse(text) : undefined; } catch { value = text; }
    assert.equal(response.status, expected, `${method} ${route}: ${response.status} ${JSON.stringify(value)}`);
    return value;
  }
  async login(email, role) {
    await this.request('GET', '/api/v1/auth/csrf', undefined, 204);
    const user = await this.request('POST', '/api/v1/auth/login', { email, password: demoPassword });
    assert.ok(user.roles.includes(role));
    await this.request('GET', '/api/v1/auth/csrf', undefined, 204);
    return user;
  }
}
