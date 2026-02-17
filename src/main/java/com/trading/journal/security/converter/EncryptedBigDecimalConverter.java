package com.trading.journal.security.converter;

import com.trading.journal.security.EncryptionUtil;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** JPA AttributeConverter for encrypting BigDecimal values in the database. */
@Converter
@Component
public class EncryptedBigDecimalConverter implements AttributeConverter<BigDecimal, String> {

    private static EncryptionUtil encryptionUtil;

    public EncryptedBigDecimalConverter() {
        // Default constructor for JPA
    }

    public EncryptedBigDecimalConverter(EncryptionUtil util) {
        EncryptedBigDecimalConverter.encryptionUtil = util;
    }

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        if (attribute == null) {
            return null;
        }
        if (encryptionUtil == null || !encryptionUtil.isEncryptionEnabled()) {
            return attribute.toPlainString();
        }
        return encryptionUtil.encrypt(attribute.toPlainString());
    }

    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        if (encryptionUtil == null || !encryptionUtil.isEncryptionEnabled()) {
            return new BigDecimal(dbData);
        }
        try {
            String decrypted = encryptionUtil.decrypt(dbData);
            return new BigDecimal(decrypted);
        } catch (Exception e) {
            // May be unencrypted legacy data
            return new BigDecimal(dbData);
        }
    }
}
