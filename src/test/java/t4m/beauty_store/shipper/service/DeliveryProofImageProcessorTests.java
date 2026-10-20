package t4m.beauty_store.shipper.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import t4m.beauty_store.image.service.ImageUploadValidator;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import static org.assertj.core.api.Assertions.*;

class DeliveryProofImageProcessorTests {
    private final DeliveryProofImageProcessor processor = new DeliveryProofImageProcessor(
        new ImageUploadValidator(10_485_760, 40_000_000, 12_000));

    @Test
    void resizesAndReencodesTransparentPngAsJpeg() throws Exception {
        byte[] bytes = processor.prepare(png(2400, 1200));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(bytes[0] & 0xff).isEqualTo(0xff);
        assertThat(bytes[1] & 0xff).isEqualTo(0xd8);
        assertThat(decoded.getWidth()).isEqualTo(1600);
        assertThat(decoded.getHeight()).isEqualTo(800);
        assertThat(decoded.getRGB(0, 0) & 0xffffff).isEqualTo(0xffffff);
        assertThat(bytes.length).isLessThan(1_000_000);
    }

    @Test
    void rejectsMissingOversizedAndDisguisedUploads() {
        assertThatIllegalArgumentException().isThrownBy(() -> processor.prepare(null));
        assertThatIllegalArgumentException().isThrownBy(() -> processor.prepare(
            new MockMultipartFile("image", "large.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1])));
        assertThatIllegalArgumentException().isThrownBy(() -> processor.prepare(
            new MockMultipartFile("image", "fake.png", "image/png", "<svg/>".getBytes())));
    }

    @Test
    void rejectsMimeMismatchAndKeepsSmallImagesSmall() throws Exception {
        var valid = png(12, 6);
        assertThatIllegalArgumentException().isThrownBy(() -> processor.prepare(
            new MockMultipartFile("image", "photo.jpg", "image/jpeg", valid.getBytes())));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(processor.prepare(valid)));
        assertThat(decoded.getWidth()).isEqualTo(12);
        assertThat(decoded.getHeight()).isEqualTo(6);
    }

    static MockMultipartFile png(int width, int height) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output);
        return new MockMultipartFile("image", "proof.png", "image/png", output.toByteArray());
    }
}
