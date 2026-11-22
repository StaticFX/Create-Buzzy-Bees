import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import mermaid from 'astro-mermaid';

export default defineConfig({
  site: 'https://staticfx.github.io',
  base: '/Create-Buzzy-Bees',
  integrations: [
    mermaid({
      autoTheme: true,
      enableLog: false,
      mermaidConfig: {
        fontFamily: '"Monocraft", monospace',
        themeVariables: { fontFamily: '"Monocraft", monospace' },
        flowchart: { wrappingWidth: 220 },
      },
    }),
    starlight({
      title: 'Create Buzzy Beez',
      description: 'Config reference and addon API',
      favicon: '/favicon.svg',
      head: [
        {
          tag: 'link',
          attrs: { rel: 'preload', href: '/Create-Buzzy-Bees/fonts/Monocraft.ttf', as: 'font', type: 'font/ttf', crossorigin: '' },
        },
      ],
      social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/StaticFX/Create-Buzzy-Bees' }],
      editLink: { baseUrl: 'https://github.com/StaticFX/Create-Buzzy-Bees/edit/main/docs/' },
      customCss: ['./src/styles/minecraft.css'],
      sidebar: [
        { label: 'Config', slug: 'config' },
        {
          label: 'API',
          items: [
            { label: 'Adding compat', slug: 'api/compat' },
            { label: 'How Buzzy Beez works', slug: 'api/how-it-works' },
          ],
        },
      ],
    }),
  ],
});
