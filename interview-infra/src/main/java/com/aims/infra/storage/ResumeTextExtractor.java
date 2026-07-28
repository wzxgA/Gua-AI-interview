package com.aims.infra.storage;

import com.aims.core.common.ErrorCode;
import com.aims.core.common.exception.BizException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * 简历文本抽取工具。
 *
 * <p>根据文件扩展名选择抽取方式：PDF 用 PDFBox 3.x（{@link Loader#loadPDF(byte[])} + {@link
 * PDFTextStripper#getText}），TXT 直接读取 UTF-8 文本。
 */
public final class ResumeTextExtractor {

    private ResumeTextExtractor() {}

    /**
     * 从输入流中抽取文本。
     *
     * @param inputStream 文件输入流
     * @param filename 原始文件名（用于判断扩展名）
     * @return 抽取出的纯文本
     * @throws BizException 不支持的文件类型或抽取失败时抛出 {@link ErrorCode#FILE_UPLOAD_FAILED}
     */
    public static String extract(InputStream inputStream, String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        try {
            if (lower.endsWith(".pdf")) {
                return extractPdf(inputStream);
            } else if (lower.endsWith(".txt")) {
                return extractText(inputStream);
            } else {
                throw new BizException(ErrorCode.FILE_UPLOAD_FAILED, "不支持的文件类型: " + filename);
            }
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
}
