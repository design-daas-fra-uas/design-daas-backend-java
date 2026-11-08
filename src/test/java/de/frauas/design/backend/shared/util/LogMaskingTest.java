package de.frauas.design.backend.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogMasking")
class LogMaskingTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(
            value = {
                "john.doe@example.com, j***@example.com",
                "a@b.c, a***@b.c",
                "'', ''",
                "'  ', '  '",
                "no-at-sign, no-at-sign",
                "@example.com, @example.com",
            },
            ignoreLeadingAndTrailingWhitespace = false)
    @DisplayName("masks the local part of an e-mail address")
    void masksEmail(String input, String expected) {
        assertThat(LogMasking.maskEmail(input.strip().isEmpty() ? input : input.strip()))
                .isEqualTo(expected.strip().isEmpty() ? expected : expected.strip());
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("keeps null")
    void keepsNull(String input) {
        assertThat(LogMasking.maskEmail(input)).isNull();
    }
}
