import { common, Want, bundleManager } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import { pasteboard } from '@kit.BasicServicesKit';
import { UpdateChecker, UpdateKind } from '../data/UpdateChecker';
import { TopBar } from '../ui/TopBar';
import { AppDialog } from '../ui/AppDialog';

/**
 * 关于页:版本 + 检查更新、软件简介、联系方式(邮箱/GitHub/Bilibili/微信复制)、
 * 许可证/隐私/协议长文弹窗。外链走 startAbility + browsable Want,微信号复制到剪贴板。
 */
@Entry
@Component
struct AboutPage {
  @State versionText: string = '';
  @State checking: boolean = false;
  private context: common.UIAbilityContext = getContext(this) as common.UIAbilityContext;

  aboutToAppear(): void {
    this.loadVersion();
  }

  private loadVersion(): void {
    try {
      const info = bundleManager.getBundleInfoForSelfSync(
        bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION);
      const name = info.versionName;
      this.versionText = this.context.resourceManager.getStringSync(
        $r('app.string.about_version').id, name);
    } catch (e) {
      this.versionText = '';
    }
  }

  private openUri(uri: string): void {
    const want: Want = {
      action: 'ohos.want.action.viewData',
      entities: ['entity.system.browsable'],
      uri
    };
    this.context.startAbility(want);
  }

  private copyWechat(): void {
    const value = this.context.resourceManager.getStringSync($r('app.string.about_contact_wechat_value').id);
    const data = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, value);
    pasteboard.getSystemPasteboard().setData(data).then(() => {
      AppDialog.notice(this.context.resourceManager.getStringSync($r('app.string.about_wechat_copied').id));
    });
  }

  private checkUpdate(): void {
    if (this.checking) {
      return;
    }
    this.checking = true;
    AppDialog.notice(this.context.resourceManager.getStringSync($r('app.string.about_checking').id));
    const current = this.versionText;
    const info = bundleManager.getBundleInfoForSelfSync(
      bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION);
    UpdateChecker.check(info.versionName).then((result) => {
      this.checking = false;
      if (result.kind === UpdateKind.NewVersion) {
        AppDialog.confirm(
          this.getUIContext(),
          this.context.resourceManager.getStringSync($r('app.string.about_new_version_title').id, result.version),
          this.context.resourceManager.getStringSync($r('app.string.about_new_version_message').id),
          this.context.resourceManager.getStringSync($r('app.string.about_go_download').id),
          this.context.resourceManager.getStringSync($r('app.string.cancel').id),
          () => {
            this.openUri(result.pageUrl.length > 0 ? result.pageUrl : result.apkUrl);
          }
        );
      } else if (result.kind === UpdateKind.UpToDate) {
        AppDialog.notice(this.context.resourceManager.getStringSync($r('app.string.about_up_to_date').id));
      } else {
        AppDialog.notice(this.context.resourceManager.getStringSync(
          $r('app.string.about_check_failed').id, result.message));
      }
    });
  }

  private showDoc(title: Resource, content: Resource): void {
    AppDialog.message(
      this.getUIContext(),
      this.context.resourceManager.getStringSync(title.id),
      this.context.resourceManager.getStringSync(content.id),
      this.context.resourceManager.getStringSync($r('app.string.about_dialog_close').id)
    );
  }

  @Builder
  linkRow(label: Resource, value: Resource, onTap: () => void) {
    Row() {
      Text(label)
        .fontSize(16)
        .fontColor($r('app.color.text_dark'))
      Blank()
      Text(value)
        .fontSize(14)
        .fontColor($r('app.color.text_hint'))
    }
    .width('100%')
    .padding(16)
    .onClick(onTap)
  }

  @Builder
  docRow(label: Resource, onTap: () => void) {
    Row() {
      Text(label)
        .fontSize(16)
        .fontColor($r('app.color.text_dark'))
        .layoutWeight(1)
      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(18)
        .fontColor([$r('app.color.text_hint')])
    }
    .width('100%')
    .padding(16)
    .onClick(onTap)
  }

  @Builder
  section(title: Resource) {
    Text(title)
      .fontSize(14)
      .fontColor($r('app.color.text_hint'))
      .width('100%')
      .margin({ top: 20, bottom: 8, left: 16 })
  }

  build() {
    Column() {
      TopBar({
        title: this.context.resourceManager.getStringSync($r('app.string.about_title').id),
        showBack: true,
        onBack: () => {
          router.back();
        }
      })
      Scroll() {
        Column() {
          Column() {
            Text($r('app.string.app_name'))
              .fontSize(22)
              .fontWeight(FontWeight.Medium)
              .fontColor($r('app.color.text_dark'))
            Text(this.versionText)
              .fontSize(14)
              .fontColor($r('app.color.text_hint'))
              .margin({ top: 6 })
            Button($r('app.string.about_check_update'))
              .fontColor($r('app.color.on_primary'))
              .backgroundColor($r('app.color.primary'))
              .enabled(!this.checking)
              .margin({ top: 16 })
              .onClick(() => {
                this.checkUpdate();
              })
          }
          .width('100%')
          .padding(24)
          .borderRadius(16)
          .backgroundColor($r('app.color.surface'))

          this.section($r('app.string.about_intro_title'))
          Column() {
            Text($r('app.string.about_intro'))
              .fontSize(15)
              .fontColor($r('app.color.text_dark'))
              .width('100%')
          }
          .width('100%')
          .padding(16)
          .borderRadius(12)
          .backgroundColor($r('app.color.surface'))

          this.section($r('app.string.about_contact_title'))
          Column() {
            this.linkRow($r('app.string.about_contact_email'), $r('app.string.about_contact_email_value'), () => {
              this.openUri('mailto:' +
                this.context.resourceManager.getStringSync($r('app.string.about_contact_email_value').id));
            })
            this.linkRow($r('app.string.about_contact_github'), $r('app.string.about_contact_github_value'), () => {
              this.openUri('https://github.com/RE-TikaRa');
            })
            this.linkRow($r('app.string.about_contact_bilibili'), $r('app.string.about_contact_bilibili_value'), () => {
              this.openUri('https://m.bilibili.com/space/374412219');
            })
            this.linkRow($r('app.string.about_contact_wechat'), $r('app.string.about_contact_wechat_value'), () => {
              this.copyWechat();
            })
          }
          .width('100%')
          .borderRadius(12)
          .backgroundColor($r('app.color.surface'))

          this.section($r('app.string.about_other_title'))
          Column() {
            this.docRow($r('app.string.about_license'), () => {
              this.showDoc($r('app.string.about_license_title'), $r('app.string.about_license_content'));
            })
            this.docRow($r('app.string.about_privacy'), () => {
              this.showDoc($r('app.string.about_privacy_title'), $r('app.string.about_privacy_content'));
            })
            this.docRow($r('app.string.about_agreement'), () => {
              this.showDoc($r('app.string.about_agreement_title'), $r('app.string.about_agreement_content'));
            })
          }
          .width('100%')
          .borderRadius(12)
          .backgroundColor($r('app.color.surface'))
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
