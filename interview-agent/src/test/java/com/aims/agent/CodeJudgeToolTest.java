package com.aims.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link CodeJudgeTool} 单元测试：未配置沙箱返回明确错误，不抛异常。 */
class CodeJudgeToolTest {

    @Test
    void judge_withoutSandbox_returnsExplicitError() {
        CodeJudgeTool tool = new CodeJudgeTool();

        CodeJudgeResult result = tool.judge("java", "class A {}", List.of("assertEquals(1,1)"));

        assertFalse(result.passed());
        assertEquals("sandbox not configured", result.error());
    }
}
