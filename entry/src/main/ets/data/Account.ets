/**
 * 一张校园卡账号。openid 是身份凭证,id 指向具体卡片。
 * name/cardNo/balance 由抓取结果回填,cachedCode 是最近一次成功取到的付款码内容。
 */
interface AccountJson {
  openid: string;
  cardId: string;
  name: string;
  cardNo: string;
  balance: string;
  cachedCode: string;
  cachedAt: number;
}

export class Account {
  openid: string;
  cardId: string;
  name: string = '';
  cardNo: string = '';
  balance: string = '';
  cachedCode: string = '';
  cachedAt: number = 0;

  constructor(openid: string, cardId: string) {
    this.openid = openid;
    this.cardId = cardId;
  }

  toJson(): string {
    const json: AccountJson = {
      openid: this.openid,
      cardId: this.cardId,
      name: this.name,
      cardNo: this.cardNo,
      balance: this.balance,
      cachedCode: this.cachedCode,
      cachedAt: this.cachedAt
    };
    return JSON.stringify(json);
  }

  /** 展示用标题:优先姓名,没有则用卡号,再没有用 openid 尾段 */
  displayName(): string {
    if (this.name.length > 0) {
      return this.name;
    }
    if (this.cardNo.length > 0) {
      return this.cardNo;
    }
    return '卡' + this.openid.slice(-4);
  }

  static fromJson(o: Record<string, string | number>): Account {
    const account = new Account(String(o.openid), o.cardId !== undefined ? String(o.cardId) : '9');
    account.name = o.name !== undefined ? String(o.name) : '';
    account.cardNo = o.cardNo !== undefined ? String(o.cardNo) : '';
    account.balance = o.balance !== undefined ? String(o.balance) : '';
    account.cachedCode = o.cachedCode !== undefined ? String(o.cachedCode) : '';
    account.cachedAt = o.cachedAt !== undefined ? Number(o.cachedAt) : 0;
    return account;
  }
}
