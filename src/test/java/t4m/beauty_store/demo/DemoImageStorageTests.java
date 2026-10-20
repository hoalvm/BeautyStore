package t4m.beauty_store.demo;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import t4m.beauty_store.image.service.ImageUploadValidator;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DemoImageStorageTests {
    @TempDir Path directory;

    @Test
    void validatesStoresAndDeletesOnlyOwnedLocalImages() throws Exception {
        var storage = storage();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var upload = storage.uploadEvidenceImage(new MockMultipartFile(
                "file", "../../untrusted.png", "image/png", bytes.toByteArray()));
        assertThat(upload.get("url")).startsWith("/demo/media/").endsWith(".png");
        String filename = upload.get("publicId").substring(5);
        assertThat(Files.readAllBytes(storage.resolve(filename))).isEqualTo(bytes.toByteArray());
        assertThat(storage.getThumbnailUrl(upload.get("publicId"))).isEqualTo(upload.get("url"));
        assertThat(storage.deleteImage("../../pom.xml")).isFalse();
        assertThat(storage.deleteImage("demo/../../pom.xml")).isFalse();
        assertThat(storage.deleteImage(upload.get("publicId"))).isTrue();
        assertThat(Files.exists(storage.resolve(filename))).isFalse();
        assertThat(storage.deleteImage(upload.get("publicId"))).isTrue();
    }

    @Test
    void refusesSpoofedUploadsAndTraversal() {
        var storage = storage();
        assertThatThrownBy(() -> storage.uploadImage(new MockMultipartFile(
                "file", "photo.png", "image/png", "<script>bad</script>".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.resolve("../pom.xml")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.resolve("00000000-0000-0000-0000-000000000000.svg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DemoImageStorage storage() {
        return new DemoImageStorage(mock(Cloudinary.class),
                new ImageUploadValidator(10_485_760, 40_000_000, 12_000), directory);
    }
}
