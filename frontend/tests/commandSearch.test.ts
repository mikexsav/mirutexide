import test from 'node:test';
import assert from 'node:assert/strict';
import { filterCommands } from '../src/commandSearch.ts';
const items=[{id:'a',title:'Main.total',detail:'Метод L30',keywords:'total(List)'},{id:'b',title:'Анализировать программу',detail:'Сложность и проблемы'},{id:'c',title:'Экспорт проекта',detail:'Java или ZIP'}];
test('search is case insensitive and accepts a signature',()=>{assert.deepEqual(filterCommands(items,'TOTAL list').map(x=>x.id),['a']);});
test('multiple words must all match',()=>{assert.deepEqual(filterCommands(items,'анализ проблемы').map(x=>x.id),['b']);assert.equal(filterCommands(items,'total ZIP').length,0);});
test('empty search retains order and limits large graphs',()=>{assert.deepEqual(filterCommands(items,'  '),items);assert.equal(filterCommands(Array.from({length:1000},(_,i)=>({...items[0],id:String(i)})),'').length,80);});
test('search does not alter command descriptors',()=>{const frozen=Object.freeze(items.map(i=>Object.freeze({...i})));assert.equal(filterCommands([...frozen],'java')[0].id,'c');});
