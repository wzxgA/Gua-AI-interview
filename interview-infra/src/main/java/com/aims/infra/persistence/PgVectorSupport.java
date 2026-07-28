package com.aims.infra.persistence;

/**
 * pgvector 向量列格式转换工具。
 *
 * <p>pgvector 的 vector 类型在 SQL 中用字符串表示，如 "[0.1,0.2,0.3]"。 本类封装 float[] <-> pgvector 字符串的互转，供 Mapper
 * 层使用。
 */
public final class PgVectorSupport {

    private PgVectorSupport() {}

    /**
     * 将 float[] 转为 pgvector 字符串格式："[0.1,0.2,0.3]" 用于 SQL 参数绑定（如 UPDATE ... SET embedding =
     * ?::vector）。
     */
    public static String toVectorString(float[] embedding) {
        if (embedding == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /** 将 pgvector 查询结果字符串解析为 float[]。 pgvector 返回格式如 "[0.1,0.2,0.3]"。 */
    public static float[] fromVectorString(String vectorStr) {
        if (vectorStr == null || vectorStr.isBlank()) {
            return null;
        }
        // 去除首尾的 [ ]
        String inner = vectorStr.trim();
        if (inner.startsWith("[")) {
            inner = inner.substring(1);
        }
        if (inner.endsWith("]")) {
            inner = inner.substring(0, inner.length() - 1);
        }
        if (inner.isBlank()) {
            return new float[0];
        }
        String[] parts = inner.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
