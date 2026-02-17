package com.trading.journal.security.converter;

import com.trading.journal.security.EncryptionUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Configuration to inject EncryptionUtil into JPA converters. */
@Component
public class EncryptionConfig {

    private final EncryptionUtil encryptionUtil;

    public EncryptionConfig(EncryptionUtil encryptionUtil) {
        this.encryptionUtil = encryptionUtil;
    }

    @PostConstruct
    public void init() {
        new EncryptedBigDecimalConverter(encryptionUtil);
        new EncryptedStringConverter(encryptionUtil);
    }
}
