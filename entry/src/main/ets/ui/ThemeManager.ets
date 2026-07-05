import { preferences } from '@kit.ArkData';
import { common, ConfigurationConstant } from '@kit.AbilityKit';

/**
 * 主题档位:跟随系统 / 浅色 / 深色。持久化选择并映射到应用级 ColorMode。
 * 鸿蒙走 ApplicationContext.setColorMode,base/dark 两套资源随之自动切换。
 */
export enum ThemeMode {
  System,
  Light,
  Dark
}

export class ThemeManager {
  static getMode(context: common.Context): ThemeMode {
    const store = preferences.getPreferencesSync(context, { name: ThemeManager.PREFS });
    const v = store.getSync(ThemeManager.KEY_MODE, ThemeMode.System) as number;
    if (v === ThemeMode.Light) {
      return ThemeMode.Light;
    }
    if (v === ThemeMode.Dark) {
      return ThemeMode.Dark;
    }
    return ThemeMode.System;
  }

  static setMode(context: common.Context, mode: ThemeMode): void {
    const store = preferences.getPreferencesSync(context, { name: ThemeManager.PREFS });
    store.putSync(ThemeManager.KEY_MODE, mode);
    store.flush();
    ThemeManager.apply(context, mode);
  }

  /** 把档位映射到应用级 ColorMode,立即生效 */
  static apply(context: common.Context, mode: ThemeMode): void {
    const appContext = context.getApplicationContext();
    switch (mode) {
      case ThemeMode.Light:
        appContext.setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_LIGHT);
        break;
      case ThemeMode.Dark:
        appContext.setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_DARK);
        break;
      default:
        appContext.setColorMode(ConfigurationConstant.ColorMode.COLOR_MODE_NOT_SET);
    }
  }

  private static readonly PREFS: string = 'appearance';
  private static readonly KEY_MODE: string = 'theme_mode';
}
