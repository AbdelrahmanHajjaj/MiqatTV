// Pages are pre-rendered for Cairo (the app's default city) so search engines see real times;
// in the browser everything is recomputed for the visitor's own city and clock.
process.env.TZ = 'Africa/Cairo';

import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';

export default defineConfig({
  site: 'https://abdelrahmanhajjaj.github.io',
  base: '/MiqatTV',
  trailingSlash: 'always',
  vite: {
    optimizeDeps: { include: ['@miqat/core'] },
    build: { commonjsOptions: { include: [/node_modules/, /shared[\\/]core/] } },
    ssr: { external: ['@miqat/core'] },
  },
  integrations: [sitemap({ i18n: { defaultLocale: 'ar', locales: { ar: 'ar', en: 'en' } } })],
});
