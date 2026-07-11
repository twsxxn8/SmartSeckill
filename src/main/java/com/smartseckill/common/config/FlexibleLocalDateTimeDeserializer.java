package com.smartseckill.common.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * 灵活 LocalDateTime 反序列化器，兼容多种日期格式。
 * <p>
 * 按优先级尝试解析：
 * <ol>
 *   <li>{@code yyyy-MM-dd HH:mm:ss}</li>
 *   <li>{@code yyyy-MM-dd'T'HH:mm:ss}</li>
 *   <li>{@code yyyy-MM-dd'T'HH:mm:ss.SSS}</li>
 *   <li>{@code yyyy-MM-dd HH:mm:ss.SSS}</li>
 *   <li>{@code yyyy-MM-dd'T'HH:mm:ss.SSSSSS}</li>
 *   <li>{@code yyyy-MM-dd'T'HH:mm:ss.SSSSSSS}</li>
 * </ol>
 * 任一格式匹配成功即返回，全部失败则抛出异常。
 * </p>
 */
public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final List<DateTimeFormatter> FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText().trim();
        if (text.isEmpty()) {
            return null;
        }

        // 1. 尝试直接解析（包含空格或 T）
        for (DateTimeFormatter fmt : FORMATTERS) {
            try {
                return LocalDateTime.parse(text, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }

        // 2. 替换空格为 T 再试
        if (text.contains(" ")) {
            String isoStyle = text.replace(" ", "T");
            for (DateTimeFormatter fmt : FORMATTERS) {
                try {
                    return LocalDateTime.parse(isoStyle, fmt);
                } catch (DateTimeParseException ignored) {
                }
            }
        }

        // 3. 替换 T 为空格再试
        if (text.contains("T")) {
            String spaceStyle = text.replace("T", " ");
            for (DateTimeFormatter fmt : FORMATTERS) {
                try {
                    return LocalDateTime.parse(spaceStyle, fmt);
                } catch (DateTimeParseException ignored) {
                }
            }
        }

        // 4. 补充秒位：如果只有日期和时分（yyyy-MM-dd HH:mm 或 yyyy-MM-ddTHH:mm）
        if (text.matches("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}")) {
            try {
                return LocalDateTime.parse(text.replace(" ", "T") + ":00",
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ignored) {
            }
        }

        throw new IOException("Cannot parse LocalDateTime: " + text
                + ". Expected yyyy-MM-dd HH:mm:ss or ISO format.");
    }
}
