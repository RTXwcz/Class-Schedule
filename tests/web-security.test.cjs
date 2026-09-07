const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const contract = require('../schedule-contract.js');

// Execute the shipped calendar renderer against imported data. The sink is captured
// before a browser parses it, so the regression never executes attacker JavaScript.
function renderCalendar(file, input) {
  const source = fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
  const escapeFunction = source.match(/^function esc\(s\).*$/m)?.[0];
  const start = source.indexOf('function renderCalendar(){');
  const end = source.indexOf('function calPrev()', start);
  assert.ok(escapeFunction && start >= 0 && end > start, 'production renderer must exist');
  const nodes = {calendar: {}, calTitle: {}, examCount: {}};
  const context = vm.createContext({
    exams: contract.toWeb(contract.decode(input)).exams,
    calYear: 2026,
    calMonth: 8,
    $: id => nodes[id],
    todayDateStr: () => '2026-09-08',
    resolveTheme: () => 'light',
    fmtDate: (year, month, day) => `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`,
    courseColor: () => ({bg: '#fff', fg: '#111', bd: '#555'}),
  });
  vm.runInContext(`${escapeFunction}\n${source.slice(start, end)}\nrenderCalendar();`, context);
  return nodes.calendar.innerHTML;
}

for (const file of ['课表.html', 'apk/www/index.html']) {
  test(`${file}: imported exam and event IDs cannot break out of HTML attributes`, () => {
    const payload = 'id"><img src=x onerror="globalThis.__schedule_xss=1"><div data-id="';
    const html = renderCalendar(file, {exams: [
      {id: payload, subject: '数学', date: '2026-09-08', type: 'EXAM'},
      {id: `${payload}event`, subject: '社团', date: '2026-09-08', type: 'EVENT'},
    ]});
    assert.doesNotMatch(html, /<img\b/i);
    assert.match(html, /data-id="id&quot;&gt;&lt;img src=x onerror=&quot;globalThis\.__schedule_xss=1/);
    assert.equal((html.match(/class="exam-chip"/g) || []).length, 2);
  });

  test(`${file}: imported subject and location remain plain text`, () => {
    const html = renderCalendar(file, {exams: [{
      id: 'safe-id', subject: '<svg onload="globalThis.__schedule_xss=1">',
      date: '2026-09-08', building: '<img src=x onerror="globalThis.__schedule_xss=1">',
    }]});
    assert.doesNotMatch(html, /<(?:svg|img)\b/i);
    assert.match(html, /data-id="safe-id"/);
    assert.match(html, /&lt;svg onload=&quot;/);
    assert.match(html, /&lt;img src=x onerror=&quot;/);
    assert.doesNotMatch(html, /class="meta">(?:undefined|null)/);
  });
}
