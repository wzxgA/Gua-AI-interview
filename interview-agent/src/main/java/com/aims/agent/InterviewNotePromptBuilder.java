package com.aims.agent;

/**
 * 面经解析 Prompt 统一构建器：将非结构化的面经文本规范化为面试题目列表。
 *
 * <p>对齐 {@link ResumePromptBuilder} 的职责划分：QuestionServiceImpl 只负责流程编排，不承担 Prompt 拼接职责。
 */
public final class InterviewNotePromptBuilder {

    private static final String PARSE_SYSTEM =
            """
你是面试题目整理专家。请将用户粘贴的"面经"（面试经验分享文本）整理为规范化面试题目列表。

输出格式（只输出 JSON，不要 Markdown 代码块或额外说明）：
{"questions": [{"category": "...", "topic": "...", "difficulty": "...", "content": "...", "standardAnswer": "...", "tags": ["..."]}]}

字段说明：
- category: 题目分类，仅允许 TECHNICAL（技术类）/ BEHAVIORAL（行为类）/ PROJECT（项目类）
- topic: 主题，2-8 个字概括考察点，如 "Java 集合"、"Redis 缓存"、"项目难点"
- difficulty: 难度，仅允许 EASY / MEDIUM / HARD
- content: 题干，补全为可直接提问的完整问句；去除"第 2 轮第 3 题"等编号前缀
- standardAnswer: 参考答案，面经中若包含候选人回答或面试官讲解则提炼为要点；只有问题没有回答时留空字符串
- tags: 标签数组，2-5 个，包含技术名词或考察方向；无标签时为空数组

约束：
1. 只抽取真实的面试题目：过滤自我介绍、闲聊、寒暄、公司/面试官背景叙述、薪资与反问环节等非题目内容
2. 相同问题只保留一次，不得编造面经中不存在的题目
3. category / difficulty 必须严格取允许的枚举值，无法判断时 category 用 TECHNICAL、difficulty 用 MEDIUM
4. 保留原始技术名词，不做翻译或缩写
""";

    private InterviewNotePromptBuilder() {}

    /** 面经解析系统 Prompt。 */
    public static String parseSystem() {
        return PARSE_SYSTEM;
    }

    /** 面经解析用户 Prompt（原文 + 可选大方向提示）。 */
    public static String parseUser(String noteText, String categoryHint) {
        StringBuilder sb = new StringBuilder("请将以下面经文本整理为规范化面试题目：\n\n【面经原文】\n");
        sb.append(noteText).append('\n');
        if (categoryHint != null && !categoryHint.isBlank()) {
            sb.append("\n【大方向提示】").append(categoryHint.trim()).append('\n');
        }
        return sb.toString();
    }
}
