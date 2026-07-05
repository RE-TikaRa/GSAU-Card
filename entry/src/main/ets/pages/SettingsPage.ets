import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import { ThemeManager, ThemeMode } from '../ui/ThemeManager';
import { TopBar } from '../ui/TopBar';

/**
 * 设置页:外观主题三档单选(跟随系统/浅色/深色)+ 关于入口。
 * 鸿蒙无安卓那套保活/电池/自启动,保活由服务卡片刷新周期承担,这里不再出现。
 */
@Entry
@Component
struct SettingsPage {
  @State mode: ThemeMode = ThemeMode.System;
  private context: common.UIAbilityContext = getContext(this) as common.UIAbilityContext;

  aboutToAppear(): void {
    this.mode = ThemeManager.getMode(this.context);
  }

  private select(mode: ThemeMode): void {
    this.mode = mode;
    ThemeManager.setMode(this.context, mode);
  }

  @Builder
  themeRow(label: Resource, target: ThemeMode) {
    Row() {
      Text(label)
        .fontSize(16)
        .fontColor($r('app.color.text_dark'))
        .layoutWeight(1)
      if (this.mode === target) {
        SymbolGlyph($r('sys.symbol.checkmark'))
          .fontSize(20)
          .fontColor([$r('app.color.primary')])
      }
    }
    .width('100%')
    .padding(16)
    .onClick(() => {
      this.select(target);
    })
  }

  build() {
    Column() {
      TopBar({
        title: this.context.resourceManager.getStringSync($r('app.string.settings_title').id),
        showBack: true,
        onBack: () => {
          router.back();
        }
      })
      Column() {
        Text($r('app.string.settings_appearance_title'))
          .fontSize(14)
          .fontColor($r('app.color.text_hint'))
          .width('100%')
          .margin({ top: 20, bottom: 8, left: 16 })
        Column() {
          this.themeRow($r('app.string.settings_theme_system'), ThemeMode.System)
          this.themeRow($r('app.string.settings_theme_light'), ThemeMode.Light)
          this.themeRow($r('app.string.settings_theme_dark'), ThemeMode.Dark)
        }
        .width('100%')
        .borderRadius(12)
        .backgroundColor($r('app.color.surface'))

        Row() {
          Text($r('app.string.settings_about_title'))
            .fontSize(16)
            .fontColor($r('app.color.text_dark'))
            .layoutWeight(1)
          SymbolGlyph($r('sys.symbol.chevron_right'))
            .fontSize(18)
            .fontColor([$r('app.color.text_hint')])
        }
        .width('100%')
        .padding(16)
        .margin({ top: 20 })
        .borderRadius(12)
        .backgroundColor($r('app.color.surface'))
        .onClick(() => {
          router.pushUrl({ url: 'pages/AboutPage' });
        })
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.screen_bg'))
  }
}
