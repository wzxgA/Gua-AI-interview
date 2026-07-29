package com.aims.infra.storage;

import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * 简历文本抽取工具。
 *
 * <p>根据文件扩展名选择抽取方式：PDF 用 PDFBox 3.x（{@link Loader#loadPDF(byte[])} + {@link
 * PDFTextStripper#getText}），TXT 直接读取 UTF-8 文本。抽取后会进行文本清洗：去除不可见字符、统一空白、去除页眉页脚噪声。
 */
public final class ResumeTextExtractor {

    /** BOM 和零宽字符等不可见字符。 */
    private static final Pattern INVISIBLE_CHARS =
            Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\uFEFF]");

    /** 连续 3 个以上的空行压缩为 2 个。 */
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\\n{3,}");

    /** 行首行尾多余空白。 */
    private static final Pattern LINE_TRIM = Pattern.compile("[ \\t]+\\n");

    private ResumeTextExtractor() {}

    /**
     * 从输入流中抽取文本。
     *
     * @param inputStream 文件输入流
     * @param filename 原始文件名（用于判断扩展名）
     * @return 抽取并清洗后的纯文本
     * @throws BizException 不支持的文件类型或抽取失败时抛出 {@link ErrorCode#FILE_UPLOAD_FAILED}
     */
    public static String extract(InputStream inputStream, String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        try {
            String raw;
            if (lower.endsWith(".pdf")) {
                raw = extractPdf(inputStream);
            } else if (lower.endsWith(".txt")) {
                raw = extractText(inputStream);
            } else {
                throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "不支持的文件类型: " + filename);
            }
            return clean(raw);
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "文件文本抽取失败: " + filename, e);
        }
    }

    /** PDF 文本抽取：PDFBox 3.x Loader.loadPDF + PDFTextStripper。 */
    private static String extractPdf(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    /** TXT 文本抽取：直接读取 UTF-8 文本。 */
    private static String extractText(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * 文本清洗：
     *
     * <ol>
     *   <li>去除不可见控制字符（保留 \n\r\t）
     *   <li>去除行尾多余空白
     *   <li>压缩 3 个以上连续空行为 2 个
     *   <li>首尾 trim
     * </ol>
     */
    private static String clean(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String result = INVISIBLE_CHARS.matcher(raw).replaceAll("");
        result = LINE_TRIM.matcher(result).replaceAll("\n");
        result = EXCESS_BLANK_LINES.matcher(result).replaceAll("\n\n");
        return result.strip();
    }
}
