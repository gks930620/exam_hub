package com.test.test.exam.domain;

/**
 * 시험 구분: 필기 / 실기.
 */
public enum ExamType {
    WRITTEN("필기"),
    PRACTICAL("실기");

    private final String label;

    ExamType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
