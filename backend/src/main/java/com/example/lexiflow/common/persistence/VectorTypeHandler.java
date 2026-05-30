package com.example.lexiflow.common.persistence;

import com.pgvector.PGvector;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class VectorTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        List<Double> values = parseVectorString(parameter);
        ps.setObject(i, new PGvector(values));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return vectorToString(rs.getObject(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return vectorToString(rs.getObject(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return vectorToString(cs.getObject(columnIndex));
    }

    private String vectorToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof PGvector v) {
            String str = v.getValue();
            if (str != null && str.startsWith("[") && str.endsWith("]")) {
                return str;
            }
            return "[" + str + "]";
        }
        return String.valueOf(value);
    }

    private List<Double> parseVectorString(String vectorStr) {
        if (vectorStr == null || vectorStr.isBlank()) {
            return List.of();
        }
        String trimmed = vectorStr.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Double::parseDouble)
                .toList();
    }
}
