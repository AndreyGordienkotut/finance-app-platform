package transaction_service.transaction_service.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("test")
@RequestMapping("/api/test")
public class TestSecurityController {

    @GetMapping("/test-principal")
    public ResponseEntity<?> testPrincipal(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam Long accountId
    ) {
        return ResponseEntity.ok("User email: " + user.getUsername());
    }


    @GetMapping("/admin-stats")
    public ResponseEntity<?> adminStats() {
        return ResponseEntity.ok("Secret admin data");
    }
}