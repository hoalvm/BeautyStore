package t4m.beauty_store.config;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes the configured legal identity and customer policy available to every Thymeleaf view. */
@ControllerAdvice
@RequiredArgsConstructor
public class StoreModelAdvice {
    private final StoreProperties storeProperties;

    @ModelAttribute("store")
    public StoreProperties store() {
        return storeProperties;
    }
}
