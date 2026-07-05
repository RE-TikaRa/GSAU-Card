/**
 * 付款码卡片界面。数据由 EntryFormAbility 推入 LocalStorage。
 * 无账号:整卡点击开应用,提示添加校园卡。
 * 有账号:上方姓名条点击切下一账号(postCardAction message),下方二维码点击开付款页(postCardAction router)。
 * cachedCode 为空时二维码位置显"点击刷新付款码"文案。
 */
let storage = new LocalStorage();

@Entry(storage)
@Component
struct WidgetCard {
  @LocalStorageProp('hasCard') hasCard: boolean = false;
  @LocalStorageProp('name') name: string = '';
  @LocalStorageProp('qrContent') qrContent: string = '';
  @LocalStorageProp('hint') hint: string = '';

  build() {
    Column() {
      if (!this.hasCard) {
        Column() {
          Text($r('app.string.widget_no_account'))
            .fontSize(15)
            .fontColor($r('app.color.text_dark'))
          Text($r('app.string.widget_add_card'))
            .fontSize(13)
            .fontColor($r('app.color.text_hint'))
            .margin({ top: 6 })
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Center)
        .onClick(() => {
          postCardAction(this, {
            action: 'router',
            abilityName: 'EntryAbility',
            params: { targetPage: 'home' }
          });
        })
      } else {
        Text(this.name)
          .fontSize(15)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.on_primary'))
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .width('100%')
          .textAlign(TextAlign.Center)
          .padding({ top: 10, bottom: 10, left: 16, right: 16 })
          .borderRadius(10)
          .backgroundColor($r('app.color.primary'))
          .onClick(() => {
            postCardAction(this, {
              action: 'message',
              params: { method: 'switch' }
            });
          })
        Column() {
          if (this.qrContent.length > 0) {
            QRCode(this.qrContent)
              .width('100%')
              .height('100%')
              .color($r('app.color.qr_foreground'))
              .backgroundColor($r('app.color.qr_background'))
          } else {
            Text($r('app.string.widget_tap_refresh'))
              .fontSize(13)
              .fontColor($r('app.color.text_hint'))
          }
        }
        .width('100%')
        .layoutWeight(1)
        .margin({ top: 10 })
        .padding(10)
        .borderRadius(12)
        .backgroundColor($r('app.color.qr_background'))
        .justifyContent(FlexAlign.Center)
        .onClick(() => {
          postCardAction(this, {
            action: 'router',
            abilityName: 'EntryAbility',
            params: { targetPage: 'pay' }
          });
        })
      }
    }
    .width('100%')
    .height('100%')
    .padding(10)
    .backgroundColor($r('app.color.surface'))
  }
}
