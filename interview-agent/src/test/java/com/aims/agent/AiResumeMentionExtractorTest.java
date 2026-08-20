package com.aims.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aims.agent.AiResumeMentionExtractor.MentionDto;
import com.aims.agent.AiResumeMentionExtractor.MentionExtractionResult;
import com.aims.ai.facade.AiChatFacade;
import com.aims.ai.router.ModelTier;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** {@link AiResumeMentionExtractor} 单元测试：真实体保留 / 非真实体过滤（CLH等待队列、只处理网络）/ 失败回退 null。 */
class AiResumeMentionExtractorTest {

    private AiChatFacade aiChatFacade;
    private AiResumeMentionExtractor extractor;

    @BeforeEach
    void setUp() {
        aiChatFacade = mock(AiChatFacade.class);
        extractor = new AiResumeMentionExtractor(aiChatFacade);
    }

    @Test
    void extract_keepsOnlyRealEntities() {
        MentionExtractionResult result =
                new MentionExtractionResult(
                        List.of(
                                new MentionDto(true, "阿里巴巴", "在阿里巴巴负责中台", "high", "真实公司"),
                                new MentionDto(false, "CLH等待队列", "用「CLH等待队列」解决并发", "high", "技术概念"),
                                new MentionDto(false, "只处理网络", "只处理网络相关的活", "high", "职责短语")));
        when(aiChatFacade.callForEntity(
                        eq(ModelTier.STANDARD), any(), any(), eq(MentionExtractionResult.class)))
                .thenReturn(result);

        List<ResumeEntityMentionExtractor.ResumeMention> mentions = extractor.extract("回答");

        assertEquals(1, mentions.size());
        assertEquals("阿里巴巴", mentions.get(0).resolvedName());
    }

    @Test
    void extract_emptyResult_returnsEmpty() {
        when(aiChatFacade.callForEntity(
                        eq(ModelTier.STANDARD), any(), any(), eq(MentionExtractionResult.class)))
                .thenReturn(new MentionExtractionResult(List.of()));

        assertTrue(extractor.extract("回答").isEmpty());
    }

    @Test
    void extract_nullResult_returnsNullFallback() {
        when(aiChatFacade.callForEntity(
                        eq(ModelTier.STANDARD), any(), any(), eq(MentionExtractionResult.class)))
                .thenReturn(null);

        assertNull(extractor.extract("回答"));
    }

    @Test
    void extract_exception_returnsNullFallback() {
        when(aiChatFacade.callForEntity(
                        eq(ModelTier.STANDARD), any(), any(), eq(MentionExtractionResult.class)))
                .thenThrow(new RuntimeException("ai down"));

        assertNull(extractor.extract("回答"));
    }

    @Test
    void extract_blankAnswer_returnsNull() {
        assertNull(extractor.extract(""));
        assertNull(extractor.extract(null));
    }
}
