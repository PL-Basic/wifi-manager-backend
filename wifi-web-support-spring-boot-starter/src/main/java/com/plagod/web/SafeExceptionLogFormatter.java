package com.plagod.web;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 将异常渲染为不含异常消息的有界日志文本。
 */
public final class SafeExceptionLogFormatter {

    static final int MAX_CAUSE_DEPTH = 8;
    static final int MAX_FRAMES_PER_CAUSE = 12;
    private static final int MAX_FRAME_PART_LENGTH = 160;

    private SafeExceptionLogFormatter() {
    }

    public static String format(Throwable throwable) {
        if (throwable == null) {
            return "";
        }

        StringBuilder result = new StringBuilder(1024);
        Map<Throwable, Boolean> visited = new IdentityHashMap<>();
        Throwable current = throwable;
        int depth = 0;

        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (visited.put(current, Boolean.TRUE) != null) {
                appendCausePrefix(result, depth);
                result.append("[circular cause: ")
                        .append(current.getClass().getName())
                        .append(']');
                return result.toString();
            }

            appendCausePrefix(result, depth);
            result.append(current.getClass().getName());
            appendFrames(result, safeStackTrace(current, result));

            current = safeCause(current, result);
            depth++;
        }

        if (current != null) {
            result.append(System.lineSeparator())
                    .append("Caused by: [cause depth limit reached: ")
                    .append(current.getClass().getName())
                    .append(']');
        }
        return result.toString();
    }

    private static StackTraceElement[] safeStackTrace(
            Throwable throwable,
            StringBuilder result) {
        try {
            return throwable.getStackTrace();
        } catch (Throwable accessorFailure) {
            result.append(System.lineSeparator())
                    .append("\tat [stack trace unavailable]");
            return null;
        }
    }

    private static Throwable safeCause(
            Throwable throwable,
            StringBuilder result) {
        try {
            return throwable.getCause();
        } catch (Throwable accessorFailure) {
            result.append(System.lineSeparator())
                    .append("Caused by: [cause unavailable]");
            return null;
        }
    }

    private static void appendCausePrefix(StringBuilder result, int depth) {
        if (depth > 0) {
            result.append(System.lineSeparator()).append("Caused by: ");
        }
    }

    private static void appendFrames(
            StringBuilder result,
            StackTraceElement[] frames) {
        if (frames == null || frames.length == 0) {
            return;
        }

        int rendered = Math.min(frames.length, MAX_FRAMES_PER_CAUSE);
        for (int index = 0; index < rendered; index++) {
            result.append(System.lineSeparator())
                    .append("\tat ")
                    .append(renderFrame(frames[index]));
        }
        if (frames.length > rendered) {
            result.append(System.lineSeparator())
                    .append("\t... ")
                    .append(frames.length - rendered)
                    .append(" frames omitted");
        }
    }

    private static String renderFrame(StackTraceElement frame) {
        if (frame == null) {
            return "<unknown>";
        }

        StringBuilder rendered = new StringBuilder(128);
        rendered.append(safeFramePart(frame.getClassName()))
                .append('.')
                .append(safeFramePart(frame.getMethodName()))
                .append('(');
        if (frame.isNativeMethod()) {
            rendered.append("Native Method");
        } else if (frame.getFileName() == null) {
            rendered.append("Unknown Source");
        } else {
            rendered.append(safeFramePart(frame.getFileName()));
            if (frame.getLineNumber() >= 0) {
                rendered.append(':').append(frame.getLineNumber());
            }
        }
        return rendered.append(')').toString();
    }

    private static String safeFramePart(String value) {
        if (value == null || value.isEmpty()) {
            return "<unknown>";
        }

        int length = Math.min(value.length(), MAX_FRAME_PART_LENGTH);
        StringBuilder safe = new StringBuilder(length);
        for (int index = 0; index < length; ) {
            int current = value.codePointAt(index);
            int characterCount = Character.charCount(current);
            if (index + characterCount > length) {
                break;
            }
            int type = Character.getType(current);
            boolean unsafe = Character.isISOControl(current)
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR
                    || type == Character.FORMAT
                    || (current >= 0x2066 && current <= 0x2069);
            if (unsafe) {
                safe.append('_');
            } else {
                safe.appendCodePoint(current);
            }
            index += characterCount;
        }
        return safe.toString();
    }
}
