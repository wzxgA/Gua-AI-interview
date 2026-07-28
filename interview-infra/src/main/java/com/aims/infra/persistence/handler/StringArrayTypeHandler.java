package com.aims.infra.persistence.handler;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * PostgreSQL text[] 类型 ↔ Java String[] 的 MyBatis TypeHandler。
 *
 * <p>用于 {@code question_bank.tags} 列（PostgreSQL {@code TEXT[]}）的自动映射。 配合实体类
 * {@code @TableField(typeHandler = StringArrayTypeHandler.class)} 使用。
 */
@MappedJdbcTypes(JdbcType.ARRAY)
@MappedTypes(String[].class)
public class StringArrayTypeHandler extends BaseTypeHandler<String[]> {

    @Override
    public void setNonNullParameter(
            PreparedStatement ps, int i, String[] parameter, JdbcType jdbcType)
            throws SQLException {
        Connection conn = ps.getConnection();
        Array array = conn.createArrayOf("text", parameter);
        ps.setArray(i, array);
    }

    @Override
    public String[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toStringArray(rs.getArray(columnName));
    }

    @Override
    public String[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toStringArray(rs.getArray(columnIndex));
    }

    @Override
    public String[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toStringArray(cs.getArray(columnIndex));
    }

    private static String[] toStringArray(Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        Object[] objArray = (Object[]) array.getArray();
        String[] result = new String[objArray.length];
        for (int i = 0; i < objArray.length; i++) {
            result[i] = objArray[i] == null ? null : objArray[i].toString();
        }
        return result;
    }
}
