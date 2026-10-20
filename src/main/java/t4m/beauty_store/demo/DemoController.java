package t4m.beauty_store.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@Profile("demo")
@RequiredArgsConstructor
public class DemoController {
    private final DemoMailbox mailbox;

    @GetMapping({"/demo", "/demo/inbox"})
    String inbox() {
        return "demo/inbox";
    }

    @GetMapping("/demo/api/mail")
    @ResponseBody
    List<DemoMailbox.Summary> listMail() {
        return mailbox.list();
    }

    @GetMapping("/demo/mail/{id}")
    ResponseEntity<String> readMail(@PathVariable String id) {
        String html = mailbox.read(id);
        if (html == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Content-Security-Policy",
                        "default-src 'none'; style-src 'unsafe-inline'; img-src 'self' data:; sandbox")
                .body(html);
    }
}
