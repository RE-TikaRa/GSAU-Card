import assert from 'node:assert/strict'
import test from 'node:test'
import worker from './gh-proxy.js'

globalThis.caches = { default: { match: async () => undefined, put: async () => {} } }

test('公开更新接口使用 Worker Secret 认证', async (t) => {
  t.mock.property(globalThis, 'caches', { default: { match: async () => undefined, put: async () => {} } })
  t.mock.method(globalThis, 'fetch', async (target, options) => {
    assert.equal(target, 'https://api.github.com/repos/RE-TikaRa/GSAU-Card/releases/latest')
    assert.equal(options.headers.Accept, 'application/vnd.github+json')
    if (options.headers.Authorization !== 'Bearer valid-token') {
      return Response.json({ message: 'Bad credentials' }, { status: 401 })
    }
    return Response.json({ tag_name: 'v2.2' })
  })
  const response = await worker.fetch(
    new Request('https://gh.re-tikara.fun/api/repos/RE-TikaRa/GSAU-Card/releases/latest', {
      headers: { Accept: 'application/vnd.github+json' },
    }),
    { GITHUB_TOKEN: 'valid-token' },
    { waitUntil: () => {} },
  )

  assert.equal(response.status, 200)
  assert.equal((await response.json()).tag_name, 'v2.2')
})

test('公开 API 缓存成功响应并保留错误状态', async (t) => {
  const cache = { match: async () => undefined, put: async () => {} }
  t.mock.property(globalThis, 'caches', { default: cache })
  const put = t.mock.method(cache, 'put')
  t.mock.method(globalThis, 'fetch', async () => Response.json({ message: 'rate limited' }, { status: 403 }))
  const response = await worker.fetch(new Request('https://gh.re-tikara.fun/api/repos/RE-TikaRa/GSAU-Card/releases/latest'), {}, { waitUntil: () => {} })

  assert.equal(response.status, 403)
  assert.equal(response.headers.get('Access-Control-Allow-Origin'), '*')
  assert.equal((await response.json()).message, 'rate limited')
  assert.equal(put.mock.callCount(), 0)
  assert.equal(response.headers.get('Cache-Control'), null)

  t.mock.method(globalThis, 'fetch', async () => Response.json({ tag_name: 'v2.2' }))
  const success = await worker.fetch(new Request('https://gh.re-tikara.fun/api/repos/RE-TikaRa/GSAU-Card/releases/latest'), {}, { waitUntil: () => {} })
  assert.equal(put.mock.callCount(), 1)
  assert.equal(success.headers.get('Cache-Control'), 'public, max-age=0, s-maxage=300')
})

test('图片与下载请求沿用原有转发规则', async (t) => {
  const targets = []
  t.mock.method(globalThis, 'fetch', async (target, options) => {
    targets.push(target)
    assert.equal(options.redirect, 'follow')
    assert.equal(options.cf, undefined)
    assert.equal(options.headers.Authorization, undefined)
    return new Response('content', { headers: { 'Content-Type': 'application/octet-stream' } })
  })
  const image = await worker.fetch(new Request('https://gh.re-tikara.fun/raw/RE-TikaRa/ImgHosting/main/guide.jpg'))
  const download = await worker.fetch(new Request('https://gh.re-tikara.fun/RE-TikaRa/GSAU-Card/releases/download/v2.2/GSAU-Card-v2.2.apk'))

  assert.deepEqual(targets, [
    'https://raw.githubusercontent.com/RE-TikaRa/ImgHosting/main/guide.jpg',
    'https://github.com/RE-TikaRa/GSAU-Card/releases/download/v2.2/GSAU-Card-v2.2.apk',
  ])
  assert.equal(await image.text(), 'content')
  assert.equal(await download.text(), 'content')
})

test('保留共享更新接口与 API 路径限制', async (t) => {
  t.mock.property(globalThis, 'caches', { default: { match: async () => undefined, put: async () => {} } })
  const fetch = t.mock.method(globalThis, 'fetch', async (target) => {
    assert.equal(target, 'https://api.github.com/repos/RE-TikaRa/GSAULife/releases/latest')
    return Response.json({ tag_name: 'v1.0' })
  })
  const shared = await worker.fetch(new Request('https://gh.re-tikara.fun/api/repos/RE-TikaRa/GSAULife/releases/latest'), {}, { waitUntil: () => {} })
  assert.equal(shared.status, 200)
  const other = await worker.fetch(new Request('https://gh.re-tikara.fun/api/user'))
  const post = await worker.fetch(new Request('https://gh.re-tikara.fun/api/repos/RE-TikaRa/GSAU-Card/releases/latest', { method: 'POST' }))
  assert.equal(other.status, 404)
  assert.equal(post.status, 404)
  assert.equal(fetch.mock.callCount(), 1)
})

test('下载转发支持 Range 和 HEAD', async (t) => {
  t.mock.method(globalThis, 'fetch', async (_, options) => {
    assert.equal(options.method, 'HEAD')
    assert.equal(options.headers.Range, 'bytes=0-1023')
    return new Response(null, { status: 206 })
  })
  const response = await worker.fetch(new Request('https://gh.re-tikara.fun/RE-TikaRa/GSAU-Card/releases/download/v2.2/GSAU-Card-v2.2.apk', {
    method: 'HEAD', headers: { Range: 'bytes=0-1023' },
  }))
  assert.equal(response.status, 206)
})
