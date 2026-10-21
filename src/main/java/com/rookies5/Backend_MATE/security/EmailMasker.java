package com.rookies5.Backend_MATE.security;

public final class EmailMasker {

    private EmailMasker() {
    }

    // "honggildong@gmail.com" -> "ho*********@gmail.com" (앞 2글자와 도메인만 남긴다)
    public static String mask(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String local = email.substring(0, at);
        int visible = Math.min(2, local.length() - 1);
        return local.substring(0, visible) + "*".repeat(local.length() - visible) + email.substring(at);
    }
}
