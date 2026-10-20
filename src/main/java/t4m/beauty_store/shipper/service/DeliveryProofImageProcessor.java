package t4m.beauty_store.shipper.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import t4m.beauty_store.image.service.ImageUploadValidator;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Re-encodes pixels only: original names, EXIF/GPS and other metadata are not retained. */
@Component
@RequiredArgsConstructor
public class DeliveryProofImageProcessor {
    public static final long MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    private static final int MAX_EDGE = 1600;
    private final ImageUploadValidator validator;

    public byte[] prepare(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chụp hoặc chọn ảnh giao hàng");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Ảnh giao hàng không được vượt quá 5 MB");
        }
        var validated = validator.validate(file);
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(validated.bytes()));
        if (source == null) throw new IllegalArgumentException("Không thể giải mã ảnh giao hàng");
        double scale = Math.min(1d, (double) MAX_EDGE / Math.max(source.getWidth(), source.getHeight()));
        BufferedImage normalized = new BufferedImage(
            Math.max(1, (int) (source.getWidth() * scale)),
            Math.max(1, (int) (source.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
        var graphics = normalized.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, normalized.getWidth(), normalized.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, normalized.getWidth(), normalized.getHeight(), null);
        } finally {
            graphics.dispose();
            source.flush();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(normalized, "jpeg", output)) throw new IOException("JPEG encoder unavailable");
            return output.toByteArray();
        } finally {
            normalized.flush();
        }
    }
}
