package t4m.beauty_store.product.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import t4m.beauty_store.image.service.ImageUploadValidator;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private static final Pattern SAFE_PUBLIC_ID = Pattern.compile("[A-Za-z0-9_./-]{1,255}");

    private final Cloudinary cloudinary;
    private final ImageUploadValidator imageUploadValidator;

    @Value("${cloudinary.folder:beautystore-products}")
    private String folder;

    public Map<String, String> uploadImage(MultipartFile file) throws IOException {
        return uploadImage(file, normalizedFolder());
    }

    public Map<String, String> uploadEvidenceImage(MultipartFile file) throws IOException {
        return uploadImage(file, normalizedFolder() + "/evidence");
    }

    private Map<String, String> uploadImage(MultipartFile file, String targetFolder) throws IOException {
        ImageUploadValidator.ValidatedImage image = imageUploadValidator.validate(file);
        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    image.bytes(),
                    ObjectUtils.asMap(
                            "public_id", UUID.randomUUID().toString(),
                            "folder", targetFolder,
                            "resource_type", "image",
                            "allowed_formats", List.of("jpg", "jpeg", "png", "webp"),
                            "transformation", new Transformation<>()
                                    .width(1600)
                                    .height(1600)
                                    .crop("limit")
                                    .quality("auto")
                                    .fetchFormat("auto"),
                            "overwrite", false));

            String secureUrl = value(uploadResult, "secure_url");
            String publicId = value(uploadResult, "public_id");
            if (!secureUrl.startsWith("https://") || !isSafePublicId(publicId)) {
                if (isSafePublicId(publicId)) {
                    deleteImage(publicId);
                }
                throw new IOException("Cloudinary returned an invalid image resource");
            }
            log.info("Uploaded a validated BeautyStore product image");
            return Map.of("url", secureUrl, "publicId", publicId);
        } catch (IOException exception) {
            log.error("Cloudinary image upload failed: {}", exception.getClass().getSimpleName());
            throw new IOException("Không thể tải ảnh lên", exception);
        }
    }

    public boolean deleteImage(String publicId) {
        if (!isSafePublicId(publicId)) {
            log.warn("Rejected an invalid Cloudinary public ID");
            return false;
        }
        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            if (!"ok".equals(result.get("result")) && !"not found".equals(result.get("result"))) {
                log.warn("Cloudinary did not delete an image resource");
                return false;
            }
            return true;
        } catch (IOException exception) {
            log.error("Cloudinary image deletion failed: {}", exception.getClass().getSimpleName());
            return false;
        }
    }

    public Map<String, String> replaceImage(String oldPublicId, MultipartFile newFile) throws IOException {
        Map<String, String> uploadResult = uploadImage(newFile);
        if (oldPublicId != null && !oldPublicId.isBlank()) {
            deleteImage(oldPublicId);
        }
        return uploadResult;
    }

    public String getOptimizedImageUrl(String publicId, int width, int height) {
        if (!isSafePublicId(publicId)) {
            throw new IllegalArgumentException("Invalid Cloudinary public ID");
        }
        if (width <= 0 || height <= 0 || width > 2_000 || height > 2_000) {
            throw new IllegalArgumentException("Image dimensions must be between 1 and 2000 pixels");
        }
        return cloudinary.url()
                .secure(true)
                .transformation(new Transformation<>()
                        .width(width)
                        .height(height)
                        .crop("fill")
                        .quality("auto")
                        .fetchFormat("auto"))
                .generate(publicId);
    }

    public String getThumbnailUrl(String publicId) {
        return getOptimizedImageUrl(publicId, 200, 200);
    }

    private String normalizedFolder() {
        String value = folder == null ? "" : folder.strip();
        if (!value.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalStateException("cloudinary.folder is invalid");
        }
        return value;
    }

    private boolean isSafePublicId(String publicId) {
        return publicId != null
                && SAFE_PUBLIC_ID.matcher(publicId).matches()
                && !publicId.contains("..")
                && !publicId.startsWith("/");
    }

    private String value(Map<?, ?> result, String key) throws IOException {
        Object value = result == null ? null : result.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IOException("Cloudinary response is missing " + key);
        }
        return text;
    }
}
