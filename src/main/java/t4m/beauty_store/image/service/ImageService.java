package t4m.beauty_store.image.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ImageService {

    private final Cloudinary cloudinary;
    private final ImageUploadValidator imageUploadValidator;

    @Value("${cloudinary.folder:beautystore-products}")
    private String uploadFolder;

    public ImageService(Cloudinary cloudinary, ImageUploadValidator imageUploadValidator) {
        this.cloudinary = cloudinary;
        this.imageUploadValidator = imageUploadValidator;
    }

    public String uploadFromFile(Path path, String publicId) throws Exception {
        if (path == null || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Image path must be a regular file");
        }
        if (publicId == null || !publicId.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalArgumentException("Invalid image public ID");
        }

        ImageUploadValidator.ValidatedImage image = imageUploadValidator.validate(Files.readAllBytes(path));
        Map<?, ?> result = cloudinary.uploader().upload(
                image.bytes(),
                ObjectUtils.asMap(
                        "public_id", publicId + "-" + UUID.randomUUID(),
                        "folder", normalizedFolder(),
                        "resource_type", "image",
                        "allowed_formats", List.of("jpg", "jpeg", "png", "webp"),
                        "overwrite", false));
        Object secureUrl = result.get("secure_url");
        if (!(secureUrl instanceof String value) || !value.startsWith("https://")) {
            throw new IllegalStateException("Cloudinary returned an invalid URL");
        }
        return value;
    }

    private String normalizedFolder() {
        String value = uploadFolder == null ? "" : uploadFolder.strip();
        if (!value.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalStateException("cloudinary.folder is invalid");
        }
        return value;
    }
}
