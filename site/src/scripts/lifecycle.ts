type Setup = (stage: HTMLElement, signal: AbortSignal) => void;
const screens: {name: string; setup: Setup}[] = [];
let lifetime = new AbortController();

export function onScreen(name: string, setup: Setup) {
  screens.push({name, setup});
  const stage = document.querySelector<HTMLElement>('.tv');
  if (stage && (name === '*' || stage.dataset.screen === name)) setup(stage, lifetime.signal);
}

export function mountScreen(stage: HTMLElement) {
  lifetime.abort();
  lifetime = new AbortController();
  for (const {name, setup} of screens) {
    if (name === '*' || stage.dataset.screen === name) setup(stage, lifetime.signal);
  }
}

export function navigate(url: string) {
  const event = new CustomEvent('miqat:navigate', {detail: url, cancelable: true});
  if (document.dispatchEvent(event)) location.assign(url);
}
