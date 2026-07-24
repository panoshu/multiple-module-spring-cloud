package com.example.shared.json.path;

import java.util.List;

/**
 * JSON 路径规则，从 JsonBodySanitizer 抽取的通用路径匹配数据结构。
 *
 * <p>将 JsonPath 表达式（如 {@code $.user.info.password}）解析为：
 * <ul>
 *   <li>{@code leafName} = "password"（叶子字段名）</li>
 *   <li>{@code parentSegments} = ["info", "user"]（父级路径，倒序存储）</li>
 *   <li>{@code isDeepScan} = false（是否深度扫描，即 {@code $..} 语法）</li>
 * </ul>
 *
 * <p>使用 record 保证主体不可变，{@code parentSegments} 使用 {@link List}（通过 {@link List#copyOf} 创建）
 * 保证深度不可变，避免数组可变导致的线程安全风险。
 *
 * @param leafName       叶子字段名
 * @param parentSegments 父级路径段（倒序：从叶子到根），不含 leafName；不可变 List
 * @param isDeepScan     是否深度扫描模式（{@code $..fieldName}）
 * @author trae
 * @since 1.0
 */
public record RulePath(
    String leafName,
    List<String> parentSegments,
    boolean isDeepScan
) {
}
