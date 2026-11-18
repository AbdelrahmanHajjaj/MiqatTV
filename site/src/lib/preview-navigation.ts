/** Intercept only known screens in this language and project, leaving other links alone. */
export function previewPath(href: string, current: string, paths: ReadonlySet<string>): string | null {
  try {
    const url = new URL(href, current);
    return url.origin === new URL(current).origin && !url.search && paths.has(url.pathname) ? url.pathname : null;
  } catch { return null; }
}
