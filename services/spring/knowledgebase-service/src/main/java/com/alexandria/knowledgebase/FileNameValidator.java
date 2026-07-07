package com.alexandria.knowledgebase;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Guardrail checks for user-supplied file names.
 * <p>
 * Rejects inputs that could enable path traversal, null-byte injection, header/HTML injection or
 * collisions with reserved OS names.
 * <p>
 * Forbidden chars based partly on https://www.baeldung.com/java-validate-filename
 * Reserved windows file names based on https://www.helpndoc.com/documentation/html/Windowsreservedfilenames.html
 */
public final class FileNameValidator {

    public static final int MAX_LENGTH = 255;
    private static final Pattern FORBIDDEN_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F/\\\\<>:\"|?*]");
    private static final Set<String> RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private FileNameValidator() {
    }

    /**
     * Validates the given file name.
     *
     * @param fileName candidate file name (must not be {@code null})
     * @throws IllegalArgumentException if the file name is missing, oversized, or contains unsafe
     *                                  content
     */
    public static void validate(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("File name must not be null");
        }
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("File name must not be blank");
        }
        if (fileName.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "File name must not exceed " + MAX_LENGTH + " characters");
        }
        if (!fileName.equals(fileName.strip())) {
            throw new IllegalArgumentException(
                    "File name must not have leading or trailing whitespace");
        }
        if (fileName.endsWith(".")) {
            throw new IllegalArgumentException("File name must not end with a dot");
        }
        if (fileName.equals(".") || fileName.equals("..")) {
            throw new IllegalArgumentException("File name must not be relative path");
        }
        if (FORBIDDEN_CHARS.matcher(fileName).find()) {
            throw new IllegalArgumentException(
                    "File name contains forbidden characters" + " (control chars, path separators, or <>:\"|?*)");
        }
        String baseName = fileName;
        int dot = fileName.indexOf('.');
        if (dot > 0) {
            baseName = fileName.substring(0, dot);
        }
        if (RESERVED_NAMES.contains(baseName.toUpperCase())) {
            throw new IllegalArgumentException(
                    "File name uses a reserved system name: " + baseName);
        }
    }
}
