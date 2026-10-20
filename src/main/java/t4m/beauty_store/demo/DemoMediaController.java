package t4m.beauty_store.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("demo")
@RequiredArgsConstructor
public class DemoMediaController {
    private final DemoImageStorage storage;

    @GetMapping("/demo/media/{filename}")
    ResponseEntity<Resource> media(@PathVariable String filename) {
        try {
            var file = storage.resolve(filename);
            if (!storage.exists(file)) return ResponseEntity.notFound().build();
            MediaType type = filename.endsWith(".png") ? MediaType.IMAGE_PNG
                    : filename.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
            return ResponseEntity.ok().contentType(type).header("X-Content-Type-Options", "nosniff")
                    .body(new FileSystemResource(file));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }
}
