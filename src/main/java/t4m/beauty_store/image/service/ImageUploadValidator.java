package t4m.beauty_store.image.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/** Validates image bytes independently of the user-controlled filename. */
@Component
public class ImageUploadValidator {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp");

    private final long maxFileSize;
    private final long maxPixels;
    private final int maxDimension;

    public ImageUploadValidator(
            @Value("${cloudinary.max-file-size-bytes:10485760}") long maxFileSize,
            @Value("${cloudinary.max-image-pixels:40000000}") long maxPixels,
            @Value("${cloudinary.max-image-dimension:12000}") int maxDimension) {
        this.maxFileSize = maxFileSize;
        this.maxPixels = maxPixels;
        this.maxDimension = maxDimension;
    }

    public ValidatedImage validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn ảnh để tải lên");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("Ảnh không được vượt quá 10 MB");
        }
        byte[] bytes = file.getBytes();
        String format = detectFormat(bytes);
        String declaredContentType = file.getContentType();
        if (declaredContentType == null
                || !CONTENT_TYPES.get(format).equals(declaredContentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Định dạng ảnh khai báo không khớp nội dung tệp");
        }
        validateDimensionsAndDecode(bytes);
        return new ValidatedImage(bytes, format);
    }

    public ValidatedImage validate(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Tệp ảnh trống");
        }
        if (bytes.length > maxFileSize) {
            throw new IllegalArgumentException("Ảnh không được vượt quá 10 MB");
        }
        String format = detectFormat(bytes);
        validateDimensionsAndDecode(bytes);
        return new ValidatedImage(bytes, format);
    }

    private String detectFormat(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "jpeg";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return "png";
        }
        if (bytes.length >= 30
                && asciiEquals(bytes, 0, "RIFF")
                && asciiEquals(bytes, 8, "WEBP")) {
            return "webp";
        }
        throw new IllegalArgumentException("Chỉ hỗ trợ ảnh JPEG, PNG hoặc WebP hợp lệ");
    }

    private void validateDimensionsAndDecode(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IllegalArgumentException("Không thể đọc ảnh");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("Nội dung tệp không phải ảnh hợp lệ");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
                    throw new IllegalArgumentException("Không thể giải mã ảnh");
                }
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Không thể giải mã ảnh", exception);
        }
    }

    private int[] readWebpDimensions(byte[] bytes) {
        long declaredRiffSize = uint32Le(bytes, 4) + 8L;
        if (declaredRiffSize > bytes.length || declaredRiffSize < 30) {
            throw new IllegalArgumentException("Tệp WebP không hoàn chỉnh");
        }

        if (asciiEquals(bytes, 12, "VP8X")) {
            int width = 1 + uint24Le(bytes, 24);
            int height = 1 + uint24Le(bytes, 27);
            return new int[]{width, height};
        }
        if (asciiEquals(bytes, 12, "VP8L") && (bytes[20] & 0xff) == 0x2f) {
            int bits = (bytes[21] & 0xff)
                    | ((bytes[22] & 0xff) << 8)
                    | ((bytes[23] & 0xff) << 16)
                    | ((bytes[24] & 0xff) << 24);
            int width = (bits & 0x3fff) + 1;
            int height = ((bits >>> 14) & 0x3fff) + 1;
            return new int[]{width, height};
        }
        if (asciiEquals(bytes, 12, "VP8 ")
                && bytes.length >= 30
                && (bytes[23] & 0xff) == 0x9d
                && (bytes[24] & 0xff) == 0x01
                && (bytes[25] & 0xff) == 0x2a) {
            int width = ((bytes[26] & 0xff) | ((bytes[27] & 0xff) << 8)) & 0x3fff;
            int height = ((bytes[28] & 0xff) | ((bytes[29] & 0xff) << 8)) & 0x3fff;
            return new int[]{width, height};
        }
        throw new IllegalArgumentException("Cấu trúc WebP không hợp lệ");
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > maxDimension || height > maxDimension
                || (long) width * height > maxPixels) {
            throw new IllegalArgumentException("Kích thước ảnh không hợp lệ hoặc quá lớn");
        }
    }

    private boolean asciiEquals(byte[] bytes, int offset, String value) {
        if (offset < 0 || bytes.length < offset + value.length()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if ((byte) value.charAt(i) != bytes[offset + i]) {
                return false;
            }
        }
        return true;
    }

    private long uint32Le(byte[] bytes, int offset) {
        return ((long) bytes[offset] & 0xff)
                | (((long) bytes[offset + 1] & 0xff) << 8)
                | (((long) bytes[offset + 2] & 0xff) << 16)
                | (((long) bytes[offset + 3] & 0xff) << 24);
    }

    private int uint24Le(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16);
    }

    public record ValidatedImage(byte[] bytes, String format) {
    }
}
