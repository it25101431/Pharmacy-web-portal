package com.smartcare.config;

import com.smartcare.repository.NotificationRepository;
import com.smartcare.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.IOException;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalAdvice {
    private final UserRepository users;
    private final NotificationRepository notifications;

    /** Unread-notification badge shown in the navigation bar. */
    @ModelAttribute("unreadCount")
    public long unreadCount(Authentication auth) {
        if (auth == null) return 0;
        // The bell badge is decoration: if counting fails for any reason, the page must still open.
        try {
            return users.findByUsername(auth.getName()).map(notifications::countByUserAndSeenFalse).orElse(0L);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Current path (e.g. /customer/cart) so the navigation bar can highlight the active page. */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getServletPath();
    }

    /** Business-rule violations are shown as a red flash message on the page the user came from. */
    @ExceptionHandler({IllegalStateException.class, MaxUploadSizeExceededException.class, OptimisticLockingFailureException.class})
    public void handle(Exception ex, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String msg = ex instanceof MaxUploadSizeExceededException ? "File is too large (max 5 MB)."
                : ex instanceof OptimisticLockingFailureException ? "This record was changed by someone else. Please retry."
                : ex.getMessage();
        FlashMap fm = new FlashMap();
        fm.put("err", msg);
        RequestContextUtils.getFlashMapManager(req).saveOutputFlashMap(fm, req, resp);
        String ref = req.getHeader("Referer");
        resp.sendRedirect(ref != null ? ref : req.getContextPath() + "/home");
    }
}
