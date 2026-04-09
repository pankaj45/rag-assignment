package com.example.secfix.parser;

import com.example.secfix.exception.UnsupportedFileTypeException;

public final class XlsxFileSupport {

    private static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private XlsxFileSupport() {
    }

    public static boolean isXlsx(String contentType, String fileName) {
        boolean byName = fileName != null && fileName.toLowerCase().endsWith(".xlsx");
        boolean byMime = contentType != null && XLSX_MIME.equalsIgnoreCase(contentType);
        return byName || byMime;
    }

    public static void requireXlsx(String contentType, String fileName, String operation) {
        if (!isXlsx(contentType, fileName)) {
            throw new UnsupportedFileTypeException(
                    "Only .xlsx files are currently supported for " + operation + ".");
        }
    }
}
