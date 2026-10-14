import http from 'k6/http';
import { check } from 'k6';

// Supply 50 distinct borrower/equipment fixtures through a local JSON file. Never commit credentials.
const base = __ENV.SERMS_K6_BASE_URL || 'http://127.0.0.1:8789';
const fixtures = JSON.parse(open(__ENV.SERMS_K6_USERS_FILE || './k6-users.local.json'));
if (!Array.isArray(fixtures) || fixtures.length < 50) throw new Error('50 borrower fixtures are required');
export const options = {
  scenarios: { reservation: { executor: 'per-vu-iterations', vus: 50, iterations: 1, maxDuration: '3m' } },
  thresholds: {
    http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<2000'], checks: ['rate>0.99'],
    'http_req_duration{operation:login}': ['p(95)<2000'],
    'http_req_duration{operation:search}': ['p(95)<2000'],
    'http_req_duration{operation:reserve}': ['p(95)<2000'],
  },
};

function csrfHeader(jar) {
  const cookies = jar.cookiesForURL(base);
  const token = cookies['XSRF-TOKEN']?.[0];
  return token ? { 'X-XSRF-TOKEN': decodeURIComponent(token) } : {};
}
export default function () {
  const fixture = fixtures[__VU - 1];
  const jar = http.cookieJar();
  const common = { headers: { Accept: 'application/json' }, timeout: '15s', tags: { operation: 'login' } };
  const csrf = http.get(`${base}/api/v1/auth/csrf`, common);
  check(csrf, { 'CSRF bootstrap': (r) => r.status === 204 });
  const login = http.post(`${base}/api/v1/auth/login`, JSON.stringify({ email: fixture.email, password: fixture.password }),
    { headers: { ...common.headers, ...csrfHeader(jar), 'Content-Type': 'application/json' }, timeout: '15s', tags: { operation: 'login' } });
  check(login, { 'login succeeds': (r) => r.status === 200 });
  if (login.status !== 200) return;
  http.get(`${base}/api/v1/auth/csrf`, common);
  const startAt = fixture.startAt;
  const endAt = fixture.endAt;
  const query = `q=${encodeURIComponent(fixture.assetTag)}&from=${encodeURIComponent(startAt)}&to=${encodeURIComponent(endAt)}&availableOnly=true&size=20`;
  const search = http.get(`${base}/api/v1/equipment?${query}`,
    { headers: common.headers, timeout: '15s', tags: { operation: 'search' } });
  check(search, { 'search succeeds': (r) => r.status === 200 });
  if (search.status !== 200) return;
  const equipment = search.json('content') || [];
  if (!equipment.some((item) => item.equipmentId === fixture.equipmentId)) {
    check(null, { 'fixture equipment available': () => false });
    return;
  }
  const reserve = http.post(`${base}/api/v1/reservations`, JSON.stringify({
    equipmentId: fixture.equipmentId, startAt, endAt, purpose: 'SERMS 50-user load rehearsal',
  }), { headers: { ...common.headers, ...csrfHeader(jar), 'Content-Type': 'application/json' },
    timeout: '15s', tags: { operation: 'reserve' } });
  check(reserve, { 'reservation created': (r) => r.status === 201 });
}
