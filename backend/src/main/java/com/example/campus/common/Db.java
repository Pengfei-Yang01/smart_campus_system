package com.example.campus.common;

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

/**
 * 控制器层共用的轻量数据库工具类。
 *
 * 本项目直接使用数据库访问模板，因此把查询单行数据、读取自增主键、
 * 校验必填字段、转换数据库日期时间等通用操作集中在这里，
 * 让控制器更专注于业务规则。
 */
@Component
public class Db {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    /**
     * 从后端容器接收数据库访问工具对象。
     *
     * @param jdbc 使用位置参数的数据库访问工具
     * @param named 动态筛选时使用的命名参数数据库访问工具
     */
    public Db(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    /**
     * @return 控制器执行数据库语句使用的访问工具
     */
    public JdbcTemplate jdbc() {
        return jdbc;
    }

    /**
     * @return 执行命名参数数据库语句使用的访问工具
     */
    public NamedParameterJdbcTemplate named() {
        return named;
    }

    /**
     * 查询并返回第一行数据；如果没有数据则抛出业务异常。
     * 当请求的资源不存在时使用。
     *
     * @param sql 要执行的数据库查询
     * @param args 位置参数
     * @return 第一条匹配记录的列值映射
     */
    public Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) {
            throw new BusinessException("数据不存在");
        }
        return rows.get(0);
    }

    /**
     * 返回第一行数据；没有匹配数据时返回 {@code null}。
     *
     * @param sql 要执行的数据库查询
     * @param args 位置参数
     * @return 第一行数据或 null
     */
    public Map<String, Object> maybeOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 执行计数查询，并把空结果统一转成 0。
     *
     * @param sql 返回单个数字计数的数据库语句
     * @param args 位置参数
     * @return 计数结果
     */
    public int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    /**
     * 执行插入语句并返回数据库生成的主键。
     *
     * @param sql 插入数据库语句
     * @param args 插入语句的位置参数
     * @return 数据库生成的主键；没有返回时为 null
     */
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

    /**
     * 为命名参数查询添加包含条件参数。
     *
     * @param column 包含条件前面的数据库列名
     * @param values 绑定到命名参数的值
     * @param params 需要写入参数的容器
     * @param name 命名参数名称
     * @return 数据库包含条件片段
     */
    public String inClause(String column, List<?> values, MapSqlParameterSource params, String name) {
        params.addValue(name, values);
        return column + " IN (:" + name + ")";
    }

    /**
     * 校验字符串字段存在且不为空白。
     */
    public static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(name + " 不能为空");
        }
        return value.trim();
    }

    /**
     * 校验枚举字段存在。
     */
    public static <E extends Enum<E>> E require(E value, String name) {
        if (value == null) {
            throw new BusinessException(name + " 不能为空");
        }
        return value;
    }

    /**
     * 校验长整数字段存在。
     */
    public static Long require(Long value, String name) {
        if (value == null) {
            throw new BusinessException(name + " 不能为空");
        }
        return value;
    }

    /**
     * 校验整数字段存在。
     */
    public static Integer require(Integer value, String name) {
        if (value == null) {
            throw new BusinessException(name + " 不能为空");
        }
        return value;
    }

    /**
     * 校验小数字段存在。
     */
    public static Double require(Double value, String name) {
        if (value == null) {
            throw new BusinessException(name + " 不能为空");
        }
        return value;
    }

    /**
     * 从旧版键值请求体读取可选字符串。
     */
    public static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    /**
     * 从旧版键值请求体读取并校验必填字符串。
     */
    public static String required(Map<String, Object> body, String key) {
        return require(str(body, key), key);
    }

    /**
     * 从旧版键值请求体读取可选长整数值。
     */
    public static Long longVal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    /**
     * 从旧版键值请求体读取可选小数值。
     */
    public static Double doubleVal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    /**
     * 从旧版键值请求体读取可选整数值。
     */
    public static Integer intVal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }

    /**
     * 把前端传来的必填日期时间字符串解析为后端日期时间对象。
     */
    public static LocalDateTime dateTime(String value, String fieldName) {
        String text = require(value, fieldName);
        return LocalDateTime.parse(text.replace(" ", "T"));
    }

    /**
     * 从旧版键值请求体解析必填日期时间。
     */
    public static LocalDateTime dateTime(Map<String, Object> body, String key) {
        return dateTime(str(body, key), key);
    }

    /**
     * 把后端日期时间对象转为数据库写入需要的时间戳。
     */
    public static Timestamp ts(LocalDateTime time) {
        return time == null ? null : Timestamp.valueOf(time);
    }

    /**
     * 兼容不同数据库驱动版本返回的日期时间值。
     *
     * 有的驱动返回数据库时间戳，有的驱动可能直接返回后端日期时间对象。
     * 集中转换可以修复之前学生报名活动时出现的日期时间强制转换问题。
     *
     * @param value 数据库访问工具返回的原始值
     * @return 统一转换后的日期时间对象或空值
     */
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

    /**
     * 把角色查询结果转换成排序后的角色编码列表。
     */
    public static List<String> rolesFromRows(List<Map<String, Object>> rows) {
        Set<String> roles = new TreeSet<>();
        for (Map<String, Object> row : rows) {
            roles.add(String.valueOf(row.get("role_code")));
        }
        return new ArrayList<>(roles);
    }

    /**
     * 使用安全摘要算法计算密码摘要，以匹配初始化脚本。
     *
     * @param raw 明文密码
     * @return 小写十六进制密码摘要
     */
    public static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException("密码加密失败");
        }
    }
}
