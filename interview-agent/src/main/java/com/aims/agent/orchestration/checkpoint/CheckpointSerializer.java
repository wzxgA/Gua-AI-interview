package com.aims.agent.orchestration.checkpoint;

import com.aims.agent.orchestration.state.InterviewState;
import com.aims.core.evaluation.RoundEvaluation;
import com.aims.core.interview.ConflictDetail;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.InterviewPlan;
import com.aims.core.interview.InterviewerPersona;
import com.aims.core.interview.QaPair;
import com.aims.core.interview.SupervisorDecision;
import com.aims.core.report.ReportResult;
import com.aims.core.session.SessionStatus;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checkpoint 序列化器：负责 {@link CheckpointRecord} 与 JSON 字符串之间的转换。
 *
 * <h2>序列化策略</h2>
 *
 * <p>采用「普通 {@link ObjectMapper} + Schema 感知的类型归一化」，而非 Jackson Default Typing。原因：
 *
 * <ol>
 *   <li>{@code InterviewState} 的值类型多为 {@code final}（record / enum / 包装类型），Default Typing 的 {@code
 *       NON_FINAL} 模式不会为它们写入 {@code @class}，反序列化后将退化为 {@code LinkedHashMap} / {@code String}，丢失类型。
 *   <li>{@code As.PROPERTY} 无法为标量（Long/Integer/String）和数组（List）追加 {@code @class} 属性，因此 {@code
 *       sessionId(Long)} 这类小整数往返后会被误读为 {@code Integer}，导致 {@code InterviewState.sessionId()} 的
 *       {@code <Long>} 强转抛 {@code ClassCastException}。
 *   <li>Default Typing 的 {@code @class} 携带全限定类名，存在反序列化 gadgets 风险，需 {@code
 *       PolymorphicTypeValidator} 白名单维护；本方案完全不写入类型标识，从根上消除该攻击面。
 * </ol>
 *
 * <p>序列化时 Jackson 依据运行时类型写出 JSON；反序列化时 {@code Map<String, Object>} 的值会退化为 标量 / {@code
 * LinkedHashMap} / {@code ArrayList}，随后由 {@link #normalizeState(Map)} 依据 {@link InterviewState} 的
 * Schema 还原精确类型（Long/Integer/enum/record/List 元素）。该过程是幂等的。
 *
 * <p>当 {@code InterviewState} 新增字段时：String / 数值字段自动兼容；新增 record / enum 字段需在此处 的类型登记表中补齐，否则该字段会以
 * {@code LinkedHashMap} / {@code String} 形式保留（不会报错）。
 *
 * @since 1.1.0
 */
public class CheckpointSerializer {

    private final ObjectMapper mapper;

    public CheckpointSerializer() {
        this(defaultMapper());
    }

    public CheckpointSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private static ObjectMapper defaultMapper() {
        ObjectMapper m = new ObjectMapper();
        // F1：sessionStartedAt 为 java.time.Instant，需 JSR310 模块序列化/反序列化。
        // 关闭时间戳输出：Instant 写 ISO-8601 字符串（无损、可读），否则默认输出 epoch 秒小数（Double），
        // 反序列化经 Map<String,Object> 读回会退化为 Double，导致 <Instant> 强转 ClassCastException。
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return m;
    }

    /** 序列化 {@link CheckpointRecord} 为 JSON 字符串。 */
    public String serialize(CheckpointRecord record) {
        try {
            return mapper.writeValueAsString(record);
        } catch (Exception e) {
            throw new CheckpointSerializationException("Failed to serialize checkpoint", e);
        }
    }

    /** 反序列化 JSON 字符串为 {@link CheckpointRecord}，并还原 {@code stateData} 的精确类型。 */
    public CheckpointRecord deserialize(String json) {
        try {
            CheckpointRecord raw = mapper.readValue(json, CheckpointRecord.class);
            return new CheckpointRecord(
                    raw.checkpointId(),
                    raw.nodeId(),
                    raw.nextNodeId(),
                    normalizeState(raw.stateData()),
                    raw.timestampEpochMillis());
        } catch (Exception e) {
            throw new CheckpointSerializationException("Failed to deserialize checkpoint", e);
        }
    }

    // ─── Schema 感知的类型登记表 ───

    private static final Map<String, Class<? extends Enum<?>>> ENUM_TYPES =
            Map.of(
                    InterviewState.PERSONA, InterviewerPersona.class,
                    InterviewState.SESSION_STATUS, SessionStatus.class,
                    InterviewState.FOLLOW_UP_TYPE, FollowUpType.class);

    private static final Map<String, Class<?>> RECORD_TYPES =
            Map.of(
                    InterviewState.INTERVIEW_PLAN, InterviewPlan.class,
                    InterviewState.FOLLOW_UP_DECISION, FollowUpDecision.class,
                    InterviewState.REPORT_RESULT, ReportResult.class,
                    InterviewState.SUPERVISOR_DECISION, SupervisorDecision.class);

    private static final Map<String, Class<?>> LONG_TYPES =
            Map.of(
                    InterviewState.SESSION_ID,
                    Long.class,
                    InterviewState.RESUME_ID,
                    Long.class,
                    InterviewState.CURRENT_ROUND_ID,
                    Long.class,
                    InterviewState.ELAPSED_MS,
                    Long.class);

    private static final Map<String, Class<?>> INT_TYPES =
            Map.ofEntries(
                    Map.entry(InterviewState.TOTAL_ROUNDS, Integer.class),
                    Map.entry(InterviewState.CURRENT_SEQ, Integer.class),
                    Map.entry(InterviewState.PARENT_SEQ, Integer.class),
                    Map.entry(InterviewState.FOLLOW_UP_INDEX, Integer.class),
                    Map.entry(InterviewState.FOLLOW_UP_COUNT, Integer.class),
                    Map.entry(InterviewState.LAST_SUMMARIZED_SEQ, Integer.class),
                    Map.entry(InterviewState.RETRY_COUNT, Integer.class));

    /** List 元素为 record 类型（反序列化后为 LinkedHashMap，需 convertValue 还原）。 */
    private static final Map<String, Class<?>> LIST_RECORD_ELEMENTS =
            Map.of(
                    InterviewState.QA_HISTORY, QaPair.class,
                    InterviewState.ROUND_EVALUATIONS, RoundEvaluation.class);

    /** List 元素为 Long（JSON 小整数会被读成 Integer，需还原）。 */
    private static final Map<String, Class<?>> LIST_LONG_ELEMENTS =
            Map.of(InterviewState.EVALUATED_ROUND_IDS, Long.class);

    // ─── 归一化逻辑 ───

    private Map<String, Object> normalizeState(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return data == null ? new LinkedHashMap<>() : data;
        }
        Map<String, Object> result = new LinkedHashMap<>(data.size());
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            result.put(entry.getKey(), normalizeValue(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object normalizeValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (InterviewState.SESSION_STARTED_AT.equals(key)) {
            return toInstant(value);
        }
        Class<?> recordType = RECORD_TYPES.get(key);
        if (recordType != null && value instanceof Map) {
            return mapper.convertValue(value, recordType);
        }
        Class<?> enumType = ENUM_TYPES.get(key);
        if (enumType != null && !(value instanceof Enum)) {
            return Enum.valueOf((Class<Enum>) enumType, value.toString());
        }
        if (value instanceof Number) {
            if (LONG_TYPES.containsKey(key)) {
                return ((Number) value).longValue();
            }
            if (INT_TYPES.containsKey(key)) {
                return ((Number) value).intValue();
            }
        }
        if (value instanceof List<?> list) {
            return normalizeList(key, list);
        }
        // v1.1-F4：CONFLICT_DETAILS_BY_ROUND 为 Map<String, List<ConflictDetail>>，
        // 反序列化后退化为 Map<String, List<LinkedHashMap>>，需还原元素为 ConflictDetail，
        // 否则 EvaluateNode/ReportNode 访问 conflictField() 会抛 ClassCastException
        if (InterviewState.CONFLICT_DETAILS_BY_ROUND.equals(key)
                && value instanceof Map<?, ?> map) {
            return normalizeConflictDetailsMap(map);
        }
        return value;
    }

    /** 还原矛盾点映射：Map<String, List<ConflictDetail>>（元素由 LinkedHashMap 转回 ConflictDetail）。 */
    @SuppressWarnings("unchecked")
    private Map<String, List<ConflictDetail>> normalizeConflictDetailsMap(Map<?, ?> map) {
        Map<String, List<ConflictDetail>> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String k = String.valueOf(entry.getKey());
            if (entry.getValue() instanceof List<?> list) {
                List<ConflictDetail> details = new ArrayList<>(list.size());
                for (Object element : list) {
                    if (element instanceof ConflictDetail cd) {
                        details.add(cd);
                    } else if (element instanceof Map) {
                        details.add(mapper.convertValue(element, ConflictDetail.class));
                    }
                }
                result.put(k, details);
            }
        }
        return result;
    }

    /**
     * 还原 {@code sessionStartedAt} 为 {@link Instant}：兼容新 checkpoint（ISO-8601 字符串）与旧 checkpoint
     * （JSR310 默认输出的 epoch 秒 Double）。
     */
    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            double secs = number.doubleValue();
            long epochSecond = (long) secs;
            int nano = (int) Math.round((secs - epochSecond) * 1_000_000_000);
            return Instant.ofEpochSecond(epochSecond, nano);
        }
        return Instant.parse(value.toString());
    }

    private List<Object> normalizeList(String key, List<?> list) {
        Class<?> recordElem = LIST_RECORD_ELEMENTS.get(key);
        boolean longElem = LIST_LONG_ELEMENTS.containsKey(key);
        if (recordElem == null && !longElem) {
            return new ArrayList<>(list);
        }
        List<Object> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element == null) {
                result.add(null);
            } else if (recordElem != null && element instanceof Map) {
                result.add(mapper.convertValue(element, recordElem));
            } else if (longElem && element instanceof Number) {
                result.add(((Number) element).longValue());
            } else {
                result.add(element);
            }
        }
        return result;
    }
}
