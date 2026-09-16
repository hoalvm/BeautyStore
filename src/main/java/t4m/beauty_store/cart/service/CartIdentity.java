package t4m.beauty_store.cart.service;

public record CartIdentity(String userEmail, String guestTokenHash) {
    public boolean authenticated() {
        return userEmail != null && !userEmail.isBlank();
    }
}
