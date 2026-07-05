import { common } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';
import { router } from '@kit.ArkUI';
import { Account } from '../data/Account';
import { AccountStore } from '../data/AccountStore';
import { LinkParser } from '../data/LinkParser';
import { PayCodeManager } from '../data/PayCodeManager';
import { ResultKind } from '../data/PayCodeRepository';
import { QrGenerator } from '../qr/QrGenerator';
import { TopBar } from '../ui/TopBar';
import { AppDialog } from '../ui/AppDialog';

/**
 * 主页:顶栏(标题+添加+设置)、当前卡片(姓名条+付款码+提示)、卡片列表(点切换/长按删)。
 * 首启一次跳引导页。onPageShow 重渲染并刷新当前卡付款码,刷新回来前先校验没被切走。
 */
@Entry
@Component
struct Index {
  @State accounts: Account[] = [];
  @State currentIndex: number = -1;
  @State statusText: string = '';
  @State qrContent: string = '';
  @State balanceText: string = '';
  private context: common.UIAbilityContext = getContext(this) as common.UIAbilityContext;

  aboutToAppear(): void {
    this.maybeShowGuide();
    this.reload();
  }

  onPageShow(): void {
    this.reload();
    this.refreshCurrent();
  }

  private maybeShowGuide(): void {
    const store = preferences.getPreferencesSync(this.context, { name: 'paycard_guide' });
    const shown = store.getSync('guide_shown', false) as boolean;
    if (!shown) {
      store.putSync('guide_shown', true);
      store.flush();
      router.pushUrl({ url: 'pages/GuidePage' });
    }
  }

  private async reload(): Promise<void> {
    const store = await AccountStore.get(this.context);
    this.accounts = store.list();
    this.currentIndex = store.currentIndex();
    this.render();
  }

  private render(): void {
    const account = this.currentAccount();
    if (account === null) {
      this.statusText = '';
      this.qrContent = '';
      this.balanceText = '';
      return;
    }
    this.balanceText = account.balance.length > 0
      ? this.context.resourceManager.getStringSync($r('app.string.balance_format').id, account.balance)
      : '';
    if (account.cachedCode.length > 0) {
      this.qrContent = account.cachedCode;
      this.statusText = this.context.resourceManager.getStringSync($r('app.string.main_tap_qr').id);
    } else {
      this.qrContent = '';
      this.statusText = this.context.resourceManager.getStringSync($r('app.string.main_loading').id);
    }
  }

  private currentAccount(): Account | null {
    if (this.currentIndex < 0 || this.currentIndex >= this.accounts.length) {
      return null;
    }
    return this.accounts[this.currentIndex];
  }

  private async refreshCurrent(): Promise<void> {
    const account = this.currentAccount();
    if (account === null) {
      return;
    }
    const openid = account.openid;
    const result = await PayCodeManager.refresh(this.context, account);
    const store = await AccountStore.get(this.context);
    const now = store.current();
    if (now === null || now.openid !== openid) {
      return;
    }
    if (result.kind === ResultKind.Ok) {
      await this.reload();
    } else if (result.kind === ResultKind.Invalid) {
      this.statusText = this.context.resourceManager.getStringSync($r('app.string.main_invalid').id);
    } else {
      this.statusText = this.context.resourceManager.getStringSync(
        $r('app.string.main_fetch_failed').id, result.message);
    }
  }

  private switchTo(index: number): void {
    AccountStore.get(this.context).then((store) => {
      store.setCurrentIndex(index);
      this.reload();
      this.refreshCurrent();
    });
  }

  private addFromLink(text: string): void {
    const parsed = LinkParser.parse(text);
    if (parsed === null) {
      AppDialog.notice(this.context.resourceManager.getStringSync($r('app.string.add_no_openid').id));
      return;
    }
    const account = new Account(parsed.openid, parsed.cardId);
    PayCodeManager.refresh(this.context, account).then((result) => {
      if (result.kind === ResultKind.Ok) {
        AccountStore.get(this.context).then((store) => {
          store.add(account);
          const all = store.list();
          const newIndex = all.findIndex((it) =>
            it.openid === account.openid && it.cardId === account.cardId);
          if (newIndex >= 0) {
            store.setCurrentIndex(newIndex);
          }
          this.reload();
          this.refreshCurrent();
          AppDialog.notice(this.context.resourceManager.getStringSync(
            $r('app.string.add_success').id, account.displayName()));
        });
      } else if (result.kind === ResultKind.Invalid) {
        AppDialog.notice(this.context.resourceManager.getStringSync($r('app.string.add_invalid').id));
      } else {
        AppDialog.notice(this.context.resourceManager.getStringSync(
          $r('app.string.add_verify_failed').id, result.message));
      }
    });
  }

  private showAddDialog(): void {
    AppDialog.input(
      this.getUIContext(),
      this.context.resourceManager.getStringSync($r('app.string.add_dialog_title').id),
      this.context.resourceManager.getStringSync($r('app.string.add_dialog_hint').id),
      this.context.resourceManager.getStringSync($r('app.string.add_action').id),
      this.context.resourceManager.getStringSync($r('app.string.cancel').id),
      (text: string) => {
        this.addFromLink(text);
      }
    );
  }

  private confirmRemove(index: number): void {
    const account = this.accounts[index];
    AppDialog.confirm(
      this.getUIContext(),
      this.context.resourceManager.getStringSync($r('app.string.remove_dialog_title').id),
      this.context.resourceManager.getStringSync(
        $r('app.string.remove_dialog_message').id, account.displayName()),
      this.context.resourceManager.getStringSync($r('app.string.remove_action').id),
      this.context.resourceManager.getStringSync($r('app.string.cancel').id),
      () => {
        AccountStore.get(this.context).then((store) => {
          store.removeAt(index);
          this.reload();
          this.refreshCurrent();
        });
      }
    );
  }

  private openPay(): void {
    router.pushUrl({ url: 'pages/PayPage' });
  }

  private openSettings(): void {
    router.pushUrl({ url: 'pages/SettingsPage' });
  }

  @Builder
  topActions() {
    Row() {
      SymbolGlyph($r('sys.symbol.plus'))
        .fontSize(24)
        .fontColor([$r('app.color.text_light')])
        .margin({ right: 16 })
        .onClick(() => {
          this.showAddDialog();
        })
      SymbolGlyph($r('sys.symbol.gearshape'))
        .fontSize(24)
        .fontColor([$r('app.color.text_light')])
        .onClick(() => {
          this.openSettings();
        })
    }
  }

  @Builder
  cardArea() {
    if (this.currentAccount() === null) {
      Column() {
        Text($r('app.string.main_no_card'))
          .fontSize(18)
          .fontColor($r('app.color.text_dark'))
        Text($r('app.string.main_add_hint'))
          .fontSize(14)
          .fontColor($r('app.color.text_hint'))
          .margin({ top: 8 })
      }
      .width('100%')
      .padding(32)
      .justifyContent(FlexAlign.Center)
    } else {
      Column() {
        Text(this.currentAccount()!.displayName())
          .fontSize(18)
          .fontColor($r('app.color.text_light'))
          .width('100%')
          .padding({ top: 12, bottom: 12, left: 16, right: 16 })
          .backgroundColor($r('app.color.name_bar'))
        if (this.balanceText.length > 0) {
          Text(this.balanceText)
            .fontSize(15)
            .fontColor($r('app.color.text_dark'))
            .margin({ top: 12 })
        }
        if (this.qrContent.length > 0) {
          QRCode(this.qrContent)
            .width(QrGenerator.SIZE_CARD)
            .height(QrGenerator.SIZE_CARD)
            .color($r('app.color.qr_foreground'))
            .backgroundColor($r('app.color.qr_background'))
            .margin({ top: 16 })
            .onClick(() => {
              this.openPay();
            })
        }
        Text(this.statusText)
          .fontSize(14)
          .fontColor($r('app.color.text_hint'))
          .margin({ top: 12, bottom: 16 })
      }
      .width('100%')
      .borderRadius(16)
      .clip(true)
      .backgroundColor($r('app.color.surface'))
    }
  }

  build() {
    Column() {
      TopBar({
        title: this.context.resourceManager.getStringSync($r('app.string.app_name').id),
        showBack: false,
        actions: () => {
          this.topActions();
        }
      })
      Scroll() {
        Column() {
          this.cardArea()
          if (this.accounts.length > 0) {
            Text($r('app.string.main_card_list_title'))
              .fontSize(14)
              .fontColor($r('app.color.text_hint'))
              .width('100%')
              .margin({ top: 20, bottom: 8 })
            ForEach(this.accounts, (item: Account, index: number) => {
              Row() {
                Text(item.displayName())
                  .fontSize(16)
                  .fontColor(index === this.currentIndex
                    ? $r('app.color.primary') : $r('app.color.text_dark'))
                  .layoutWeight(1)
                if (index === this.currentIndex) {
                  SymbolGlyph($r('sys.symbol.checkmark'))
                    .fontSize(20)
                    .fontColor([$r('app.color.primary')])
                }
              }
              .width('100%')
              .padding(16)
              .margin({ bottom: 8 })
              .borderRadius(12)
              .backgroundColor($r('app.color.surface'))
              .onClick(() => {
                this.switchTo(index);
              })
              .gesture(
                LongPressGesture().onAction(() => {
                  this.confirmRemove(index);
                })
              )
            }, (item: Account) => item.openid + '_' + item.cardId)
          }
        }
        .width('100%')
        .padding(16)
      }
      .layoutWeight(1)
      .align(Alignment.Top)
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.screen_bg'))
  }
}
