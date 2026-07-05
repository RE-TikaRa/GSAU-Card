import { common } from '@kit.AbilityKit';
import { router } from '@kit.ArkUI';
import { image } from '@kit.ImageKit';
import { ImageLoader } from '../ui/ImageLoader';
import { TopBar } from '../ui/TopBar';

/**
 * 操作指引:三步图文,截图放在图床上按 URL 拉取。文件名含中文与空格,拼 URL 时逐段编码。
 * 图未加载出来时只显文字步骤,不占位报错。底部"开始使用"关掉引导返回主页。
 */
@Entry
@Component
struct GuidePage {
  @State images: (image.PixelMap | undefined)[] = [undefined, undefined, undefined];
  private context: common.UIAbilityContext = getContext(this) as common.UIAbilityContext;
  private static readonly RAW: string = 'https://gh.re-tikara.fun/raw/RE-TikaRa/ImgHosting/main';

  aboutToAppear(): void {
    this.loadImages();
  }

  private loadImages(): void {
    for (let i = 1; i <= 3; i++) {
      const name = encodeURIComponent(`2026年7月5日 GSAU-Card教程 (${i}).jpg`);
      const url = `${GuidePage.RAW}/${name}`;
      ImageLoader.load(url).then((pm) => {
        this.images[i - 1] = pm;
      });
    }
  }

  @Builder
  step(title: Resource, body: Resource, index: number) {
    Column() {
      Text(title)
        .fontSize(16)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_dark'))
        .width('100%')
      Text(body)
        .fontSize(14)
        .fontColor($r('app.color.text_dark'))
        .width('100%')
        .margin({ top: 8 })
      if (this.images[index] !== undefined) {
        Image(this.images[index])
          .width('100%')
          .objectFit(ImageFit.Contain)
          .margin({ top: 12 })
      }
    }
    .width('100%')
    .padding(16)
    .margin({ bottom: 12 })
    .borderRadius(12)
    .backgroundColor($r('app.color.surface'))
    .alignItems(HorizontalAlign.Start)
  }

  build() {
    Column() {
      TopBar({
        title: this.context.resourceManager.getStringSync($r('app.string.guide_title').id),
        showBack: true,
        onBack: () => {
          router.back();
        }
      })
      Scroll() {
        Column() {
          Text($r('app.string.guide_intro'))
            .fontSize(15)
            .fontColor($r('app.color.text_dark'))
            .width('100%')
            .margin({ bottom: 16 })
          this.step($r('app.string.guide_step1_title'), $r('app.string.guide_step1_body'), 0)
          this.step($r('app.string.guide_step2_title'), $r('app.string.guide_step2_body'), 1)
          this.step($r('app.string.guide_step3_title'), $r('app.string.guide_step3_body'), 2)
          Button($r('app.string.guide_start'))
            .fontColor($r('app.color.on_primary'))
            .backgroundColor($r('app.color.primary'))
            .width('100%')
            .margin({ top: 8, bottom: 16 })
            .onClick(() => {
              router.back();
            })
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
