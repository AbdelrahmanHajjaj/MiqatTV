import assert from 'node:assert/strict';
import {previewPath} from '../src/lib/preview-navigation';
import {onScreen, mountScreen} from '../src/scripts/lifecycle';

const current = 'https://example.com/MiqatTV/en/';
const paths = new Set(['/MiqatTV/en/', '/MiqatTV/en/apps/', '/MiqatTV/en/settings/']);
assert.equal(previewPath('settings/#tv', current, paths), '/MiqatTV/en/settings/');
assert.equal(previewPath('/MiqatTV/en/apps/#tv', current, paths), '/MiqatTV/en/apps/');
for (const href of ['https://other.example/MiqatTV/en/apps/', '/MiqatTV/settings/', '/other/en/apps/', '/MiqatTV/en/unknown/', '?download=1', 'javascript:alert(1)', 'https://[']) {
  assert.equal(previewPath(href, current, paths), null, href);
}

const descriptor = Object.getOwnPropertyDescriptor(globalThis, 'document');
const home = {dataset: {screen: 'home'}} as unknown as HTMLElement;
const settings = {dataset: {screen: 'settings'}} as unknown as HTMLElement;
Object.defineProperty(globalThis, 'document', {configurable: true, value: {querySelector: () => home}});
try {
  const events: string[] = [];
  const setup = (name: string) => (_: HTMLElement, signal: AbortSignal) => {
    events.push('mount:' + name);
    signal.addEventListener('abort', () => events.push('stop:' + name), {once: true});
  };
  onScreen('home', setup('home')); onScreen('settings', setup('settings')); onScreen('*', setup('remote'));
  assert.deepEqual(events, ['mount:home', 'mount:remote']);
  mountScreen(settings);
  assert.deepEqual(events.slice(2), ['stop:home', 'stop:remote', 'mount:settings', 'mount:remote']);
  mountScreen(home);
  assert.deepEqual(events.slice(6), ['stop:settings', 'stop:remote', 'mount:home', 'mount:remote']);
} finally {
  if (descriptor) Object.defineProperty(globalThis, 'document', descriptor);
  else Reflect.deleteProperty(globalThis, 'document');
}
console.log('In-page preview: known same-language routes only; previous screen work is aborted on every replacement.');
