package com.example.yazhang.ai;

import android.content.Context;
import android.net.Uri;

import com.example.yazhang.ai.config.ImageTools;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Copies a user-picked image privately and derives the dedupe hash and bounded data URL. */
public final class ImageRecognizer {

    public static final class Captured {
        public final String sha256;
        public final String dataUrl;

        Captured(String sha256, String dataUrl) {
            this.sha256 = sha256;
            this.dataUrl = dataUrl;
        }
    }

    private ImageRecognizer() {
    }

    public static Captured capture(Context context, Uri source) throws Exception {
        if (source == null) {
            throw new IllegalArgumentException("没有选择图片");
        }
        Uri local = ImageTools.persist(context, source);
        String path = local.getPath();
        if (path == null) {
            throw new IllegalArgumentException("无法保存所选图片");
        }
        File file = new File(path);
        return new Captured(sha256(file), ImageTools.dataUrl(context, local));
    }

    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return hex.toString();
    }
}
