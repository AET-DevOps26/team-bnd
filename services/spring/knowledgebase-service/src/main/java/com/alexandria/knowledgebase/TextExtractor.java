package com.alexandria.knowledgebase;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// inspired by https://medium.com/@georgeberar/springboot-extract-text-from-pdf-1d8d41b5adac

@Component
public class TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(TextExtractor.class);

    public String extract(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            return null;
        }

        try {
            String text = null;
            if (contentType.equals("application/pdf")) {
                text = extractFromPdf(file);
            } else if (contentType.startsWith("text/")) {
                text = new String(file.getBytes(), StandardCharsets.UTF_8);
            }
            return sanitizeText(text);
        } catch (IOException e) {
            String safeFilename = sanitizeForLog(file.getOriginalFilename());
            log.warn("Failed to extract text from file {}: {}", safeFilename, e.getMessage());
        }

        return null;
    }

    private String extractFromPdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String sanitizeText(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\u0000", "");
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return "null";
        }
        return value.replace('\r', '_').replace('\n', '_').replaceAll("\\p{Cntrl}", "_");
    }
}
