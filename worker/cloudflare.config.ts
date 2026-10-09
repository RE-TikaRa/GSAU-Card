import { defineConfig } from 'cf/config'
import * as entrypoint from './gh-proxy.js' with { type: 'cf-worker' }

export default defineConfig({
  worker: {
    name: 'gh-proxy',
    entrypoint,
    compatibilityDate: '2026-07-05',
    workersDev: false,
    previewUrls: false,
    domains: ['gh.re-tikara.fun'],
  },
})
