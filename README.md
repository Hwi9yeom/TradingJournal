# TradingJournal

This project is a simple trading journal application built with Spring Boot.

## Updating DART Corporation Codes

Corporation codes used to fetch disclosures from the DART API are configured in
`src/main/resources/corp-codes.yml`. Each entry maps a corporation name to its
DART corporation code.

To update or add a new mapping:

1. Open `src/main/resources/corp-codes.yml`.
2. Add or update the desired corporation name under `corp.codes` with the
   corresponding code.
3. Restart the application so the updated mappings are loaded.


Example snippet:

```yaml
corp:
  codes:
    삼성전자: "00126380"
    새로운회사: "00999999"
```
