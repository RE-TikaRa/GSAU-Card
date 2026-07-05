import { ComponentContent, promptAction, UIContext } from '@kit.ArkUI';

/**
 * 应用内弹窗集合。对齐安卓四种交互:
 * confirm(标题+描述+确认/取消)、input(带单行输入框)、message(长文可滚动+单关闭)、notice(短토스트)。
 * ArkTS 的 @Builder 只能定义在全局或 struct 内,不能是 class 静态方法,
 * 所以布局用文件级 @Builder 函数,AppDialog 只暴露静态调用入口。
 * 自定义弹窗走页面传入的 UIContext 取 promptAction,builder 里的组件创建才有 UI 实例上下文。
 */
class TextHolder {
  text: string = '';
}

interface ConfirmParams {
  title: string;
  message: string;
  confirmText: string;
  cancelText: string;
  onConfirm: () => void;
  onCancel: () => void;
}

interface InputParams {
  title: string;
  hint: string;
  confirmText: string;
  cancelText: string;
  holder: TextHolder;
  onConfirm: () => void;
  onCancel: () => void;
}

interface MessageParams {
  title: string;
  content: string;
  closeText: string;
  onClose: () => void;
}

@Builder
function confirmLayout(p: ConfirmParams) {
  Column() {
    Text(p.title)
      .fontSize(18)
      .fontWeight(FontWeight.Medium)
      .fontColor($r('app.color.text_dark'))
      .width('100%')
    Text(p.message)
      .fontSize(15)
      .fontColor($r('app.color.text_dark'))
      .width('100%')
      .margin({ top: 12 })
    Row() {
      Button(p.cancelText)
        .fontColor($r('app.color.text_hint'))
        .backgroundColor(Color.Transparent)
        .layoutWeight(1)
        .onClick(p.onCancel)
      Button(p.confirmText)
        .fontColor($r('app.color.on_primary'))
        .backgroundColor($r('app.color.primary'))
        .layoutWeight(1)
        .onClick(p.onConfirm)
    }
    .width('100%')
    .margin({ top: 20 })
    .justifyContent(FlexAlign.SpaceBetween)
  }
  .width('86%')
  .padding(20)
  .borderRadius(16)
  .backgroundColor($r('app.color.surface'))
}

@Builder
function inputLayout(p: InputParams) {
  Column() {
    Text(p.title)
      .fontSize(18)
      .fontWeight(FontWeight.Medium)
      .fontColor($r('app.color.text_dark'))
      .width('100%')
    TextInput({ placeholder: p.hint })
      .width('100%')
      .margin({ top: 12 })
      .fontColor($r('app.color.text_dark'))
      .backgroundColor($r('app.color.surface_variant'))
      .onChange((value: string) => {
        p.holder.text = value;
      })
    Row() {
      Button(p.cancelText)
        .fontColor($r('app.color.text_hint'))
        .backgroundColor(Color.Transparent)
        .layoutWeight(1)
        .onClick(p.onCancel)
      Button(p.confirmText)
        .fontColor($r('app.color.on_primary'))
        .backgroundColor($r('app.color.primary'))
        .layoutWeight(1)
        .onClick(p.onConfirm)
    }
    .width('100%')
    .margin({ top: 20 })
    .justifyContent(FlexAlign.SpaceBetween)
  }
  .width('86%')
  .padding(20)
  .borderRadius(16)
  .backgroundColor($r('app.color.surface'))
}

@Builder
function messageLayout(p: MessageParams) {
  Column() {
    Text(p.title)
      .fontSize(18)
      .fontWeight(FontWeight.Medium)
      .fontColor($r('app.color.text_dark'))
      .width('100%')
    Scroll() {
      Text(p.content)
        .fontSize(15)
        .fontColor($r('app.color.text_dark'))
        .width('100%')
    }
    .width('100%')
    .constraintSize({ maxHeight: 320 })
    .margin({ top: 12 })
    Button(p.closeText)
      .fontColor($r('app.color.on_primary'))
      .backgroundColor($r('app.color.primary'))
      .width('100%')
      .margin({ top: 20 })
      .onClick(p.onClose)
  }
  .width('86%')
  .padding(20)
  .borderRadius(16)
  .backgroundColor($r('app.color.surface'))
}

export class AppDialog {
  static notice(message: string): void {
    promptAction.showToast({ message, duration: 2000 });
  }

  static confirm(
    ui: UIContext,
    title: string,
    message: string,
    confirmText: string,
    cancelText: string,
    onConfirm: () => void
  ): void {
    const prompt = ui.getPromptAction();
    const params: ConfirmParams = {
      title,
      message,
      confirmText,
      cancelText,
      onConfirm: () => {
        prompt.closeCustomDialog(content);
        onConfirm();
      },
      onCancel: () => {
        prompt.closeCustomDialog(content);
      }
    };
    const content = new ComponentContent(ui, wrapBuilder(confirmLayout), params);
    prompt.openCustomDialog(content, {
      alignment: DialogAlignment.Center,
      autoCancel: true
    });
  }

  static input(
    ui: UIContext,
    title: string,
    hint: string,
    confirmText: string,
    cancelText: string,
    onConfirm: (text: string) => void
  ): void {
    const prompt = ui.getPromptAction();
    const holder: TextHolder = new TextHolder();
    const params: InputParams = {
      title,
      hint,
      confirmText,
      cancelText,
      holder,
      onConfirm: () => {
        prompt.closeCustomDialog(content);
        onConfirm(holder.text);
      },
      onCancel: () => {
        prompt.closeCustomDialog(content);
      }
    };
    const content = new ComponentContent(ui, wrapBuilder(inputLayout), params);
    prompt.openCustomDialog(content, {
      alignment: DialogAlignment.Center,
      autoCancel: true
    });
  }

  static message(ui: UIContext, title: string, content: string, closeText: string): void {
    const prompt = ui.getPromptAction();
    const params: MessageParams = {
      title,
      content,
      closeText,
      onClose: () => {
        prompt.closeCustomDialog(node);
      }
    };
    const node = new ComponentContent(ui, wrapBuilder(messageLayout), params);
    prompt.openCustomDialog(node, {
      alignment: DialogAlignment.Center,
      autoCancel: true
    });
  }
}
