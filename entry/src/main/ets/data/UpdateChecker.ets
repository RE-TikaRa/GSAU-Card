import { http } from '@kit.NetworkKit';

/**
 * 读 GitHub Releases 最新版,与当前 versionName 比对。
 * 返回下载链接给外部拉起浏览器,不在应用内下载安装。
 */
export enum UpdateKind {
  NewVersion,
  UpToDate,
  Error
}

export interface UpdateResult {
  kind: UpdateKind;
  version: string;
  apkUrl: string;
  pageUrl: string;
  message: string;
}

export class UpdateChecker {
  static async check(currentVersion: string): Promise<UpdateResult> {
    const request = http.createHttp();
    try {
      const resp = await request.request(UpdateChecker.LATEST, {
        method: http.RequestMethod.GET,
        header: { 'Accept': 'application/vnd.github+json' },
        connectTimeout: 10000,
        readTimeout: 10000
      });
      if (resp.responseCode !== 200) {
        return UpdateChecker.error(`HTTP ${resp.responseCode}`);
      }
      const json = typeof resp.result === 'string' ? resp.result : '';
      return UpdateChecker.parse(json, currentVersion);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '网络错误';
      return UpdateChecker.error(msg);
    } finally {
      request.destroy();
    }
  }

  static parse(json: string, currentVersion: string): UpdateResult {
    const tagMatch = UpdateChecker.TAG_RE.exec(json);
    if (tagMatch === null) {
      return UpdateChecker.error('解析失败');
    }
    const latest = tagMatch[1].startsWith('v') ? tagMatch[1].slice(1) : tagMatch[1];
    if (UpdateChecker.compareVersion(latest, currentVersion) <= 0) {
      return { kind: UpdateKind.UpToDate, version: '', apkUrl: '', pageUrl: '', message: '' };
    }
    const apkMatch = UpdateChecker.APK_RE.exec(json);
    if (apkMatch === null) {
      return UpdateChecker.error('未找到安装包');
    }
    const apkUrl = UpdateChecker.proxied(apkMatch[1]);
    const pageMatch = UpdateChecker.PAGE_RE.exec(json);
    const pageUrl = pageMatch !== null ? UpdateChecker.proxied(pageMatch[1]) : '';
    return { kind: UpdateKind.NewVersion, version: latest, apkUrl, pageUrl, message: '' };
  }

  private static error(message: string): UpdateResult {
    return { kind: UpdateKind.Error, version: '', apkUrl: '', pageUrl: '', message };
  }

  /** 把 github.com 原始链接换成走 Cloudflare 的镜像域名,下载与页面都经代理。 */
  private static proxied(u: string): string {
    return u.replace('https://github.com', UpdateChecker.PROXY);
  }

  /** 按点分段逐段比较数字,a>b 返回正数,相等返回 0。 */
  private static compareVersion(a: string, b: string): number {
    const pa = a.split('.');
    const pb = b.split('.');
    const len = Math.max(pa.length, pb.length);
    for (let i = 0; i < len; i++) {
      const na = i < pa.length ? (parseInt(pa[i], 10) || 0) : 0;
      const nb = i < pb.length ? (parseInt(pb[i], 10) || 0) : 0;
      if (na !== nb) {
        return na - nb;
      }
    }
    return 0;
  }

  private static readonly PROXY: string = 'https://gh.re-tikara.fun';
  private static readonly LATEST: string =
    'https://gh.re-tikara.fun/api/repos/RE-TikaRa/GSAU-Card/releases/latest';
  private static readonly TAG_RE: RegExp = new RegExp('"tag_name"\\s*:\\s*"([^"]+)"');
  private static readonly APK_RE: RegExp = new RegExp('"browser_download_url"\\s*:\\s*"([^"]+\\.apk)"');
  private static readonly PAGE_RE: RegExp = new RegExp('"html_url"\\s*:\\s*"([^"]+/releases/tag/[^"]+)"');
}
