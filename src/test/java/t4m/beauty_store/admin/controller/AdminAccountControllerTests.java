package t4m.beauty_store.admin.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import t4m.beauty_store.admin.dto.AccountDTO;
import t4m.beauty_store.admin.dto.AccountUpdateRequest;
import t4m.beauty_store.admin.dto.BulkActionRequest;
import t4m.beauty_store.admin.service.AdminAccountService;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAccountControllerTests {

    private AdminAccountService service;
    private AdminAccountController controller;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        service = mock(AdminAccountService.class);
        controller = new AdminAccountController(service);
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("admin@beautystore.vn");
    }

    @Test
    void actorIdentityIsForwardedToEverySingleAccountMutationThatCanRemoveAccess() {
        AccountUpdateRequest update = new AccountUpdateRequest();
        when(service.updateAccount(1L, update, "admin@beautystore.vn"))
            .thenReturn(new AccountDTO());

        controller.updateAccount(1L, update, authentication);
        controller.banAccount(2L, authentication);
        controller.deleteAccount(3L, authentication);

        verify(service).updateAccount(1L, update, "admin@beautystore.vn");
        verify(service).banAccount(2L, "admin@beautystore.vn");
        verify(service).deleteAccount(3L, "admin@beautystore.vn");
    }

    @Test
    void actorIdentityIsForwardedToBulkMutation() {
        BulkActionRequest request = new BulkActionRequest();
        request.setAction("delete");
        request.setUserIds(List.of(2L, 3L));

        controller.bulkAction(request, authentication);

        verify(service).bulkAction(request, "admin@beautystore.vn");
    }
}
