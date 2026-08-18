package com.aims.agent;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import com.aims.core.interview.ConflictDetail;
import com.aims.core.interview.FollowUpContext;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;
import com.aims.core.interview.InterviewerPersona;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/** 默认追问 Agent 实现：STANDARD 档决策（F2 起注册简历交叉验证工具）+ FLAGSHIP 档流式生成。 */
@Service
public class DefaultFollowUpAgent implements FollowUpAgent {

    private static final Logger log = LoggerFactory.getLogger(DefaultFollowUpAgent.class);

    private final AiChatFacade aiChatFacade;
    private final ObjectMapper objectMapper;
    private final ResumeCrossCheckTool resumeCrossCheckTool;

    public DefaultFollowUpAgent(
            AiChatFacade aiChatFacade,
            ObjectMapper objectMapper,
            ResumeCrossCheckTool resumeCrossCheckTool) {
        this.aiChatFacade = aiChatFacade;
        this.objectMapper = objectMapper;
        this.resumeCrossCheckTool = resumeCrossCheckTool;
    }

    @Override
    public FollowUpDecision evaluate(FollowUpContext context) {
        try {
            // F2：注册简历交叉验证工具，模型判定"需查证回答与简历一致性"时自主调用，工具结果仅作证据
            // F4：决策前先经规则通道探测矛盾点（公司/项目/时间），注入决策 prompt 并随决策返回，供落库/评估/报告引用
            List<ConflictDetail> conflicts = probeConflicts(context);
            String result =
                    aiChatFacade.callWithTools(
                            ModelTier.STANDARD,
                            FollowUpPromptBuilder.decisionSystem(),
                            FollowUpPromptBuilder.decisionUser(context, conflicts),
                            List.of(resumeCrossCheckTool));
            if (result == null || result.isBlank()) {
                log.warn("追问决策返回空，默认不追问 sessionId={}", context.sessionId());
                return FollowUpDecision.noFollowUp("决策返回空");
            }
            return withConflicts(parseDecision(result, context.sessionId()), conflicts);
        } catch (Exception e) {
            log.warn("追问决策异常，默认不追问 sessionId={}", context.sessionId(), e);
            return FollowUpDecision.noFollowUp("决策异常: " + e.getMessage());
        }
    }

    /** F4/F5 规则通道探测：简历 ID + 回答 → 经历表实体比对，返回矛盾点（探测失败返回空，不阻断决策）。 */
    private List<ConflictDetail> probeConflicts(FollowUpContext context) {
        if (context.resumeId() == null || context.answer() == null || context.answer().isBlank()) {
            return List.of();
        }
        try {
            // F5：先规则提取回答中的公司名作为 companyHint，使"回答提到简历外公司"也能定向比对"简历是否提及"
            String companyHint = CompanyNameExtractor.extract(context.answer());
            ResumeCrossCheckResult r =
                    resumeCrossCheckTool.crossCheck(
                            context.resumeId(), context.answer(), companyHint);
            return r == null ? List.of() : r.conflictDetails();
        } catch (Exception e) {
            log.debug("追问决策前矛盾点探测失败 sessionId={} err={}", context.sessionId(), e.getMessage());
            return List.of();
        }
    }

    /** 决策结果回填探测到的矛盾点（决策 JSON 本身不含矛盾点，随决策带出供落库/评估引用）。 */
    private FollowUpDecision withConflicts(
            FollowUpDecision decision, List<ConflictDetail> conflicts) {
        if (conflicts.isEmpty() || decision.conflictDetails().isEmpty() == false) {
            return decision;
        }
        return new FollowUpDecision(
                decision.shouldFollowUp(),
                decision.followUpType(),
                decision.followUpQuestion(),
                decision.reason(),
                conflicts);
    }

    @Override
    public Flux<String> streamFollowUp(FollowUpContext context, FollowUpDecision decision) {
        InterviewerPersona persona =
                context.persona() != null ? context.persona() : InterviewerPersona.FRIENDLY;
        return aiChatFacade.stream(
                        ModelTier.FLAGSHIP,
                        InterviewPromptBuilder.interviewerSystem(persona),
                        FollowUpPromptBuilder.followUpUser(context, decision))
                .filter(chunk -> chunk != null && !chunk.isBlank());
    }

    private FollowUpDecision parseDecision(String json, Long sessionId) {
        try {
            // 提取 JSON 部分（AI 可能输出多余文本）
            String jsonStr = extractJson(json);
            DecisionDto dto = objectMapper.readValue(jsonStr, DecisionDto.class);
            FollowUpType type = parseAction(dto.action());
            if (type == FollowUpType.NONE) {
                return FollowUpDecision.noFollowUp(dto.reason());
            }
            String question = dto.followUpQuestion();
            if (question == null || question.isBlank() || "null".equals(question)) {
                log.warn(
                        "追问决策 action={} 但 followUpQuestion 为空，默认不追问 sessionId={}",
                        dto.action(),
                        sessionId);
                return FollowUpDecision.noFollowUp("追问问题为空");
            }
            return FollowUpDecision.of(type, question.trim(), dto.reason());
        } catch (Exception e) {
            // 模型 JSON 偶发在 reason/追问问题里带未转义双引号导致标准解析失败：
            // 宽松提取 action 保住关键决策（NEXT vs CLARIFY/DEEPEN），避免追问被静默丢弃
            log.warn(
                    "追问决策 JSON 解析失败，尝试宽松提取 sessionId={} raw={}", sessionId, truncate(json, 300), e);
            return parseLeniently(json, sessionId);
        }
    }

    /** 宽松解析：标准 JSON 失败时，用正则至少提取 action 字段；追问类型还需要 followUpQuestion。 */
    private FollowUpDecision parseLeniently(String raw, Long sessionId) {
        Matcher actionMatcher = Pattern.compile("\"action\"\\s*:\\s*\"([A-Za-z]+)\"").matcher(raw);
        if (!actionMatcher.find()) {
            log.warn("追问决策宽松提取失败，默认不追问 sessionId={} raw={}", sessionId, truncate(raw, 300));
            return FollowUpDecision.noFollowUp("JSON 解析失败且无法提取 action");
        }
        FollowUpType type = parseAction(actionMatcher.group(1));
        if (type == FollowUpType.NONE) {
            return FollowUpDecision.noFollowUp("模型判定不追问（宽松解析）");
        }
        // 追问类型需要问题文本：尽力从原始输出提取（可能同样被裸引号截断）
        String question = extractQuestionLeniently(raw);
        if (question == null || question.isBlank() || "null".equals(question)) {
            log.warn("追问决策宽松提取缺 followUpQuestion，默认不追问 sessionId={} type={}", sessionId, type);
            return FollowUpDecision.noFollowUp("追问问题为空（宽松解析）");
        }
        return FollowUpDecision.of(type, question.trim(), "宽松解析提取");
    }

    private String extractQuestionLeniently(String raw) {
        Matcher m = Pattern.compile("\"followUpQuestion\"\\s*:\\s*\"([^\"]*)\"").matcher(raw);
        return m.find() ? m.group(1) : null;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "-";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private FollowUpType parseAction(String action) {
        if (action == null) return FollowUpType.NONE;
        return switch (action.trim().toUpperCase()) {
            case "CLARIFY" -> FollowUpType.CLARIFY;
            case "DEEPEN" -> FollowUpType.DEEPEN;
            case "REDIRECT" -> FollowUpType.REDIRECT;
            default -> FollowUpType.NONE;
        };
    }

    /** 从可能包含非 JSON 文本的响应中提取 JSON 对象。 */
    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DecisionDto(
            @JsonProperty("action") String action,
            @JsonProperty("reason") String reason,
            @JsonProperty("followUpQuestion") String followUpQuestion) {}
}
