# GitHub 代理

`gh.re-tikara.fun` 为甘农卡与 GSAULife 提供公开更新查询，并转发 GitHub 下载和引导图片。

- `/api/repos/RE-TikaRa/GSAU-Card/releases/latest` 查询甘农卡最新版。
- `/api/repos/RE-TikaRa/GSAULife/releases/latest` 查询 GSAULife 最新版。
- `/raw/*` 转发 `raw.githubusercontent.com`。
- 其余路径转发 `github.com`，支持下载的 Range 和 HEAD 请求。

公开 Releases 查询使用 Worker Secret 中的 `GITHUB_TOKEN` 认证，成功响应缓存五分钟。

```sh
pnpm install
pnpm test
pnpm deploy
```

部署使用 `cloudflare.config.ts` 中的 Worker 名称和自定义域名，由 `cf` 构建并发布。认证使用本机的 Cloudflare CLI 登录配置。
