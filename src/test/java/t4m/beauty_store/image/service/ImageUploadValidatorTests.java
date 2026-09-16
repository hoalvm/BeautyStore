package t4m.beauty_store.image.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageUploadValidatorTests {

    private final ImageUploadValidator validator =
            new ImageUploadValidator(10 * 1024 * 1024, 40_000_000, 12_000);

    @Test
    void acceptsDecodedPngBytes() throws Exception {
        byte[] png = pngBytes();
        MockMultipartFile file = new MockMultipartFile(
                "image", "product.png", "image/png", png);

        ImageUploadValidator.ValidatedImage result = validator.validate(file);

        assertThat(result.format()).isEqualTo("png");
        assertThat(result.bytes()).isEqualTo(png);
    }

    @Test
    void rejectsContentTypeThatDoesNotMatchMagicBytes() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "image", "product.jpg", "image/jpeg", pngBytes());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAFileThatOnlyClaimsToBeAnImage() {
        MockMultipartFile file = new MockMultipartFile(
                "image", "payload.png", "image/png", "not an image".getBytes());

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsWebpOnlyAfterARealDecode() throws Exception {
        byte[] webp = Base64.getDecoder().decode(
            "UklGRhwAAABXRUJQVlA4TA8AAAAvAUAAAAcQ9Y/+ByKi/wEA");
        MockMultipartFile file = new MockMultipartFile(
            "image", "proof.webp", "image/webp", webp);

        assertThat(validator.validate(file).format()).isEqualTo("webp");
    }

    @Test
    void rejectsWebpContainerWithForgedDimensionsButNoDecodablePixels() {
        byte[] forged = new byte[30];
        System.arraycopy("RIFF".getBytes(), 0, forged, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, forged, 8, 4);
        System.arraycopy("VP8X".getBytes(), 0, forged, 12, 4);
        MockMultipartFile file = new MockMultipartFile(
            "image", "forged.webp", "image/webp", forged);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("giải mã");
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
