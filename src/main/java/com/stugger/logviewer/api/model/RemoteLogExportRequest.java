package com.stugger.logviewer.api.model;

import java.util.List;

/**
 * Request model used for remote log export estimation and ZIP downloads.
 *
 * @param from inclusive start date in ISO-8601 format
 * @param to inclusive end date in ISO-8601 format
 * @param globalTypes selected global category/type paths
 * @param playerTypes selected player category/type paths
 * @param logFileNameFormat date format used by remote log file names
 * @param logFileExtension expected remote log file extension
 *
 * @author Jake
 * @since May 6th, 2026
 */
public record RemoteLogExportRequest(
        String from,
        String to,
        List<String> globalTypes,
        List<String> playerTypes,
        String logFileNameFormat,
        String logFileExtension) {
}