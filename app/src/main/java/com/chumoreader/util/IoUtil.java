package com.chumoreader.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;

/** 文件读写与文本编码判定。 */
public final class IoUtil {

    private IoUtil() {
    }

    public static byte[] readBytes(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            return readBytes(in, (int) Math.max(1024, f.length()));
        } finally {
            close(in);
        }
    }

    public static byte[] readBytes(InputStream in, int hint) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(1024, hint));
        byte[] buf = new byte[128 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    public static String readText(File f, String charsetName) throws IOException {
        return new String(readBytes(f), Charset.forName(charsetName));
    }

    /**
     * 判定文本编码：BOM 优先，其次严格 UTF-8 校验，最后回落到 GB18030（覆盖 GBK/GB2312）。
     * 中文小说里这三类覆盖 99%。
     */
    public static String detectCharset(byte[] data) {
        if (data == null || data.length == 0) {
            return "UTF-8";
        }
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            return "UTF-8";
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            return "UTF-16LE";
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            return "UTF-16BE";
        }
        int sample = Math.min(data.length, 200 * 1024);
        if (looksLikeUtf8(data, sample)) {
            return "UTF-8";
        }
        return "GB18030";
    }

    private static boolean looksLikeUtf8(byte[] data, int len) {
        try {
            CharsetDecoder dec = Charset.forName("UTF-8").newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            dec.decode(ByteBuffer.wrap(data, 0, len));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String decodeText(byte[] data) {
        String cs = detectCharset(data);
        String text = new String(data, Charset.forName(cs));
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return text;
    }

    public static void writeBytes(File f, byte[] data) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(data);
            out.flush();
        } finally {
            close(out);
        }
    }

    public static void copy(InputStream in, File dest) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        OutputStream out = new FileOutputStream(dest);
        try {
            byte[] buf = new byte[128 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            close(out);
        }
    }

    public static void close(Object c) {
        try {
            if (c instanceof java.io.Closeable) {
                ((java.io.Closeable) c).close();
            }
        } catch (Exception ignored) {
        }
    }

    public static void delete(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    delete(k);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    public static String readAssetOrFile(File f) {
        try {
            return readText(f, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
