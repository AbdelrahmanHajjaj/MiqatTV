import assert from 'node:assert/strict';
import {pairingDemo,pairingStep,pairingHtml} from '../src/screens/pairing';
import {makeText} from '../src/lib/text';
import {makeCtx} from '../src/lib/ctx';
import {DEFAULT_PREFS} from '../src/lib/data';
import {DEVICE,rowsFor,rowHtml} from '../src/screens/settings';
for(const lang of ['ar','en'] as const){
  const t=makeText(lang),ctx=makeCtx(lang,DEFAULT_PREFS,new Date('2026-11-12T12:00:00Z'));
  const row=rowsFor(ctx,DEVICE).find(row=>row.id==='device.companion')!;assert.ok(row);assert.match(rowHtml(t,row,0),/data-ui="device.companion" data-ui-kind="action"/);
  let state=pairingDemo();const bindings=new Set<string>();
  const collect=()=>{const html=pairingHtml(t,state);for(const match of html.matchAll(/data-ui="([^"]+)"/g))bindings.add(match[1]);return html;};
  collect();assert.equal(pairingStep(state,'complete'),state,'completion cannot bypass approval');
  state=pairingStep(state,'open');assert.match(collect(),/width="420" height="420"/);
  state=pairingStep(state,'request');collect();assert.equal(state.phase,'pending');
  state=pairingStep(state,'reject');assert.equal(state.phase,'idle');assert.equal(state.phones.length,0);
  state=pairingStep(pairingStep(pairingStep(state,'open'),'request'),'approve');collect();assert.equal(state.phones.length,0);
  state=pairingStep(state,'complete');collect();assert.deepEqual(state.phones,[1]);
  state=pairingStep(state,'revoke',1);assert.equal(state.phones.length,0);
  state=pairingStep(pairingStep(state,'open'),'cancel');assert.equal(state.phase,'idle');
  assert.deepEqual([...bindings].sort(),['pairing.open','pairing.cancel','pairing.approve','pairing.reject','pairing.revoke'].sort());
}
console.log('PASS: bilingual inert preview pairing controls, explicit approval/completion, cancellation and revocation; device entry uses the canonical binding.');
