package com.khankiddo.learning.model.enums;

import org.springframework.util.StringUtils;

public enum ProblemType {

    TENSE("Tense", "时态错误"),
    AGREEMENT("Agreement", "主谓一致"),
    PLURAL("Plural", "单复数错误"),
    ARTICLE("Article", "冠词错误"),
    PREPOSITION("Preposition", "介词错误"),
    PRONOUN("Pronoun", "代词错误"),
    STRUCTURE("Structure", "句式结构"),
    CLAUSE("Clause", "从句错误"),
    WORD_FORM("Word Form", "词性错误"),
    COMPARISON("Comparison", "比较级错误"),
    WORD_CHOICE("Word Choice", "用词不当"),
    COLLOCATION("Collocation", "搭配错误"),
    CHINGLISH("Chinglish", "中式英语"),
    REDUNDANCY("Redundancy", "表达冗余"),
    TONE("Tone", "语气不当"),
    UNNATURAL("Unnatural", "表达生硬"),
    VOCABULARY("Vocabulary", "词汇贫乏"),
    FORMAL("Formal", "口语化不足"),
    INCOMPLETE("Incomplete", "句子未完成"),
    CHINESE("Chinese", "中文表达");

    private final String englishName;
    private final String chineseName;

    ProblemType(String englishName, String chineseName) {
        this.englishName = englishName;
        this.chineseName = chineseName;
    }

    public String getEnglishName() {
        return englishName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public static ProblemType fromEnglishName(String englishName) {
        if (!StringUtils.hasText(englishName)) {
            return null;
        }
        String trimmed = englishName.trim();
        for (ProblemType type : values()) {
            if (type.englishName.equals(trimmed)) {
                return type;
            }
        }
        return null;
    }

    public ErrorLevel getErrorLevel() {
        return switch (this) {
            case TENSE, AGREEMENT, STRUCTURE, CLAUSE, INCOMPLETE -> ErrorLevel.FATAL;
            case PLURAL, ARTICLE, PRONOUN, WORD_FORM, COMPARISON, PREPOSITION -> ErrorLevel.BASIC;
            case WORD_CHOICE, COLLOCATION, CHINGLISH, UNNATURAL, REDUNDANCY -> ErrorLevel.NATURAL;
            default -> ErrorLevel.STYLE;
        };
    }

    public static String translate(String englishName) {
        ProblemType type = fromEnglishName(englishName);
        return type != null ? type.getChineseName() : englishName;
    }
}
