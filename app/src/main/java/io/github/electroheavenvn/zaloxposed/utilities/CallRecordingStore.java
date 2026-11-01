package io.github.electroheavenvn.zaloxposed.utilities;

import android.content.Context;

import java.io.File;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Storage and formatting helpers for call recordings.
 *
 * <p>Recordings live in the host app's app-specific data directory, so the
 * app needs no storage permission and the files are removed together with the
 * app's data. The public location is
 * {@code Android/data/<host-package>/files/ZaloXposed/CallRecordings}; if
 * external storage is unavailable the internal files directory is used
 * instead.</p>
 */
public final class CallRecordingStore
{
    private static final String FOLDER = "ZaloXposed/CallRecordings";
    private static final String EXTENSION = ".wav";
    private static final Pattern NAME_PATTERN = Pattern.compile(
        "^zalo-call-(\\d+)-(incoming|outgoing|unknown)-([0-9a-fA-F]+)\\.wav$");

    private CallRecordingStore() { }

    public static File directory(Context context)
    {
        File root = context.getExternalFilesDir(null);
        if (root == null)
            root = context.getFilesDir();
        File directory = new File(root, FOLDER);
        if (!directory.exists())
            directory.mkdirs();
        return directory;
    }

    public static File newRecordingFile(Context context, String direction, long peerHandle)
    {
        long timestamp = System.currentTimeMillis();
        String name = "zalo-call-" + timestamp
            + "-" + HookUtils.safeName(direction)
            + "-" + Long.toHexString(peerHandle)
            + EXTENSION;
        return new File(directory(context), name);
    }

    public static List<File> list(Context context)
    {
        File[] files = directory(context).listFiles((dir, name) ->
            name != null && name.toLowerCase(Locale.US).endsWith(EXTENSION));
        if (files == null || files.length == 0)
            return Collections.emptyList();
        List<File> result = new ArrayList<>(files.length);
        Collections.addAll(result, files);
        result.sort((left, right) ->
            Long.compare(right.lastModified(), left.lastModified()));
        return result;
    }

    public static String displayTime(File file)
    {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
            .format(new Date(timestampOf(file)));
    }

    public static String displayDirection(File file)
    {
        String direction = directionOf(file);
        boolean english = Utils.IsEnglish();
        if ("incoming".equals(direction))
            return english ? "Incoming" : "Gọi đến";
        if ("outgoing".equals(direction))
            return english ? "Outgoing" : "Gọi đi";
        return english ? "Call" : "Cuộc gọi";
    }

    public static String displayTitle(File file)
    {
        return displayTime(file) + " · " + displayDirection(file);
    }

    public static String displayDetails(File file)
    {
        String duration = formatDuration(file);
        String size = formatSize(file.length());
        return duration.isEmpty() ? size : duration + " · " + size;
    }

    public static String displayMeta(File file)
    {
        String duration = formatDuration(file);
        String size = formatSize(file.length());
        String direction = displayDirection(file);
        return duration.isEmpty()
            ? direction + " · " + size
            : direction + " · " + duration + " · " + size;
    }

    private static String directionOf(File file)
    {
        Matcher matcher = NAME_PATTERN.matcher(file.getName());
        return matcher.matches() ? matcher.group(2) : "unknown";
    }

    private static long timestampOf(File file)
    {
        Matcher matcher = NAME_PATTERN.matcher(file.getName());
        if (matcher.matches())
        {
            try
            {
                return Long.parseLong(matcher.group(1));
            }
            catch (NumberFormatException ignored) { }
        }
        return file.lastModified();
    }

    public static String formatSize(long bytes)
    {
        if (bytes < 1024L)
            return bytes + " B";
        double kilobytes = bytes / 1024.0;
        if (kilobytes < 1024.0)
            return String.format(Locale.US, "%.0f KB", kilobytes);
        return String.format(Locale.US, "%.1f MB", kilobytes / 1024.0);
    }

    /**
     * Reads the WAV byte rate and data size. Native recordings have their header
     * repaired when the call ends, so this is cheap and does not need a decoder.
     */
    public static String formatDuration(File file)
    {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"))
        {
            if (raf.length() < 44L)
                return "";
            byte[] header = new byte[44];
            raf.readFully(header);
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F'
                || header[8] != 'W' || header[9] != 'A' || header[10] != 'V' || header[11] != 'E')
                return "";
            int byteRate = littleEndianInt(header, 28);
            int dataSize = littleEndianInt(header, 40);
            if (byteRate <= 0 || dataSize <= 0)
                return "";
            long seconds = (dataSize * 1000L / byteRate) / 1000L;
            long minutes = seconds / 60L;
            long remainder = seconds % 60L;
            return String.format(Locale.US, "%d:%02d", minutes, remainder);
        }
        catch (Throwable ignored)
        {
            return "";
        }
    }

    private static int littleEndianInt(byte[] bytes, int offset)
    {
        return (bytes[offset] & 0xff)
            | ((bytes[offset + 1] & 0xff) << 8)
            | ((bytes[offset + 2] & 0xff) << 16)
            | ((bytes[offset + 3] & 0xff) << 24);
    }
}
