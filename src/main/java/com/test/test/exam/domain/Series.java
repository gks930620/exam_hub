package com.test.test.exam.domain;

/**
 * 자격증 계열. label 은 화면/pSEO 표기용 한글명.
 */
public enum Series {
    TECHNICIAN("기사"),
    INDUSTRIAL("산업기사"),
    CRAFTSMAN("기능사"),
    SERVICE("서비스"),
    MASTER("기능장"),
    PROFESSIONAL("기술사"),
    ETC("기타");

    private final String label;

    Series(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
