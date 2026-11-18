import '../screens/client/home';
import '../screens/client/apps';
import '../screens/client/prayer-day';
import '../screens/client/settings';
import '../screens/client/call';
import '../screens/client/screensaver';
import {mountScreen} from './lifecycle';
import {previewPath} from '../lib/preview-navigation';

const host = document.querySelector<HTMLElement>('[data-preview-host]');
if (host) {
  const templates = new Map([...document.querySelectorAll<HTMLTemplateElement>('template[data-preview-path]')].map(t => [t.dataset.previewPath!, t]));
  const paths = new Set(templates.keys());
  const initial = host.dataset.previewPath!;
  let active = initial;
  const focus = new Map<string, number>();
  const controls = (stage: HTMLElement) => [...stage.querySelectorAll<HTMLElement>('.ft, [data-focus-start]')];
  document.addEventListener('focusin', event => {
    const stage = host.querySelector<HTMLElement>('.tv');
    if (stage?.contains(event.target as Node)) focus.set(active, controls(stage).indexOf(event.target as HTMLElement));
  });
  const select = (path: string, restore = false, remember = true) => {
    const template = templates.get(path);
    if (!template) return false;
    if (path === active) return true;
    const content = template.content.cloneNode(true) as DocumentFragment;
    const frame = content.querySelector<HTMLElement>('.tv-frame')!;
    host.replaceChildren(frame);
    host.dataset.previewPath = active = path;
    const stage = frame.querySelector<HTMLElement>('.tv')!;
    mountScreen(stage);
    const copy = document.querySelector('[data-preview-copy]');
    if (copy) copy.replaceChildren(...content.querySelector('[data-preview-copy]')!.childNodes);
    document.querySelectorAll<HTMLAnchorElement>('[data-preview-nav]').forEach(link => {
      if (new URL(link.href).pathname === path) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });
    document.querySelector<HTMLElement>('[data-preview-title]')!.textContent = template.dataset.previewName!;
    document.querySelector<HTMLAnchorElement>('[data-preview-language]')!.href = template.dataset.otherPath!;
    if (remember) history.pushState({...history.state, miqatScreen: path}, '', location.href);
    const target = restore ? controls(stage)[focus.get(path) ?? -1] : null;
    (target?.getClientRects().length ? target : stage.querySelector<HTMLElement>('[data-focus-start]'))?.focus({preventScroll: true});
    frame.scrollIntoView({block: 'nearest', inline: 'nearest'});
    return true;
  };
  history.replaceState({...history.state, miqatScreen: initial}, '', location.href);
  window.addEventListener('popstate', event => select(event.state?.miqatScreen ?? initial, true, false));
  document.addEventListener('click', event => {
    if (event.defaultPrevented || event.button !== 0 || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    const link = (event.target as Element).closest<HTMLAnchorElement>('a[href]');
    if (!link || link.target || link.hasAttribute('download') || !(host.contains(link) || link.hasAttribute('data-preview-nav'))) return;
    const path = previewPath(link.href, location.href, paths);
    if (path && select(path, link.matches('[data-home], [data-back]'))) event.preventDefault();
  });
  document.addEventListener('miqat:navigate', event => {
    const path = previewPath((event as CustomEvent<string>).detail, location.href, paths);
    if (path && select(path)) event.preventDefault();
  });
}
