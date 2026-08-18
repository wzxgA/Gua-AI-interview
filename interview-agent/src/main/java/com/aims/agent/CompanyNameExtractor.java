package com.aims.agent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公司名提取器（v1.1-F5）：从候选人回答中规则提取公司名，供简历交叉验证定向比对"简历是否提及"。
 *
 * <p>纯正则、无 AI 调用；抽不到返回 null（调用方回退全文检索，行为不劣化）。覆盖四类句式：
 *
 * <ol>
 *   <li>引号句式：我在「阿里巴巴」负责…
 *   <li>任职句式：在阿里巴巴担任/负责/做过…
 *   <li>就职句式：曾就职于/入职/加入 X
 *   <li>词尾句式：腾讯科技有限公司（公司/集团/科技… 收尾）
 * </ol>
 */
public final class CompanyNameExtractor {

    /** 公司词尾（提取第 2/3/4 类句式时用于收尾判定）。 */
    private static final List<String> COMPANY_SUFFIXES =
            List.of(
                    "有限公司", "有限责任公司", "公司", "集团", "科技", "网络", "信息", "软件", "银行", "证券", "保险", "传媒",
                    "汽车");

    /** 泛化词（不作公司名）。 */
    private static final List<String> STOP_WORDS =
            List.of("简历", "上家公司", "该公司", "这家公司", "那家公司", "公司里", "公司内", "公司", "集团");

    private static final Pattern QUOTED = Pattern.compile("[「《]([^」》]{2,30})[」》]");
    private static final Pattern ROLE_PATTERN =
            Pattern.compile(
                    "(?:在|于)([\\p{L}\\p{N}·]{2,30}?(?:有限公司|集团|科技|网络|信息|软件|银行|证券|保险|传媒|汽车|公司)?)(?:担任|负责|做过|做|主导|参与|任职|工作|实习|带团队|负责过|搞过|从事)");
    private static final Pattern JOIN_PATTERN =
            Pattern.compile(
                    "(?:就职于|入职|供职|加入|曾在|之前在|以前在|待过|干过)([\\p{L}\\p{N}·]{2,30}?)(?=后|，|。|；|,|\\.|的|负责|做|担任|从事|工作|$)");

    private CompanyNameExtractor() {}

    /**
     * 从候选人回答中提取公司名；抽不到返回 null。
     *
     * @param answerText 候选人回答
     */
    public static String extract(String answerText) {
        if (answerText == null || answerText.isBlank()) {
            return null;
        }
        String text = answerText.trim();

        // 1. 引号句式优先：我在「X」…
        Matcher quoted = QUOTED.matcher(text);
        if (quoted.find()) {
            String name = sanitize(quoted.group(1));
            if (name != null) {
                return name;
            }
        }

        // 2. 任职句式：在 X 担任/负责…
        Matcher role = ROLE_PATTERN.matcher(text);
        if (role.find()) {
            String name = sanitize(role.group(1));
            if (name != null) {
                return name;
            }
        }

        // 3. 就职句式：就职于/入职/加入 X
        Matcher join = JOIN_PATTERN.matcher(text);
        if (join.find()) {
            String name = sanitize(join.group(1));
            if (name != null) {
                return name;
            }
        }

        // 4. 词尾句式：独立出现的"X公司/集团/科技…"（排除第 1~3 类已捕获）
        for (String suffix : COMPANY_SUFFIXES) {
            Matcher m = Pattern.compile("([\\p{L}\\p{N}·]{2,20}" + suffix + ")").matcher(text);
            if (m.find()) {
                String name = sanitize(m.group(1));
                if (name != null) {
                    return name;
                }
            }
        }

        return null;
    }

    /** 清洗候选名：剔除泛化词、过短片段；非法返回 null。 */
    private static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim();
        if (name.length() < 2) {
            return null;
        }
        if (STOP_WORDS.contains(name)) {
            return null;
        }
        return name;
    }
}
