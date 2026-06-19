package com.example.campus;

import java.security.MessageDigest;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class Db {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public Db(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public NamedParameterJdbcTemplate named() {
        return named;
    }

    public Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) {
            throw new BusinessException("数据不存在");
        }
        return rows.get(0);
    }

    public Map<String, Object> maybeOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    public Long insert(String sql, Object... args) {
        org.springframework.jdbc.support.GeneratedKeyHolder holder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, holder);
        Number key = holder.getKey();
        return key == null ? null : key.longValue();
    }

    public String inClause(String column, List<?> values, MapSqlParameterSource params, String name) {
        params.addValue(name, values);
        return column + " IN (:" + name + ")";
    }

    public static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    public static String required(Map<String, Object> body, String key) {
        String value = str(body, key);
        if (value == null || value.isBlank()) {
            throw new BusinessException(key + " 不能为空");
        }
        return value;
    }

    public static Long longVal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    public static Double doubleVal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    public static Integer intVal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }

    public static LocalDateTime dateTime(Map<String, Object> body, String key) {
        String value = str(body, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value.replace(" ", "T"));
    }

    public static Timestamp ts(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }

    public static LocalDateTime localDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Date date) {
            return date.toLocalDate().atStartOfDay();
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime()).toLocalDateTime();
        }
        if (value instanceof LocalDate date) {
            return date.atStartOfDay();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(text.replace(" ", "T"));
    }

    public static List<String> rolesFromRows(List<Map<String, Object>> rows) {
        Set<String> roles = new TreeSet<>();
        for (Map<String, Object> row : rows) {
            roles.add(String.valueOf(row.get("role_code")));
        }
        return new ArrayList<>(roles);
    }

    public static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException("密码加密失败");
        }
    }
}
