package com.login.main.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 系統時間與日期工具類
 * 
 * 提供日期格式化與時間加總計算功能，主要用於日誌輸出與過期時間顯示。
 */
public class DateTimeUtil {

    private static final String DEFAULT_PATTERN = "yyyy年MM月dd日 HH:mm:ss";

    /**
     * 將指定的毫秒數與基準時間加總，並格式化為字串回傳
     * 
     * @param durationMs 要增加的毫秒數
     * @param currentTime 當前基準時間 (毫秒)
     * @return 格式化後的日期時間字串 (yyyy年MM月dd日 HH:mm:ss)
     */
    public static String formatWithDuration(long durationMs, long currentTime) {
        Date targetDate = new Date(currentTime + durationMs);
        SimpleDateFormat sdf = new SimpleDateFormat(DEFAULT_PATTERN);
        return sdf.format(targetDate);
    }
}
