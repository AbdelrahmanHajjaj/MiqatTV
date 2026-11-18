import portable from '@miqat/core';
import qrcode from 'qrcode-generator';
import type { Text } from '../lib/text';
import config from '../../../companion/protocol/lan.json';

/** Browser demonstration only: no endpoint, pairing credential, persistence or network access. */
export type PairingDemo = { phase: 'idle' | 'scan' | 'pending' | 'approved'; phones: number[]; next: number };
export type PairingAction = 'open' | 'request' | 'approve' | 'reject' | 'cancel' | 'complete' | 'revoke';
export const pairingDemo = (): PairingDemo => ({phase:'idle',phones:[],next:1});
export function pairingStep(state: PairingDemo, action: PairingAction, phone?: number): PairingDemo {
  if(action==='open'&&state.phase==='idle'&&state.phones.length<config.maxPhones)return {...state,phase:'scan'};
  if(action==='request'&&state.phase==='scan')return {...state,phase:'pending'};
  if(action==='approve'&&state.phase==='pending')return {...state,phase:'approved'};
  if(action==='complete'&&state.phase==='approved')return {phase:'idle',phones:[...state.phones,state.next],next:state.next+1};
  if(action==='cancel'||action==='reject'&&state.phase==='pending')return {...state,phase:'idle'};
  if(action==='revoke'&&state.phase==='idle')return {...state,phones:state.phones.filter(id=>id!==phone)};
  return state;
}
const escape=(value:string)=>value.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;');
export function pairingHtml(t: Text,state: PairingDemo): string {
  const button=(action:PairingAction,id?:string,value?:number)=>{
    const control=id?portable.tv.miqat.core.UiContract.bind(id,'action'):null;
    const label=control?t.s(control.labelKey):action==='request'?(t.ar?'محاكاة طلب هاتف':'Simulate phone request'):(t.ar?'إكمال الإقران التجريبي':'Complete demo pairing');
    return `<button type="button" class="ft row" data-pair-action="${action}"${value===undefined?'':` data-pair-phone="${value}"`}${control?` data-ui="${control.id}" data-ui-kind="action"`:''}${action==='open'&&state.phones.length>=config.maxPhones?' disabled':''}>${escape(label)}</button>`;
  };
  let html='';
  if(state.phase!=='idle') {
    const qr=qrcode(0,'M');qr.addData('miqat-tv-demo:v1');qr.make();
    html+=`<div class="pairing-demo-qr" role="img" aria-label="${t.ar?'رمز تجريبي':'Demo QR'}">${qr.createSvgTag({cellSize:8,margin:32,scalable:true}).replace('<svg ','<svg aria-hidden="true" width="420" height="420" ')}</div>`;
  }
  html+='<div class="pairing-demo-controls">';
  if(state.phase==='idle') {
    html+=button('open','pairing.open');
    if(!state.phones.length)html+=`<p>${escape(t.s('pairingEmpty'))}</p>`;
    for(const phone of state.phones)html+=`<p>${t.ar?'هاتف تجريبي':'Demo phone'} ${t.d(phone)}</p>`+button('revoke','pairing.revoke',phone);
  } else {
    if(state.phase==='scan')html+=`<p>${escape(t.s('pairingScan'))}</p>`+button('request');
    else {
      html+=`<strong dir="ltr" class="pairing-demo-code">123456</strong><p>${escape(t.s(state.phase==='approved'?'pairingWaiting':'pairingConfirm'))}</p>`;
      if(state.phase==='pending')html+=button('reject','pairing.reject')+button('approve','pairing.approve');
      else html+=button('complete');
    }
    html+=button('cancel','pairing.cancel');
  }
  return html+'</div>';
}
