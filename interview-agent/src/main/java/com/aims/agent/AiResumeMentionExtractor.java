package com.aims.agent;

import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 语义提名（二级）：当一级正则命中疑似公司/项目候选时，交由 AI 判定该候选是否为真实实体。
 *
 * <p>核心目标：消除"纯正则提取 → 无语义校验 → 比对失败即报矛盾"的假阳性，如「CLH等待队列」（技术概念）「只处理网络」（职责短语）被误判为公司。
 *
 * <p>仅保留 {@code isRealEntity=true} 的提名；失败/异常返回空列表（调用方回退一级正则，行为不劣化，不阻断追问链路）。
 */
@Component
public class AiResumeMentionExtractor implements ResumeEntityMentionExtractor {

    private static final Logger log = LoggerFactory.getLogger(AiResumeMentionExtractor.class);

    private final AiChatFacade aiChatFacade;

    public AiResumeMentionExtractor(AiChatFacade aiChatFacade) {
        this.aiChatFacade = aiChatFacade;
    }

    /**
     * AI 语义判定；语义约定：
     *
     * <ul>
     *   <li>返回 {@code null}：AI 不可用/超时/解析失败 → 调用方回退一级正则（行为不劣化）
     *   <li>返回空列表：AI 成功判定，但候选均为非真实体（如「CLH等待队列」「只处理网络」）→ 不产生矛盾点
     *   <li>返回非空：仅含 {@code isRealEntity=true} 的真实体提名
     * </ul>
     */
    @Override
    public List<ResumeMention> extract(String answer) {
        if (answer == null || answer.isBlank()) {
            return null;
        }
        try {
            MentionExtractionResult result =
                    aiChatFacade.callForEntity(
                            ModelTier.STANDARD,
                            MentionPromptBuilder.system(),
                            MentionPromptBuilder.user(answer),
                            MentionExtractionResult.class);
            if (result == null || result.mentions() == null) {
                return null;
            }
            return result.mentions().stream()
                    .filter(m -> Boolean.TRUE.equals(m.isRealEntity()))
                    .filter(m -> m.resolvedName() != null && !m.resolvedName().isBlank())
                    .map(m -> new ResumeMention(m.resolvedName().trim(), m.evidenceSnippet()))
                    .toList();
        } catch (Exception e) {
            // AI 不可用/超时/解析失败：返回 null 触发调用方回退正则，不劣化、不阻断
            log.debug("AI 语义提名失败，回退正则路径 err={}", e.getMessage());
            return null;
        }
    }

    /** AI 结构化输出：实体提名列表。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MentionExtractionResult(@JsonProperty("mentions") List<MentionDto> mentions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MentionDto(
            @JsonProperty("isRealEntity") Boolean isRealEntity,
            @JsonProperty("resolvedName") String resolvedName,
            @JsonProperty("evidenceSnippet") String evidenceSnippet,
            @JsonProperty("confidence") String confidence,
            @JsonProperty("reason") String reason) {}
}
