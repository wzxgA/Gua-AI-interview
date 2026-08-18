package com.aims.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** {@link CompanyNameExtractor} 单元测试（F5）：四类句式抽取 + 泛化词过滤 + 无公司名返回 null。 */
class CompanyNameExtractorTest {

    @Test
    void extract_quotedPattern() {
        assertEquals("阿里巴巴", CompanyNameExtractor.extract("我在「阿里巴巴」负责电商中台"));
        assertEquals("字节跳动", CompanyNameExtractor.extract("曾在《字节跳动》担任后端工程师"));
    }

    @Test
    void extract_rolePattern() {
        assertEquals("阿里巴巴", CompanyNameExtractor.extract("我在阿里巴巴担任技术负责人"));
        assertEquals("腾讯", CompanyNameExtractor.extract("在腾讯负责支付系统"));
        assertEquals("字节跳动", CompanyNameExtractor.extract("我在字节跳动做过订单系统"));
    }

    @Test
    void extract_joinPattern() {
        assertEquals("阿里巴巴", CompanyNameExtractor.extract("曾就职于阿里巴巴"));
        assertEquals("腾讯科技", CompanyNameExtractor.extract("后来入职腾讯科技"));
        assertEquals("字节跳动", CompanyNameExtractor.extract("我加入字节跳动后负责推荐"));
    }

    @Test
    void extract_suffixPattern() {
        assertEquals("腾讯科技有限公司", CompanyNameExtractor.extract("在腾讯科技有限公司担任工程师"));
    }

    @Test
    void extract_stopWords_filtered() {
        assertNull(CompanyNameExtractor.extract("我在简历上写的经历都在这里"));
        assertNull(CompanyNameExtractor.extract("上家公司的项目做得不错"));
        assertNull(CompanyNameExtractor.extract("公司组织架构调整过"));
    }

    @Test
    void extract_noCompany_returnsNull() {
        assertNull(CompanyNameExtractor.extract("我对微服务架构很有心得，做过分布式系统"));
        assertNull(CompanyNameExtractor.extract(""));
        assertNull(CompanyNameExtractor.extract(null));
    }
}
