package com.stugger.logviewer.api.model;

/**
 * Response model containing estimated remote log export information.
 *
 * @param fileCount number of matching log files
 * @param totalBytes combined size of matching files in bytes
 *
 * @author Jake
 * @since May 8th, 2026
 */
public record RemoteLogExportEstimate(
        int fileCount,
        long totalBytes
) {}
