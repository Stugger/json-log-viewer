package com.stugger.logviewer.data;

import com.stugger.logviewer.model.LogQuery;
import com.stugger.logviewer.model.LogRecord;

import java.util.stream.Stream;

/**
 * Abstraction over a source of log records.
 * <p>
 * Implementations produce a {@link Stream} of {@link LogRecord}s matching a {@link LogQuery}.
 * The returned stream may be backed by IO; callers should close it (e.g., try-with-resources)
 * when materializing or consuming results.
 *
 * @author Jake
 * @since January 24, 2026
 */
public interface LogSource {

    Stream<LogRecord> stream(LogQuery query);

}
