package t4m.beauty_store.order.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutRequestValidationTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validStructuredAddressPassesAndResolvesInCanonicalOrder() {
        CheckoutRequest request = validRequest();
        request.setAddressLine(" 12 Nguyễn Huệ ");
        request.setWard(" Bến Nghé ");
        request.setDistrict(" Quận 1 ");
        request.setProvince(" TP. Hồ Chí Minh ");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.resolvedShippingAddress())
            .isEqualTo("12 Nguyễn Huệ, Bến Nghé, Quận 1, TP. Hồ Chí Minh");
    }

    @Test
    void everyMissingAddressPartFailsTheCompositeConstraint() {
        for (String field : Set.of("addressLine", "ward", "district", "province")) {
            CheckoutRequest request = validRequest();
            switch (field) {
                case "addressLine" -> request.setAddressLine(" ");
                case "ward" -> request.setWard(null);
                case "district" -> request.setDistrict("");
                case "province" -> request.setProvince(" ");
                default -> throw new IllegalStateException();
            }
            assertThat(validator.validate(request)).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString).contains("shippingAddressValid");
        }
    }

    @Test
    void oversizedAddressPartsAreRejectedOnTheirOwnFields() {
        CheckoutRequest request = validRequest();
        request.setAddressLine("a".repeat(501));
        request.setWard("w".repeat(121));
        request.setDistrict("d".repeat(121));
        request.setProvince("p".repeat(121));

        assertThat(validator.validate(request)).extracting(ConstraintViolation::getPropertyPath)
            .extracting(Object::toString)
            .contains("addressLine", "ward", "district", "province");
    }

    @Test
    void invalidIdentityAndPaymentFieldsAreRejected() {
        CheckoutRequest request = validRequest();
        request.setCustomerEmail("not-an-email");
        request.setCustomerPhone("09-abcd");
        request.setPaymentMethod(" ");

        assertThat(validator.validate(request)).extracting(ConstraintViolation::getPropertyPath)
            .extracting(Object::toString)
            .contains("customerEmail", "customerPhone", "paymentMethod");
    }

    private static CheckoutRequest validRequest() {
        return CheckoutRequest.builder()
            .customerName("Nguyễn An")
            .customerEmail("an@example.test")
            .customerPhone("0901234567")
            .addressLine("12 Nguyễn Huệ")
            .ward("Bến Nghé")
            .district("Quận 1")
            .province("TP. Hồ Chí Minh")
            .paymentMethod("COD")
            .build();
    }
}
