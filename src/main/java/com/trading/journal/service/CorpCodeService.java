package com.trading.journal.service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

/** Service providing corporation code lookups loaded from a YAML configuration file. */
@Service
@Slf4j
public class CorpCodeService {

    private Map<String, String> corpCodeMap = new HashMap<>();

    @PostConstruct
    private void loadCorpCodes() {
        try (InputStream input = new ClassPathResource("corp-codes.yml").getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(input);
            if (root != null && root.get("corp") instanceof Map<?, ?> corp) {
                Object codesObj = ((Map<?, ?>) corp).get("codes");
                if (codesObj instanceof Map<?, ?> codes) {
                    for (Map.Entry<?, ?> entry : codes.entrySet()) {
                        corpCodeMap.put(
                                String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                }
            }
            log.info("Loaded {} corp codes", corpCodeMap.size());
        } catch (Exception e) {
            log.error("Failed to load corp codes", e);
            corpCodeMap = Collections.emptyMap();
        }
    }

    /**
     * Returns the DART corporation code for the given corporation name.
     *
     * @param corpName the corporation name
     * @return corporation code or {@code null} if not found
     */
    public String findCorpCode(String corpName) {
        return corpCodeMap.get(corpName);
    }
}
