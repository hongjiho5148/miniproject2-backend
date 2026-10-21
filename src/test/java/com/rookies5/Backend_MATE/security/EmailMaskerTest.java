package com.rookies5.Backend_MATE.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailMaskerTest {

    @Test
    @DisplayName("앞 2글자와 도메인만 남기고 나머지는 가린다")
    void masksLocalPart() {
        assertThat(EmailMasker.mask("honggildong@gmail.com")).isEqualTo("ho*********@gmail.com");
    }

    @Test
    @DisplayName("아이디가 짧아도 최소 1글자는 가려진다")
    void masksShortLocalPart() {
        assertThat(EmailMasker.mask("ab@mate.com")).isEqualTo("a*@mate.com");
        assertThat(EmailMasker.mask("a@mate.com")).isEqualTo("*@mate.com");
    }

    @Test
    @DisplayName("형식이 이상하면 그대로 두고, null은 null을 반환한다")
    void handlesMalformedInput() {
        assertThat(EmailMasker.mask("not-an-email")).isEqualTo("not-an-email");
        assertThat(EmailMasker.mask(null)).isNull();
    }
}
