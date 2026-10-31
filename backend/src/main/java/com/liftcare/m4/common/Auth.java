package com.liftcare.m4.common;

import org.springframework.http.HttpStatus;
import java.util.Set;

/**
 * DEV STUB. Member 1 owns authentication. Until their JWT filter is merged, the caller identity comes from the
 * X-User-Id / X-User-Role headers. Replace the header arguments in controllers with Member 1's principal and keep
 * the require(...) calls - the role names are the agreed ones.
 */
public final class Auth {
    private Auth() {}
    public static void require(String role, String... allowed) {
        if (!Set.of(allowed).contains(role))
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Role " + role + " cannot perform this action");
    }
    public static final String[] SERVICE = {"COMPANY_SUPERVISOR", "ADMIN"};
    public static final String[] VIEW_ALL = {"COMPANY_SUPERVISOR", "ADMIN", "BUILDING_MANAGER"};
}
