package com.aims.agent;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import com.aims.core.interview.FollowUpContext;
import com.aims.core.interview.FollowUpDecision;
import com.aims.core.interview.FollowUpType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/** 默认追问 Agent 实现：STANDARD 档决策 + FLAGSHIP 档流式生成。 */
@Service
public class DefaultFollowUpAgent implements FollowUpAgent {

    private static final Logger log = LoggerFactory.getLogger(DefaultFollowUpAgent.class);

    private final AiChatFacade aiChatFacade;
    private final ObjectMapper objectMapper;

    public DefaultFollowUpAgent(AiChatFacade aiChatFacade, ObjectMapper objectMapper) {
        this.aiChatFacade = aiChatFacade;
        this.objectMapper = objectMapper;
    }

    @Override
    public FollowUpDecision evaluate(FollowUpContext context) {
        try {
            String result =
                    aiChatFacade.call(
                            ModelTier.STANDARD,
                            FollowUpPromptBuilder.decisionSystem(),
                            FollowUpPromptBuilder.decisionUser(context));
            if (result == null || result.isBlank()) {
                log.warn("追问决策返回空，默认不追问 sessionId={}", context.sessionId());
                return FollowUpDecision.noFollowUp("决策返回空");
            }
            return parseDecision(result, context.sessionId());
        } catch (Exception e) {
            log.warn("追问决策异常，默认不追问 sessionId={}", context.sessionId(), e);
            return FollowUpDecision.noFollowUp("决策异常: " + e.getMessage());
        }
    }

    @Override
    public Flux<String> streamFollowUp(FollowUpContext context, FollowUpDecision decision) {
        return aiChatFacade.stream(
                        ModelTier.FLAGSHIP,
                        InterviewPromptBuilder.interviewerSystem(),
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
            log.warn("追问决策 JSON 解析失败，默认不追问 sessionId={} raw={}", sessionId, json, e);
            return FollowUpDecision.noFollowUp("JSON 解析失败");
        }
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
