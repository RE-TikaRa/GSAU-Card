import { http } from '@kit.NetworkKit';
import { image } from '@kit.ImageKit';

/**
 * 远程图片加载器。引导页的教程截图放在图床上,按 URL 拉字节转 PixelMap。
 * 进程内用 Map 缓存已解码的 PixelMap,重复打开引导页不再走网络。
 */
export class ImageLoader {
  private static cache: Map<string, image.PixelMap> = new Map();

  static async load(url: string): Promise<image.PixelMap | undefined> {
    const cached = ImageLoader.cache.get(url);
    if (cached !== undefined) {
      return cached;
    }
    const request = http.createHttp();
    try {
      const resp = await request.request(url, {
        method: http.RequestMethod.GET,
        expectDataType: http.HttpDataType.ARRAY_BUFFER,
        connectTimeout: 10000,
        readTimeout: 15000
      });
      if (resp.responseCode !== 200 || !(resp.result instanceof ArrayBuffer)) {
        return undefined;
      }
      const source = image.createImageSource(resp.result);
      const pixelMap = await source.createPixelMap();
      source.release();
      ImageLoader.cache.set(url, pixelMap);
      return pixelMap;
    } catch (e) {
      return undefined;
    } finally {
      request.destroy();
    }
  }
}
