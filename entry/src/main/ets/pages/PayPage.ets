import { common } from '@kit.AbilityKit';
import { router, window } from '@kit.ArkUI';
import { AccountStore } from '../data/AccountStore';
import { PayCodeManager } from '../data/PayCodeManager';
import { ResultKind } from '../data/PayCodeRepository';
import { QrGenerator } from '../qr/QrGenerator';
import { TopBar } from '../ui/TopBar';

/**
 * 全屏付款页:进页把屏幕调到最亮并常亮,先显缓存码再起 60 秒循环刷新。
 * 点二维码或刷新按钮手动刷。离开页面时熄掉常亮、还原亮度、停循环。
 */
@Entry
@Component
struct PayPage {
  @State qrContent: string = '';
  @State nameText: string = '';
  @State statusText: string = '';
  @State hasCard: boolean = true;
  private context: common.UIAbilityContext = getContext(this) as common.UIAbilityContext;
  private looping: boolean = false;
  private win: window.Window | null = null;

  aboutToAppear(): void {
    this.showCached();
    this.enterBrightMode();
  }

  aboutToDisappear(): void {
    this.looping = false;
    this.exitBrightMode();
  }

  onPageShow(): void {
    this.startLoop();
  }

  onPageHide(): void {
    this.looping = false;
  }

  private async enterBrightMode(): Promise<void> {
    this.win = await window.getLastWindow(this.context);
    this.win.setWindowBrightness(1.0);
    this.win.setWindowKeepScreenOn(true);
  }

  private exitBrightMode(): void {
    if (this.win !== null) {
      this.win.setWindowBrightness(-1.0);
      this.win.setWindowKeepScreenOn(false);
      this.win = null;
    }
  }

  private async showCached(): Promise<void> {
    const store = await AccountStore.get(this.context);
    const account = store.current();
    if (account === null) {
      this.hasCard = false;
      return;
    }
    this.hasCard = true;
    this.nameText = account.displayName();
    if (account.cachedCode.length > 0) {
      this.qrContent = account.cachedCode;
    }
    this.statusText = this.context.resourceManager.getStringSync($r('app.string.pay_auto_refresh').id);
  }

  private async startLoop(): Promise<void> {
    if (this.looping) {
      return;
    }
    this.looping = true;
    while (this.looping) {
      await this.refresh();
      await PayPage.delay(60000);
    }
  }

  private static delay(ms: number): Promise<void> {
    return new Promise((resolve) => {
      setTimeout(resolve, ms);
    });
  }

  private async refresh(): Promise<void> {
    const store = await AccountStore.get(this.context);
    const account = store.current();
    if (account === null) {
      this.hasCard = false;
      return;
    }
    this.statusText = this.context.resourceManager.getStringSync($r('app.string.pay_refreshing').id);
    const openid = account.openid;
    const result = await PayCodeManager.refresh(this.context, account);
    const now = store.current();
    if (now === null || now.openid !== openid) {
      return;
    }
    if (result.kind === ResultKind.Ok) {
      this.qrContent = result.code;
      this.nameText = now.displayName();
      this.statusText = this.context.resourceManager.getStringSync($r('app.string.pay_auto_refresh').id);
    } else if (result.kind === ResultKind.Invalid) {
      this.statusText = this.context.resourceManager.getStringSync($r('app.string.pay_invalid').id);
    } else {
      this.statusText = this.context.resourceManager.getStringSync(
        $r('app.string.pay_refresh_failed').id, result.message);
    }
  }

  private goBack(): void {
    router.back();
  }

  build() {
    Column() {
      TopBar({
        title: this.context.resourceManager.getStringSync($r('app.string.pay_title').id),
        showBack: true,
        onBack: () => {
          this.goBack();
        }
      })
      if (!this.hasCard) {
        Column() {
          Text($r('app.string.pay_no_card'))
            .fontSize(18)
            .fontColor($r('app.color.text_dark'))
          Text($r('app.string.pay_no_card_hint'))
            .fontSize(14)
            .fontColor($r('app.color.text_hint'))
            .margin({ top: 8 })
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else {
        Column() {
          Text(this.nameText)
            .fontSize(20)
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_dark'))
          if (this.qrContent.length > 0) {
            QRCode(this.qrContent)
              .width(QrGenerator.SIZE_FULLSCREEN)
              .height(QrGenerator.SIZE_FULLSCREEN)
              .color($r('app.color.qr_foreground'))
              .backgroundColor($r('app.color.qr_background'))
              .margin({ top: 24 })
              .onClick(() => {
                this.refresh();
              })
          }
          Text(this.statusText)
            .fontSize(14)
            .fontColor($r('app.color.text_hint'))
            .margin({ top: 20 })
          Button($r('app.string.pay_refresh_button'))
            .fontColor($r('app.color.on_primary'))
            .backgroundColor($r('app.color.primary'))
            .margin({ top: 24 })
            .onClick(() => {
              this.refresh();
            })
        }
        .width('100%')
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.screen_bg'))
  }
}
