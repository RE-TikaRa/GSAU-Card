import { http } from '@kit.NetworkKit';

/**
 * 抓取付款码页面并解析出付款码内容与账户信息。
 * 页面把二维码内容直接放在 id="code" 的隐藏字段,姓名/卡号/余额在 p.bdb 文本里。
 */
export enum ResultKind {
  Ok,
  Invalid,
  Error
}

export interface FetchResult {
  kind: ResultKind;
  code: string;
  name: string;
  cardNo: string;
  balance: string;
  message: string;
}

export class PayCodeRepository {
  async fetch(openid: string, cardId: string): Promise<FetchResult> {
    const url = `${PayCodeRepository.BASE}?openid=${openid}&displayflag=1&id=${cardId}`;
    const request = http.createHttp();
    try {
      const resp = await request.request(url, {
        method: http.RequestMethod.GET,
        header: { 'User-Agent': PayCodeRepository.UA },
        connectTimeout: 10000,
        readTimeout: 10000
      });
      if (resp.responseCode !== 200) {
        return PayCodeRepository.error(`HTTP ${resp.responseCode}`);
      }
      const html = typeof resp.result === 'string' ? resp.result : '';
      return PayCodeRepository.parse(html);
    } catch (e) {
      const msg = e instanceof Error ? e.message : '网络错误';
      return PayCodeRepository.error(msg);
    } finally {
      request.destroy();
    }
  }

  static parse(html: string): FetchResult {
    const codeMatch = PayCodeRepository.CODE_RE.exec(html);
    const code = codeMatch !== null ? codeMatch[1] : '';
    if (code.length === 0) {
      return PayCodeRepository.invalid();
    }

    // p.bdb 文本形如: 郎振杰：1073325020407 余额：16.43元
    let name = '';
    let cardNo = '';
    let balance = '';
    const bdbMatch = PayCodeRepository.BDB_RE.exec(html);
    if (bdbMatch !== null) {
      const line = bdbMatch[1];
      const nameCard = PayCodeRepository.NAME_CARD_RE.exec(line);
      if (nameCard !== null) {
        name = nameCard[1].trim();
        cardNo = nameCard[2].trim();
      }
      const bal = PayCodeRepository.BAL_RE.exec(line);
      if (bal !== null) {
        balance = bal[1].trim();
      }
    }
    return { kind: ResultKind.Ok, code, name, cardNo, balance, message: '' };
  }

  private static invalid(): FetchResult {
    return { kind: ResultKind.Invalid, code: '', name: '', cardNo: '', balance: '', message: '' };
  }

  private static error(message: string): FetchResult {
    return { kind: ResultKind.Error, code: '', name: '', cardNo: '', balance: '', message };
  }

  private static readonly BASE: string = 'https://yktapp.gsau.edu.cn/virtualcard/openVirtualcard';
  private static readonly UA: string =
    'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) ' +
    'Version/4.0 Chrome/108.0.0.0 Mobile Safari/537.36 MicroMessenger/8.0.30';

  private static readonly CODE_RE: RegExp = new RegExp('id="code"\\s+value="([0-9A-Fa-f]+)"');
  private static readonly BDB_RE: RegExp = new RegExp('<p class="bdb">([^<]*)</p>');
  private static readonly NAME_CARD_RE: RegExp = new RegExp('(.+?)[：:]\\s*(\\d+)');
  private static readonly BAL_RE: RegExp = new RegExp('余额[：:]\\s*([0-9.]+元?)');
}
