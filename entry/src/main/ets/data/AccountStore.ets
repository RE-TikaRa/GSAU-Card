import { preferences } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';
import { Account } from './Account';

/**
 * 多账号存储。用 Preferences 存账号列表和当前选中索引。
 * app 与服务卡片共享同一份数据,切换用户即改动 currentIndex。
 */
export class AccountStore {
  private store: preferences.Preferences;

  private constructor(store: preferences.Preferences) {
    this.store = store;
  }

  private static instance: AccountStore | null = null;

  static async get(context: common.Context): Promise<AccountStore> {
    if (AccountStore.instance !== null) {
      return AccountStore.instance;
    }
    const store = preferences.getPreferencesSync(context, { name: AccountStore.PREFS });
    AccountStore.instance = new AccountStore(store);
    return AccountStore.instance;
  }

  list(): Account[] {
    const raw = this.store.getSync(AccountStore.KEY_ACCOUNTS, '') as string;
    if (raw.length === 0) {
      return [];
    }
    const arr = JSON.parse(raw) as Record<string, string | number>[];
    return arr.map((o) => Account.fromJson(o));
  }

  save(accounts: Account[]): void {
    const arr = accounts.map((a) => JSON.parse(a.toJson()) as Record<string, string | number>);
    this.store.putSync(AccountStore.KEY_ACCOUNTS, JSON.stringify(arr));
    this.store.flush();
  }

  currentIndex(): number {
    const size = this.list().length;
    if (size === 0) {
      return -1;
    }
    const i = this.store.getSync(AccountStore.KEY_CURRENT, 0) as number;
    if (i < 0) {
      return 0;
    }
    if (i > size - 1) {
      return size - 1;
    }
    return i;
  }

  setCurrentIndex(i: number): void {
    this.store.putSync(AccountStore.KEY_CURRENT, i);
    this.store.flush();
  }

  current(): Account | null {
    const all = this.list();
    const i = this.currentIndex();
    if (i < 0 || i >= all.length) {
      return null;
    }
    return all[i];
  }

  /** 切到下一个账号,返回切换后的账号。卡片点姓名条时用。 */
  switchToNext(): Account | null {
    const all = this.list();
    if (all.length === 0) {
      return null;
    }
    const next = (this.currentIndex() + 1) % all.length;
    this.setCurrentIndex(next);
    return all[next];
  }

  add(account: Account): void {
    const all = this.list();
    const existing = all.findIndex((it) => it.openid === account.openid && it.cardId === account.cardId);
    if (existing >= 0) {
      all[existing] = account;
    } else {
      all.push(account);
    }
    this.save(all);
  }

  removeAt(i: number): void {
    const all = this.list();
    if (i < 0 || i >= all.length) {
      return;
    }
    all.splice(i, 1);
    this.save(all);
    const cur = this.store.getSync(AccountStore.KEY_CURRENT, 0) as number;
    if (cur >= all.length) {
      this.setCurrentIndex(all.length - 1 > 0 ? all.length - 1 : 0);
    }
  }

  /** 回填抓取结果并持久化 */
  update(account: Account): void {
    const all = this.list();
    const i = all.findIndex((it) => it.openid === account.openid && it.cardId === account.cardId);
    if (i >= 0) {
      all[i] = account;
      this.save(all);
    }
  }

  private static readonly PREFS: string = 'paycard_store';
  private static readonly KEY_ACCOUNTS: string = 'accounts';
  private static readonly KEY_CURRENT: string = 'current_index';
}
