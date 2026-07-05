import { FormExtensionAbility, formBindingData, formInfo, formProvider } from '@kit.FormKit';
import { Want } from '@kit.AbilityKit';
import { AccountStore } from '../data/AccountStore';
import { PayCodeManager } from '../data/PayCodeManager';

/**
 * 付款码桌面卡片:显示当前账号姓名与二维码。点姓名条切下一账号,点二维码开付款页。
 * 卡片进程与主应用共用同一份 preferences,直接读 AccountStore 取当前账号。
 * 刷新周期由 form_config 的 updateDuration 承担,周期到时 onUpdateForm 抓一次最新付款码。
 */
interface CardData {
  hasCard: boolean;
  name: string;
  qrContent: string;
  hint: string;
}

export default class EntryFormAbility extends FormExtensionAbility {
  onAddForm(want: Want): formBindingData.FormBindingData {
    const formId = want.parameters?.[formInfo.FormParam.IDENTITY_KEY] as string;
    this.pushData(formId, false);
    return formBindingData.createFormBindingData(EntryFormAbility.emptyData());
  }

  onUpdateForm(formId: string): void {
    this.pushData(formId, true);
  }

  onFormEvent(formId: string, message: string): void {
    const params = JSON.parse(message) as Record<string, string>;
    if (params.method === 'switch') {
      this.switchAccount(formId);
    }
  }

  onRemoveForm(formId: string): void {
  }

  private async switchAccount(formId: string): Promise<void> {
    const store = await AccountStore.get(this.context);
    store.switchToNext();
    this.pushData(formId, true);
  }

  private async pushData(formId: string, refresh: boolean): Promise<void> {
    const data = await this.buildData(refresh);
    formProvider.updateForm(formId, formBindingData.createFormBindingData(data));
  }

  private async buildData(refresh: boolean): Promise<CardData> {
    const store = await AccountStore.get(this.context);
    let account = store.current();
    if (account === null) {
      return EntryFormAbility.emptyData();
    }
    if (refresh) {
      await PayCodeManager.refresh(this.context, account);
      account = store.current();
      if (account === null) {
        return EntryFormAbility.emptyData();
      }
    }
    const hint = account.cachedCode.length > 0
      ? ''
      : this.context.resourceManager.getStringSync($r('app.string.widget_tap_refresh').id);
    return {
      hasCard: true,
      name: account.displayName(),
      qrContent: account.cachedCode,
      hint
    };
  }

  private static emptyData(): CardData {
    return { hasCard: false, name: '', qrContent: '', hint: '' };
  }
}
