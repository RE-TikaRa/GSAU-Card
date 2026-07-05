import { AbilityConstant, UIAbility, Want } from '@kit.AbilityKit';
import { window } from '@kit.ArkUI';

/**
 * 卡片点二维码会带 targetPage='pay' 拉起本 Ability,冷启动在 loadContent 回调里跳付款页,
 * 热启动在 onNewWant 里直接跳。其余情况停在主页。
 */
export default class EntryAbility extends UIAbility {
  private windowStage: window.WindowStage | null = null;
  private targetPage: string = '';

  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    this.targetPage = want.parameters?.targetPage as string ?? '';
  }

  onNewWant(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    this.targetPage = want.parameters?.targetPage as string ?? '';
    this.navigate();
  }

  onDestroy(): void {
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    this.windowStage = windowStage;
    windowStage.loadContent('pages/Index', (err) => {
      if (err.code) {
        return;
      }
      this.navigate();
    });
  }

  private navigate(): void {
    if (this.targetPage !== 'pay' || this.windowStage === null) {
      return;
    }
    this.targetPage = '';
    const router = this.windowStage.getMainWindowSync().getUIContext().getRouter();
    router.pushUrl({ url: 'pages/PayPage' });
  }

  onWindowStageDestroy(): void {
  }

  onForeground(): void {
  }

  onBackground(): void {
  }
}
