package t4m.beauty_store.demo;

import com.cloudinary.Cloudinary;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import t4m.beauty_store.image.service.ImageUploadValidator;
import t4m.beauty_store.product.service.CloudinaryService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;

/** Same validated upload contract as production, with no external credentials or network calls. */
@Service
@Primary
@Profile("demo")
public class DemoImageStorage extends CloudinaryService {
    private final ImageUploadValidator validator;
    private final Path directory;

    @org.springframework.beans.factory.annotation.Autowired
    public DemoImageStorage(Cloudinary cloudinary, ImageUploadValidator validator) {
        this(cloudinary, validator, Path.of(".beautystore", "uploads"));
    }

    DemoImageStorage(Cloudinary cloudinary, ImageUploadValidator validator, Path directory) {
        super(cloudinary, validator);
        this.validator = validator;
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override
    public Map<String, String> uploadImage(MultipartFile file) throws IOException {
        var validated = validator.validate(file);
        rejectSymlinkAncestors();
        Files.createDirectories(directory);
        String filename = UUID.randomUUID() + "." + validated.format();
        Files.write(resolve(filename), validated.bytes(), StandardOpenOption.CREATE_NEW);
        return Map.of("url", "/demo/media/" + filename, "publicId", "demo/" + filename);
    }

    @Override
    public Map<String, String> uploadEvidenceImage(MultipartFile file) throws IOException {
        return uploadImage(file);
    }

    @Override
    public boolean deleteImage(String publicId) {
        try {
            if (publicId == null || !publicId.startsWith("demo/")) return false;
            Files.deleteIfExists(resolve(publicId.substring(5)));
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public Map<String, String> replaceImage(String oldPublicId, MultipartFile newFile) throws IOException {
        var result = uploadImage(newFile);
        deleteImage(oldPublicId);
        return result;
    }

    @Override
    public String getOptimizedImageUrl(String publicId, int width, int height) {
        if (publicId == null || !publicId.startsWith("demo/") || width <= 0 || height <= 0
                || width > 2000 || height > 2000) throw new IllegalArgumentException("Invalid local image");
        resolve(publicId.substring(5));
        return "/demo/media/" + publicId.substring(5);
    }

    @Override
    public String getThumbnailUrl(String publicId) {
        return getOptimizedImageUrl(publicId, 200, 200);
    }

    Path resolve(String filename) {
        if (filename == null || !filename.matches("[a-f0-9-]{36}\\.(png|jpg|jpeg|webp)")) {
            throw new IllegalArgumentException("Invalid local image name");
        }
        rejectSymlinkAncestors();
        Path target = directory.resolve(filename).normalize();
        if (!target.startsWith(directory) || Files.isSymbolicLink(directory)
                || Files.isSymbolicLink(target)) throw new IllegalArgumentException("Unsafe local image path");
        return target;
    }

    private void rejectSymlinkAncestors() {
        for (Path current = directory; current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("Unsafe demo upload directory");
        }
    }

    boolean exists(Path file) {
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
    }
}
