(function (root) {
  'use strict';
  const assert = (value, message) => { if (!value) throw new Error(message); };
  const object = value => value && typeof value === 'object' && !Array.isArray(value);
  const integer = (value, min, max) => Number.isInteger(value) && value >= min && value <= max;
  const date = value => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
  const week = value => ({ all: 'ALL', odd: 'ODD', even: 'EVEN', ALL: 'ALL', ODD: 'ODD', EVEN: 'EVEN' })[value || 'all'];
  function course(value) {
    assert(object(value), '课程必须为对象');
    const c = { ...value, weekday: value.weekday ?? value.day, startPeriod: value.startPeriod ?? value.start,
      endPeriod: value.endPeriod ?? value.end, weekRule: week(value.weekRule ?? value.week), weeks: value.weeks ?? [] };
    assert(typeof c.id === 'string' && c.id.trim() && typeof c.name === 'string' && c.name.trim(), '课程缺少 ID 或名称');
    assert(integer(c.weekday, 1, 7) && integer(c.startPeriod, 1, 12) && integer(c.endPeriod, c.startPeriod, 12), '课程星期或节次无效');
    assert(c.weekRule && Array.isArray(c.weeks) && c.weeks.every(w => integer(w, 1, 60)) && new Set(c.weeks).size === c.weeks.length, '课程周次无效');
    if (!c.building && !c.room && c.location && !c.locationNote) c.locationNote = c.location;
    ['day', 'start', 'end', 'week', 'location'].forEach(key => delete c[key]);
    return c;
  }
  function exam(value) {
    assert(object(value) && typeof value.id === 'string' && value.id.trim() && typeof value.subject === 'string' && value.subject.trim(), '考试缺少 ID 或科目');
    assert(date(value.date) && (!value.time || /^([01]\d|2[0-3]):[0-5]\d$/.test(value.time)), '考试日期或时间无效');
    const e = { ...value };
    if (!e.building && !e.room && e.location && !e.locationNote) e.locationNote = e.location;
    delete e.location;
    return e;
  }
  function decode(input) {
    const parsed = typeof input === 'string' ? JSON.parse(input) : input;
    const doc = Array.isArray(parsed) ? { courses: parsed.filter(c => !c.subject), exams: parsed.filter(c => c.subject) } : parsed;
    assert(object(doc) && ['courses', 'exams', 'overrides'].some(key => key in doc), 'JSON 中缺少课表数据');
    ['courses', 'exams', 'overrides'].forEach(key => assert(!(key in doc) || Array.isArray(doc[key]), key + ' 必须是数组'));
    const result = { ...doc, schemaVersion: doc.schemaVersion ?? 1, datasetId: doc.datasetId || root.crypto.randomUUID(),
      updatedAt: doc.updatedAt || new Date().toISOString(), source: doc.source || 'WEB',
      courses: (doc.courses || []).map(course), exams: (doc.exams || []).map(exam),
      overrides: (doc.overrides || []).map(o => {
        assert(object(o) && date(o.date) && integer(o.replacementWeekday, 1, 7), '调休规则无效'); return { ...o };
      }) };
    assert(integer(result.schemaVersion, 1, 2147483647), '数据版本无效');
    assert(doc.semesterStartDate == null || date(doc.semesterStartDate), '学期开始日期无效');
    for (const [collection, key] of [['courses', 'id'], ['exams', 'id'], ['overrides', 'date']]) {
      assert(new Set(result[collection].map(x => x[key])).size === result[collection].length, '存在重复记录');
    }
    return result;
  }
  const location = item => [item.building, item.room, item.locationNote].filter(Boolean).join(' ');
  function toWeb(doc) {
    return { courses: doc.courses.map(c => ({ ...c, day: c.weekday, start: c.startPeriod, end: c.endPeriod,
      week: c.weekRule.toLowerCase(), location: location(c) })), exams: doc.exams.map(e => ({ ...e, location: location(e) })) };
  }
  function encode(metadata, courses, exams) {
    return decode({ ...metadata, source: 'WEB', updatedAt: new Date().toISOString(),
      courses: courses.map(c => course({ ...c, ...(c.location !== location(c) ? {building:null,room:null,locationNote:c.location} : {}),
        weekday: c.day, startPeriod: c.start, endPeriod: c.end, weekRule: week(c.week) })),
      exams: exams.map(e => ({...e, ...(e.location !== location(e) ? {building:null,room:null,locationNote:e.location} : {})})) });
  }
  function forDate(courses, overrides, semesterStart, targetDate) {
    assert(date(targetDate) && (!semesterStart || date(semesterStart)), '课表日期无效');
    const replacement = (overrides || []).find(o => o.date === targetDate);
    const weekday = replacement?.replacementWeekday ?? (new Date(targetDate).getUTCDay() || 7);
    const days = semesterStart ? (Date.parse(targetDate) - Date.parse(semesterStart)) / 86400000 : -1;
    const number = days >= 0 ? Math.floor(days / 7) + 1 : null;
    return courses.filter(c => {
      const parity = week(c.week ?? c.weekRule);
      return (c.day ?? c.weekday) === weekday &&
        (!(c.weeks || []).length || (number !== null && c.weeks.includes(number))) &&
        (parity === 'ALL' || (number !== null && (number % 2 === 1 ? 'ODD' : 'EVEN') === parity));
    });
  }
  const api = { decode, toWeb, encode, forDate };
  if (typeof module !== 'undefined') module.exports = api;
  root.ScheduleContract = api;
})(typeof globalThis !== 'undefined' ? globalThis : window);
