package t4m.beauty_store.auth.validation;

import java.util.regex.Pattern;

public final class PasswordPolicy {
    private static final Pattern STRONG = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d])\\S{8,128}$");

    private PasswordPolicy() {}

    public static boolean isStrong(String value) {
        return value != null && STRONG.matcher(value).matches();
    }

    public static void requireStrong(String value) {
        if (!isStrong(value)) {
            throw new IllegalArgumentException(
                "Mật khẩu phải dài 8-128 ký tự và có chữ hoa, chữ thường, số, ký tự đặc biệt");
        }
    }
}
