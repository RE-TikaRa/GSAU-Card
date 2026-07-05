import { common } from '@kit.AbilityKit';
import { Account } from './Account';
import { AccountStore } from './AccountStore';
import { PayCodeRepository, FetchResult, ResultKind } from './PayCodeRepository';

/**
 * 刷新协调器:抓当前账号(或指定账号)的付款码,成功后回填缓存并持久化。
 */
export class PayCodeManager {
  private static repo: PayCodeRepository = new PayCodeRepository();

  static async refresh(context: common.Context, account: Account): Promise<FetchResult> {
    const result = await PayCodeManager.repo.fetch(account.openid, account.cardId);
    if (result.kind === ResultKind.Ok) {
      const store = await AccountStore.get(context);
      account.cachedCode = result.code;
      account.cachedAt = Date.now();
      if (result.name.length > 0) {
        account.name = result.name;
      }
      if (result.cardNo.length > 0) {
        account.cardNo = result.cardNo;
      }
      if (result.balance.length > 0) {
        account.balance = result.balance;
      }
      store.update(account);
    }
    return result;
  }

  static async refreshCurrent(context: common.Context): Promise<FetchResult> {
    const store = await AccountStore.get(context);
    const account = store.current();
    if (account === null) {
      return { kind: ResultKind.Error, code: '', name: '', cardNo: '', balance: '', message: '无账号' };
    }
    return PayCodeManager.refresh(context, account);
  }
}
