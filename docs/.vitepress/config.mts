import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Create Buzzy Beez',
  description: 'Documentation for the Create Buzzy Beez Minecraft mod',
  base: '/Create-Buzzy-Bees/',

  head: [
    ['link', { rel: 'icon', href: '/Create-Buzzy-Bees/favicon.ico' }],
  ],

  themeConfig: {
    nav: [
      { text: 'Guide', link: '/guide/getting-started' },
      { text: 'Features', link: '/features/mechanical-bees' },
      {
        text: 'Download',
        items: [
          { text: 'CurseForge', link: 'https://www.curseforge.com/minecraft/mc-mods/create-buzzy-beez' },
          { text: 'Modrinth', link: 'https://modrinth.com/mod/create-buzzy-beez' },
        ],
      },
    ],

    sidebar: [
      {
        text: 'Guide',
        items: [
          { text: 'Getting Started', link: '/guide/getting-started' },
          { text: 'Bee Network', link: '/guide/bee-network' },
          { text: 'Portable Beehive', link: '/guide/portable-beehive' },
        ],
      },
      {
        text: 'Planners',
        items: [
          { text: 'Construction Planner', link: '/features/construction-planner' },
          { text: 'Deconstruction Planner', link: '/features/deconstruction-planner' },
          { text: 'Pickup Planner', link: '/features/pickup-planner' },
        ],
      },
      {
        text: 'Bees & Upgrades',
        items: [
          { text: 'Mechanical Bees', link: '/features/mechanical-bees' },
          { text: 'Upgrades', link: '/features/upgrades' },
        ],
      },
      {
        text: 'Blocks',
        items: [
          { text: 'Mechanical Beehive', link: '/features/mechanical-beehive' },
          { text: 'Logistics Ports', link: '/features/logistics-ports' },
          { text: 'Schematic Deployer', link: '/features/schematic-deployer' },
        ],
      },
      {
        text: 'Reference',
        items: [
          { text: 'Configuration', link: '/reference/configuration' },
          { text: 'FAQ', link: '/reference/faq' },
        ],
      },
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/StaticFX/Create-Buzzy-Bees' },
    ],

    search: {
      provider: 'local',
    },

    editLink: {
      pattern: 'https://github.com/StaticFX/Create-Buzzy-Bees/edit/main/docs/:path',
      text: 'Edit this page on GitHub',
    },

    footer: {
      message: 'Create Buzzy Beez is not affiliated with Mojang or the Create mod team.',
    },
  },
})
