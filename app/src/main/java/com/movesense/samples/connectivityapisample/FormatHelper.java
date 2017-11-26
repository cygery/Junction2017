package com.movesense.samples.connectivityapisample;

/**
 * Helper class for formatting Contract and Path
 */
public class FormatHelper {
    public static final String SCHEME_PREFIX = "suunto://";

    private FormatHelper() {}

    public static String formatContractToJson(String serial, String uri) {
        StringBuilder sb = new StringBuilder();
        return sb.append("{\"Uri\": \"").append(serial).append("/").append(uri).append("\"}").toString();
    }

    public static String pathFormatHelper(String serial, String path) {
        final StringBuilder sb = new StringBuilder();
        return sb.append(SCHEME_PREFIX).append(serial).append("/").append(path).toString();
    }
}
