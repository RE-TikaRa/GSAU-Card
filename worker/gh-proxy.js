/**
 * Cloudflare Worker:GitHub 反向代理,给 App 检查更新与下载提速。
 * 部署在 gh-proxy.lgq3218483753.workers.dev:
 *   /api/*  转发到 api.github.com(读 Releases 接口)
 *   /raw/*  转发到 raw.githubusercontent.com(引导页截图)
 *   其余     转发到 github.com(下载链接,自动跟随 302 到对象存储)
 * App 端 UpdateChecker 的 PROXY 常量与此域名保持一致。
 */
export default {
  async fetch(request, env) {
    const url = new URL(request.url)
    let target
    const isApi = url.pathname.startsWith('/api/')
    const isRaw = url.pathname.startsWith('/raw/')
    if (isApi) {
      target = 'https://api.github.com' + url.pathname.slice(4) + url.search
    } else if (isRaw) {
      target = 'https://raw.githubusercontent.com' + url.pathname.slice(4) + url.search
    } else {
      target = 'https://github.com' + url.pathname + url.search
    }
    const headers = {
      'User-Agent': 'GSAU-Card-Updater',
      'Accept': request.headers.get('Accept') || '*/*',
    }
    if (isApi && env.GITHUB_TOKEN) {
      headers['Authorization'] = 'Bearer ' + env.GITHUB_TOKEN
    }
    const resp = await fetch(target, {
      headers,
      redirect: 'follow',
    })
    const respHeaders = new Headers(resp.headers)
    respHeaders.set('Access-Control-Allow-Origin', '*')
    return new Response(resp.body, { status: resp.status, headers: respHeaders })
  },
}
