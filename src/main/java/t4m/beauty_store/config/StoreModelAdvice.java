package t4m.beauty_store.config;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes the configured legal identity and customer policy available to every Thymeleaf view. */
@ControllerAdvice
@RequiredArgsConstructor
public class StoreModelAdvice {
    private final StoreProperties storeProperties;
    private final StoreRuntimeCapabilities capabilities;

    @ModelAttribute("demoMode")
    public boolean demoMode() { return capabilities.isDemo(); }

    @ModelAttribute("vnpayAvailable")
    public boolean vnpayAvailable() { return capabilities.isVnpayAvailable(); }

    @ModelAttribute("store")
    public StoreProperties store() {
        return storeProperties;
    }
}
