package com.cryptopus.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * QR-code generation helper backed by ZXing.
 *
 * <p>Returns a JavaFX {@link Image} suitable for direct binding to an
 * {@code ImageView}. The bitmap is rendered with an opaque white background
 * and pure-black modules so authenticator-app cameras get the highest
 * contrast regardless of the surrounding card color.</p>
 */
public final class QrCodeGenerator {

    private QrCodeGenerator() {
    }

    /**
     * Encodes {@code content} as a QR code at the given pixel dimensions.
     *
     * @throws IllegalArgumentException if {@code content} is null or blank.
     * @throws IllegalStateException    if encoding fails for any reason.
     */
    public static Image toImage(String content, int width, int height) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("QR content must not be null or blank.");
        }

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        try {
            BitMatrix matrix = new QRCodeWriter()
                    .encode(content, BarcodeFormat.QR_CODE, width, height, hints);
            BufferedImage buffered = toBufferedImage(matrix);
            return SwingFXUtils.toFXImage(buffered, null);
        } catch (WriterException e) {
            throw new IllegalStateException("Failed to encode QR code.", e);
        }
    }

    private static BufferedImage toBufferedImage(BitMatrix matrix) {
        int w = matrix.getWidth();
        int h = matrix.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int black = Color.BLACK.getRGB();
        int white = Color.WHITE.getRGB();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, matrix.get(x, y) ? black : white);
            }
        }
        return img;
    }
}
