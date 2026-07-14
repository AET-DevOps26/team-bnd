package com.alexandria.knowledgebase;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TextExtractorTest {

    private final TextExtractor extractor = new TextExtractor();

    @Test
    void unit_kb_extractReturnsNullWhenContentTypeMissing() {
        MultipartFile file = new MockMultipartFile("f", "a.bin", null, "hi".getBytes());
        assertThat(extractor.extract(file)).isNull();
    }

    @Test
    void unit_kb_extractReadsPlainText() {
        MultipartFile file = new MockMultipartFile("f", "a.txt", "text/plain", "hello world".getBytes());
        assertThat(extractor.extract(file)).isEqualTo("hello world");
    }

    @Test
    void unit_kb_extractStripsNullBytes() {
        MultipartFile file = new MockMultipartFile("f", "a.txt", "text/plain", "a\u0000b".getBytes());
        assertThat(extractor.extract(file)).isEqualTo("ab");
    }

    @Test
    void unit_kb_extractReturnsNullForUnsupportedType() {
        MultipartFile file = new MockMultipartFile("f", "a.png", "image/png", "hi".getBytes());
        assertThat(extractor.extract(file)).isNull();
    }

    @Test
    void unit_kb_extractReadsPdfText() throws IOException {
        byte[] pdf = pdfWithText("Alexandria");
        MultipartFile file = new MockMultipartFile("f", "a.pdf", "application/pdf", pdf);
        assertThat(extractor.extract(file)).contains("Alexandria");
    }

    @Test
    void unit_kb_extractReturnsNullOnUnreadablePdf() {
        MultipartFile file = new MockMultipartFile("f", "a.pdf", "application/pdf", "not a pdf".getBytes());
        assertThat(extractor.extract(file)).isNull();
    }

    private static byte[] pdfWithText(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(100, 700);
                content.showText(text);
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
