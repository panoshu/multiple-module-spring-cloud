package com.example.approval.domain.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 终止级别值对象
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record TerminalLevel(int value) implements ValueObject, Comparable<TerminalLevel> {

    public TerminalLevel {
        if (value < 1) {
            throw new IllegalArgumentException("终止级别必须大于等于1");
        }
    }

    /**
     * 静态工厂方法
     *
     * @param value 级别值
     * @return TerminalLevel 实例
     */
    public static TerminalLevel of(int value) {
        return new TerminalLevel(value);
    }

    @Override
    public int compareTo(TerminalLevel other) {
        return Integer.compare(this.value, other.value);
    }
}