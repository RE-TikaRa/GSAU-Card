/**
 * 顶栏:左侧返回按钮 + 居中标题,右侧留一个可选动作插槽。
 * 主页不需要返回,设置/关于/引导页带返回。动作区用 @BuilderParam 由页面填。
 */
@Component
export struct TopBar {
  title: string = '';
  showBack: boolean = true;
  onBack?: () => void;
  @BuilderParam actions?: () => void;

  build() {
    Row() {
      if (this.showBack) {
        SymbolGlyph($r('sys.symbol.chevron_left'))
          .fontSize(24)
          .fontColor([$r('app.color.text_light')])
          .margin({ right: 8 })
          .onClick(() => {
            this.onBack?.();
          })
      }
      Text(this.title)
        .fontSize(20)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_light'))
        .layoutWeight(1)
      if (this.actions !== undefined) {
        this.actions()
      }
    }
    .width('100%')
    .height(56)
    .padding({ left: 16, right: 16 })
    .backgroundColor($r('app.color.primary'))
  }
}
