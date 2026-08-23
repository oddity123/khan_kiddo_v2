package com.khankiddo.learning.conversation;

import com.khankiddo.learning.model.ConversationAnalysisItem;
import org.springframework.util.StringUtils;

/**
 * 对话分析落库字段长度，与 {@code sql/DDL.sql} 中 VARCHAR 上限对齐。
 */
public final class ConversationAnalysisPersistSupport {

    public static final int ANALYSIS_ID_MAX = 64;
    public static final int POINT_ID_MAX = 48;
    public static final int ERROR_POINT_MAX = 500;
    public static final int LLM_MODEL_ID_MAX = 100;
    public static final int LLM_MODEL_NAME_MAX = 160;
    public static final int LLM_PROVIDER_MAX = 60;
    public static final int ERROR_MESSAGE_MAX = 2000;

    private ConversationAnalysisPersistSupport() {
    }

    public static String truncate(String value, int maxChars) {
        if (!StringUtils.hasText(value) || maxChars <= 0) {
            return value;
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    public static ConversationAnalysisItem truncateItem(ConversationAnalysisItem item) {
        if (item == null) {
            return null;
        }
        item.setAnalysisId(truncate(item.getAnalysisId(), ANALYSIS_ID_MAX));
        item.setPointId(truncate(item.getPointId(), POINT_ID_MAX));
        item.setErrorPoint(truncate(item.getErrorPoint(), ERROR_POINT_MAX));
        return item;
    }
}
