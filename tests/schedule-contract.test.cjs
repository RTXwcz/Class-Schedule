const test = require('node:test');
const assert = require('node:assert/strict');
const contract = require('../schedule-contract.js');

test('native to web to native retains complete records and metadata', () => {
  const data = contract.decode({schemaVersion:1,datasetId:'semester',updatedAt:'2026-09-07T00:00:00Z',source:'NATIVE',
    custom:{a:1},courses:[{id:'c',name:'数学',weekday:1,startPeriod:1,endPeriod:2,weekRule:'ODD',teacher:'张老师',
      weeks:[1,3,5],building:'A楼',room:'301',courseNote:'教材',locationNote:'东门'}],
    exams:[{id:'e',subject:'数学',date:'2026-12-20',time:'09:00',building:'B',room:'201'}],
    overrides:[{date:'2026-09-12',replacementWeekday:1,note:'补课'}]});
  const web = contract.toWeb(data);
  const roundtrip = contract.encode(data,web.courses,web.exams);
  assert.deepEqual(roundtrip.courses,data.courses);
  assert.deepEqual(roundtrip.exams,data.exams);
  assert.deepEqual(roundtrip.overrides,data.overrides);
  assert.equal(roundtrip.datasetId,'semester');
  assert.deepEqual(roundtrip.custom,{a:1});
});
test('legacy course and exam arrays remain importable', () => {
  const data = contract.decode([{id:'c',name:'Math',day:2,start:3,end:4,week:'even',location:'A 301'},
    {id:'e',subject:'Math',date:'2026-09-08'}]);
  assert.equal(data.courses[0].weekRule,'EVEN');
  assert.equal(data.courses[0].locationNote,'A 301');
  assert.equal(data.exams.length,1);
});
test('invalid collections and records reject the whole import', () => {
  for(const input of [{courses:{}},{exams:[{id:'e',subject:'Math',date:'2026-02-31'}]},
    {courses:[{id:'c',name:'Math',day:8,start:1,end:2}]},{overrides:[{date:'2026-09-08',replacementWeekday:0}]}]) {
    assert.throws(()=>contract.decode(input));
  }
});
test('editing a legacy location clears obsolete structured location', () => {
  const data = contract.decode({courses:[{id:'c',name:'Math',day:1,start:1,end:2,building:'A',room:'301'}]});
  const web = contract.toWeb(data); web.courses[0].location = 'B 402';
  const result=contract.encode(data,web.courses,[]).courses[0];
  assert.equal(result.building,null); assert.equal(result.room,null); assert.equal(result.locationNote,'B 402');
});

test('automatic date view intersects explicit weeks, parity and full-day replacement', () => {
  const courses = [
    {id:'odd',day:1,week:'odd',weeks:[1,3]},
    {id:'even',day:1,week:'even',weeks:[2]},
    {id:'saturday',day:6,week:'all'},
  ];
  const overrides = [{date:'2026-09-12',replacementWeekday:1}];
  assert.deepEqual(contract.forDate(courses,overrides,'2026-09-07','2026-09-12').map(c=>c.id),['odd']);
  assert.deepEqual(contract.forDate(courses,[],'2026-09-07','2026-09-14').map(c=>c.id),['even']);
  assert.deepEqual(contract.forDate(courses,[],null,'2026-09-07'),[]);
  assert.deepEqual(contract.forDate(courses,[],'2026-09-07','2026-10-05'),[]);
  assert.throws(()=>contract.decode({courses:[],semesterStartDate:'2026-02-31'}));
});
