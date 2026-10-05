package com.smartcare.config;

import com.smartcare.model.User;
import com.smartcare.model.UserStatus;
import com.smartcare.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Re-checks the signed-in account on every request: both its status and its role.
 *
 * Spring Security only reads a user's status at sign-in. Without this filter an
 * account that an admin deactivates keeps working until that person happens to
 * log out — so a dismissed member of staff could carry on using the system.
 * This ends their session on their very next click instead.
 *
 * Kept deliberately cheap: it runs a single lookup by username, and only for
 * requests that are already authenticated.
 */
@Component
@RequiredArgsConstructor
public class AccountStatusFilter extends OncePerRequestFilter {

    private final UserRepository users;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {

            Optional<User> found = users.findByUsername(auth.getName());
            boolean stillAllowed = found.isPresent() && found.get().getStatus() == UserStatus.ACTIVE;

            if (!stillAllowed) {
                // The account was deactivated, rejected or deleted while this
                // session was open. Drop the session and send them to the login
                // page with an explanation rather than a bare access-denied page.
                endSession(request, response, "disabled");
                return;
            }

            // The session's permissions were fixed at sign-in. If an admin has
            // since changed this person's role, the session would otherwise keep
            // the OLD role's access until they happened to log out - so a
            // pharmacist demoted to customer could still reach /pharmacist/**.
            // Compare the role stored now with the one the session was granted,
            // and require a fresh sign-in when they differ.
            String currentAuthority = "ROLE_" + found.get().getRole().name();
            boolean roleUnchanged = auth.getAuthorities().stream()
                    .anyMatch(a -> currentAuthority.equals(a.getAuthority()));

            if (!roleUnchanged) {
                endSession(request, response, "roleChanged");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /** Clears the security context, ends the session and sends the user to sign in again. */
    private void endSession(HttpServletRequest request, HttpServletResponse response, String reason)
            throws IOException {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        response.sendRedirect(request.getContextPath() + "/login?" + reason);
    }

    /** Static assets and the login page itself never need the check. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getServletPath();
        return p.startsWith("/css/") || p.startsWith("/js/") || p.startsWith("/img/")
                || p.startsWith("/fonts/") || p.equals("/login") || p.equals("/error");
    }
}
