/**
 * 通用驗證工具
 */
export const ValidationUtil = {
  /**
   * 驗證是否僅包含數字、英文字母與中文
   * @param value 待驗證字串
   * @returns boolean
   */
  isAlphanumericOrChinese: (value: string): boolean => {
    // 允許：A-Z, a-z, 0-9, 以及中文 (\u4e00-\u9fa5)
    const regex = /^[a-zA-Z0-9\u4e00-\u9fa5]+$/;
    return regex.test(value);
  },

  /**
   * 驗證是否包含非法字元 (數字、英文字母、中文以外的符號)
   * @param value 待驗證字串
   * @returns boolean (true 表示包含非法符號)
   */
  hasIllegalCharacters: (value: string): boolean => {
    return !ValidationUtil.isAlphanumericOrChinese(value);
  },

  /**
   * 驗證是否僅包含數字與英文字母 (不含中文)
   * @param value 待驗證字串
   * @returns boolean
   */
  isAlphanumeric: (value: string): boolean => {
    const regex = /^[a-zA-Z0-9]+$/;
    return regex.test(value);
  },

  /**
   * 驗證是否包含非英數之字元 (包含中文與符號)
   * @param value 待驗證字串
   * @returns boolean (true 表示包含非英數字元)
   */
  hasNonAlphanumeric: (value: string): boolean => {
    return !ValidationUtil.isAlphanumeric(value);
  }
};
