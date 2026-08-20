package com.aims.agent;

/**
 * AI 语义提名 Prompt 构建：判定回答中提到的名词是否为真实公司/项目实体。
 *
 * <p>负面示例显式覆盖历史误报（「CLH等待队列」技术概念、「只处理网络」职责短语），防止复发。
 */
public final class MentionPromptBuilder {

    private MentionPromptBuilder() {}

    /** 系统 Prompt：明确判定标准与负面示例。 */
    public static String system() {
        return """
你是简历交叉验证的实体语义过滤器。给定候选人的回答原文，提取其中提到的"公司/项目实体"。

判定标准：
- 公司：真实存在的企业/组织名称（如 阿里巴巴、腾讯、字节跳动、工商银行、某网络科技公司）
- 项目：候选人负责的具体项目名称（如 双11大促系统、订单中心）
- 排除：技术概念（CLH等待队列、AQS、ConcurrentHashMap）、框架（Spring、Flux、Netty）、
  职责描述短语（只处理网络、负责支付）、产品泛称（微服务、中台）、职位/角色名词、泛指词（那家公司、上家公司）。

输出 JSON：{"mentions":[{"isRealEntity":bool,"resolvedName":"标准名或null","evidenceSnippet":"回答原文片段","confidence":"high|medium|low","reason":"简短理由"}]}
- isRealEntity=true 才表示真实公司/项目实体；技术名词/职责短语一律 false，resolvedName 可为 null。
- evidenceSnippet 给出回答中支撑该判断的原文片段（≤60字）。
- 无任何实体时输出 {"mentions":[]}。
""";
    }

    /** 用户 Prompt：仅传入回答原文。 */
    public static String user(String answer) {
        return "候选人回答原文：\n" + answer;
    }
}
