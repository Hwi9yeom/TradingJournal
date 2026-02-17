package com.trading.journal.security.converter;

import com.trading.journal.security.EncryptionUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/** JPA AttributeConverter for encrypting String values in the database. */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static EncryptionUtil encryptionUtil;

    public EncryptedStringConverter() {
        // Default constructor for JPA
    }

    public EncryptedStringConverter(EncryptionUtil util) {
        EncryptedStringConverter.encryptionUtil = util;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        if (encryptionUtil == null || !encryptionUtil.isEncryptionEnabled()) {
            return attribute;
        }
        return encryptionUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (encryptionUtil == null || !encryptionUtil.isEncryptionEnabled()) {
            return dbData;
        }
        try {
            return encryptionUtil.decrypt(dbData);
        } catch (Exception e) {
            // May be unencrypted legacy data
            return dbData;
        }
    }
}
