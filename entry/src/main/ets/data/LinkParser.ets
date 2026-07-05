import { url } from '@kit.ArkTS';

/**
 * 从用户粘贴的付款码链接里解析 openid 和 id。
 * 形如 https://yktapp.gsau.edu.cn/virtualcard/openVirtualcard?openid=XXXX&displayflag=1&id=9
 */
export interface Parsed {
  openid: string;
  cardId: string;
}

export class LinkParser {
  static parse(text: string): Parsed | null {
    const trimmed = text.trim();
    // 先按标准 URL 解析
    try {
      const parsed = url.URL.parseURL(trimmed);
      const openid = parsed.params.get('openid');
      if (openid !== null && openid.length > 0) {
        const id = parsed.params.get('id');
        return { openid, cardId: id !== null && id.length > 0 ? id : '9' };
      }
    } catch (e) {
      // 退回正则
    }
    // 退回正则:有些粘贴内容可能带多余文字
    const openidMatch = new RegExp('openid=([0-9A-Fa-f]+)').exec(trimmed);
    if (openidMatch !== null && openidMatch[1].length > 0) {
      const idMatch = new RegExp('[?&]id=(\\d+)').exec(trimmed);
      const id = idMatch !== null ? idMatch[1] : '9';
      return { openid: openidMatch[1], cardId: id };
    }
    return null;
  }
}
