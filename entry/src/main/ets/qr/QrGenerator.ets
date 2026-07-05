/**
 * 二维码尺寸常量。鸿蒙的二维码走 ArkUI 内置 QRCode 组件声明式渲染,
 * 页面直接 QRCode(content).width(size).height(size),无需逐像素生成 Bitmap。
 */
export class QrGenerator {
  static readonly SIZE_WIDGET: number = 200;
  static readonly SIZE_CARD: number = 300;
  static readonly SIZE_FULLSCREEN: number = 350;
}
