# Nutrition Service

## External Food Source

OpenFoodFacts barcode and search fallback is disabled by default. Enable it only in environments
where `nutrition-service` is allowed outbound HTTPS to OpenFoodFacts:

```yaml
fitsync:
  nutrition:
    external-food:
      enabled: true
```

Optional guards:

```yaml
fitsync:
  nutrition:
    external-food:
      base-url: https://world.openfoodfacts.org
      user-agent: FitSync/1.0 (nutrition-service)
      connect-timeout: 2s
      read-timeout: 3s
      min-request-interval: 250ms
```

`GET /api/v1/nutrition/barcodes/{barcode}` keeps read-only fallback behavior for local misses.
`POST /api/v1/nutrition/foods/import/barcode/{barcode}` imports a found product into the reusable
catalog as `GLOBAL`, `IMPORTED`, and `verified=false`, using the existing catalog tables.
