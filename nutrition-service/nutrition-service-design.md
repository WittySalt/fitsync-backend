# Nutrition Service Design

## 1. Responsibility Boundary

`nutrition-service` should own nutrition-facing source facts and nutrition-specific master data.

Recommended ownership split:

- `nutrition-service`
  - meal logs
  - meal item nutrition snapshots
  - reusable custom foods
  - food catalog records
  - food servings
  - food barcode mappings
  - food nutrient profiles
  - nutrition target projections
  - nutrition preference projections used by nutrition workflows
- `user-service`
  - profile
  - user-owned source-of-truth preferences
  - user goals as canonical write truth
- `analytics-service`
  - adherence trends
  - calorie deficit summaries
  - habit scores
- `recommendation-service`
  - meal suggestions
  - adaptive calorie and macro guidance
  - recipe ranking

Important rule:

- `nutrition-service` stores nutrition source facts and nutrition master data
- derived outputs such as remaining calories, macro adherence, and score labels are computed from stored facts or local read models
- canonical user preference and goal writes still originate in `user-service`

---

## 2. Common Persistence Rules

`nutrition-service` must follow the shared persistence rules introduced by `persistence-support`.

Rules:

- ordinary mutable business rows extend `BaseAuditableEntity`
- code or catalog rows that require business validity windows extend `BaseEffectiveEntity`
- outbox rows extend `BaseOutboxEventEntity`
- `event-contracts` remains transport-only and must not carry JPA concerns
- do not recreate local audit or outbox base classes inside `nutrition-service`

Current nutrition usage:

- meal logs, meal items, custom foods, nutrition targets, and preference projections use shared audit primitives
- food master data such as brands, catalog items, servings, barcodes, and nutrient profiles use effective-dating support
- outbox rows use the shared produced/published/created time semantics

---

## 3. Domain Slices

`nutrition-service` should not stop at `meal + item + custom food`.

It should be split into six write-model or projection slices:

### 3.1 Meal Logging

Top-level user meal entries.

Fields:

- `mealId`
- `userId`
- `mealType`
- `name`
- `source`
- `consumedAt`
- `timezone`
- `note`

### 3.2 Meal Item Nutrition Snapshots

Per-item source facts logged inside a meal.

Fields:

- `mealItemId`
- `mealId`
- `customFoodId`
- `foodCatalogItemId`
- `foodServingId`
- `externalFoodRef`
- `barcode`
- `foodName`
- `brandName`
- `quantity`
- `unit`
- `servingSize`
- `servingUnit`
- `snapshotServingLabel`
- `calories`
- `proteinGrams`
- `carbsGrams`
- `fatGrams`
- `fiberGrams`
- `sugarGrams`
- `sodiumMg`

### 3.3 Meal Item Nutrient Detail Snapshots

Fine-grained nutrient detail for ideal calorie and nutrition breakdown.

Fields:

- `detailId`
- `mealItemId`
- `nutrientCode`
- `displayName`
- `amount`
- `unit`
- `sortOrder`

Use this slice when macro-only totals are not enough and the product needs richer nutrition detail such as saturated fat, cholesterol, potassium, or micronutrients.

### 3.4 User-Defined Food Assets

Reusable user-owned food records.

Slices:

- `CustomFood`
- optional future user recipes

### 3.5 Food Master Data

Reusable food catalog data shared across logs.

Slices:

- `FoodBrand`
- `FoodCatalogItem`
- `FoodServing`
- `FoodBarcode`
- `FoodNutrientProfile`
- `FoodNutrientDetail`

Design rule:

- meal logging must snapshot nutrition at log time
- catalog updates must not silently mutate historical meal truth

### 3.6 Nutrition Read Models and Projections

Nutrition-local read models fed by events.

Slices:

- `NutritionTargetProjection`
- `NutritionPreferenceProjection`

These are not the canonical write source for user goals or preferences. They exist so nutrition APIs can answer nutrition-specific reads without synchronous cross-service dependency at every request.

---

## 4. Recommended Aggregates

### 4.1 MealLog

```text
MealLog
- mealId: UUID
- userId: UUID
- mealType: MealType
- name: String?
- source: MealSource
- consumedAt: Instant
- timezone: String?
- note: String?
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.2 MealItem

```text
MealItem
- mealItemId: UUID
- mealId: UUID
- customFoodId: UUID?
- foodCatalogItemId: UUID?
- foodServingId: UUID?
- externalFoodRef: String?
- barcode: String?
- foodName: String
- brandName: String?
- quantity: BigDecimal
- unit: String
- servingSize: BigDecimal?
- servingUnit: String?
- snapshotServingLabel: String?
- calories: Integer
- proteinGrams: BigDecimal?
- carbsGrams: BigDecimal?
- fatGrams: BigDecimal?
- fiberGrams: BigDecimal?
- sugarGrams: BigDecimal?
- sodiumMg: BigDecimal?
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.3 MealItemNutrientDetail

```text
MealItemNutrientDetail
- detailId: UUID
- mealItemId: UUID
- nutrientCode: String
- displayName: String
- amount: BigDecimal
- unit: String
- sortOrder: Integer
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.4 CustomFood

```text
CustomFood
- customFoodId: UUID
- userId: UUID
- name: String
- brandName: String?
- servingSize: BigDecimal
- servingUnit: String
- calories: Integer
- proteinGrams: BigDecimal?
- carbsGrams: BigDecimal?
- fatGrams: BigDecimal?
- fiberGrams: BigDecimal?
- sugarGrams: BigDecimal?
- sodiumMg: BigDecimal?
- verified: boolean
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.5 FoodBrand

```text
FoodBrand
- brandId: UUID
- name: String
- normalizedName: String
- countryCode: String?
- dateEffectiveFrom: LocalDate?
- dateEffectiveTo: LocalDate?
- active: boolean
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.6 FoodCatalogItem

```text
FoodCatalogItem
- foodId: UUID
- scope: FoodCatalogScope
- ownerUserId: UUID?
- source: FoodCatalogSource
- displayName: String
- normalizedName: String
- categoryCode: String?
- brandId: UUID?
- defaultServingId: UUID?
- verified: boolean
- densityGramsPerMl: BigDecimal?
- dateEffectiveFrom: LocalDate?
- dateEffectiveTo: LocalDate?
- active: boolean
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.7 FoodServing

```text
FoodServing
- servingId: UUID
- foodId: UUID
- label: String
- quantity: BigDecimal
- unit: String
- gramsEquivalent: BigDecimal?
- millilitersEquivalent: BigDecimal?
- sortOrder: Integer
- defaultServing: boolean
- dateEffectiveFrom: LocalDate?
- dateEffectiveTo: LocalDate?
- active: boolean
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.8 FoodBarcode

```text
FoodBarcode
- barcodeId: UUID
- foodId: UUID
- barcode: String
- regionCode: String?
- primaryBarcode: boolean
- dateEffectiveFrom: LocalDate?
- dateEffectiveTo: LocalDate?
- active: boolean
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.9 FoodNutrientProfile

```text
FoodNutrientProfile
- nutrientProfileId: UUID
- foodId: UUID
- basisQuantity: BigDecimal
- basisUnit: String
- calories: Integer
- proteinGrams: BigDecimal?
- carbsGrams: BigDecimal?
- fatGrams: BigDecimal?
- saturatedFatGrams: BigDecimal?
- transFatGrams: BigDecimal?
- fiberGrams: BigDecimal?
- sugarGrams: BigDecimal?
- sodiumMg: BigDecimal?
- potassiumMg: BigDecimal?
- cholesterolMg: BigDecimal?
- dateEffectiveFrom: LocalDate?
- dateEffectiveTo: LocalDate?
- active: boolean
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.10 NutritionTargetProjection

```text
NutritionTargetProjection
- userId: UUID
- calorieGoalId: UUID?
- calorieTarget: Integer?
- calorieTargetDate: LocalDate?
- proteinGoalId: UUID?
- proteinTarget: BigDecimal?
- proteinTargetDate: LocalDate?
- carbGoalId: UUID?
- carbTargetGrams: BigDecimal?
- carbTargetDate: LocalDate?
- fatGoalId: UUID?
- fatTargetGrams: BigDecimal?
- fatTargetDate: LocalDate?
- projectionUpdatedAt: Instant
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.11 NutritionPreferenceProjection

```text
NutritionPreferenceProjection
- userId: UUID
- preferredUnitsCode: String?
- dietaryPreferenceCode: String?
- workoutDaysPerWeek: Integer?
- notificationsEnabled: boolean
- projectionUpdatedAt: Instant
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

### 4.12 FoodNutrientDetail

```text
FoodNutrientDetail
- detailId: UUID
- foodId: UUID
- nutrientCode: String
- displayName: String
- amount: BigDecimal
- unit: String
- sortOrder: Integer
- dateEffectiveFrom: LocalDate?
- dateEffectiveTo: LocalDate?
- active: boolean
- dateTimeCreated: Instant
- dateTimeUpdated: Instant
```

---

## 5. Enums

```text
MealType
- BREAKFAST
- LUNCH
- DINNER
- SNACK
- SUPPER
- OTHER

MealSource
- MANUAL
- CUSTOM_FOOD
- BARCODE
- IMPORTED

FoodCatalogScope
- GLOBAL
- USER

FoodCatalogSource
- OFFICIAL
- USER_DEFINED
- IMPORTED
- BARCODE
```

Nutrition-specific codes and labels should remain open to future code catalog treatment. Do not prematurely hardcode every nutrient as a Java enum if the UI or data import layer will likely need a broader set than compile-time constants.

---

## 6. API Surface

Base path:

- `/api/v1/nutrition`

Authenticated requests must derive `userId` from JWT.

### 6.1 Meals

- `POST /api/v1/nutrition/meals`
- `GET /api/v1/nutrition/meals`
- `GET /api/v1/nutrition/meals/{mealId}`
- `PUT /api/v1/nutrition/meals/{mealId}`
- `DELETE /api/v1/nutrition/meals/{mealId}`
- `GET /api/v1/nutrition/meals/latest`

### 6.2 Custom Foods

- `POST /api/v1/nutrition/custom-foods`
- `GET /api/v1/nutrition/custom-foods`
- `GET /api/v1/nutrition/custom-foods/{customFoodId}`
- `PUT /api/v1/nutrition/custom-foods/{customFoodId}`
- `DELETE /api/v1/nutrition/custom-foods/{customFoodId}`

### 6.3 Daily Summary

- `GET /api/v1/nutrition/summary/daily`

Current response direction:

- totals consumed
- target values when local target projection exists
- remaining values when target values exist

### 6.4 Future Food Catalog APIs

Recommended next-step APIs:

- `GET /api/v1/nutrition/foods/search`
- `GET /api/v1/nutrition/foods/{foodId}`
- `GET /api/v1/nutrition/foods/{foodId}/servings`
- `GET /api/v1/nutrition/barcodes/{barcode}`

Rule:

- treat these as nutrition master-data reads
- do not require them to exist before meal logging can work

---

## 7. Request and Response Models

### 7.1 Requests

```text
CreateMealRequest
- mealType
- name
- source
- consumedAt
- timezone
- note
- items[]

MealItemInput
- customFoodId
- foodCatalogItemId
- foodServingId
- externalFoodRef
- barcode
- foodName
- brandName
- quantity
- unit
- servingSize
- servingUnit
- snapshotServingLabel
- calories
- proteinGrams
- carbsGrams
- fatGrams
- fiberGrams
- sugarGrams
- sodiumMg
- nutrientDetails[]

CreateCustomFoodRequest
- name
- brandName
- servingSize
- servingUnit
- calories
- proteinGrams
- carbsGrams
- fatGrams
- fiberGrams
- sugarGrams
- sodiumMg
```

Validation rules:

- at least one meal item is required
- `foodName` is required even when a catalog reference exists
- numeric nutrition values must be non-negative
- `quantity` must be positive
- JWT `userId` is authoritative
- historical meal snapshots must not depend on mutable catalog facts

### 7.2 Responses

```text
MealView
- mealId
- mealType
- name
- source
- consumedAt
- timezone
- note
- totalCalories
- totalProteinGrams
- totalCarbsGrams
- totalFatGrams
- itemCount
- items[]
- createdAt
- updatedAt

MealItemView
- mealItemId
- customFoodId
- foodCatalogItemId
- foodServingId
- externalFoodRef
- barcode
- foodName
- brandName
- quantity
- unit
- servingSize
- servingUnit
- snapshotServingLabel
- calories
- proteinGrams
- carbsGrams
- fatGrams
- fiberGrams
- sugarGrams
- sodiumMg

DailyNutritionSummaryView
- date
- timezone
- totalCalories
- totalProteinGrams
- totalCarbsGrams
- totalFatGrams
- targetCalories
- targetProteinGrams
- targetCarbsGrams
- targetFatGrams
- remainingCalories
- remainingProteinGrams
- remainingCarbsGrams
- remainingFatGrams
- mealCount

FoodDetailView
- foodId
- displayName
- normalizedName
- brandName
- categoryCode
- scope
- source
- verified
- densityGramsPerMl
- calories
- proteinGrams
- carbsGrams
- fatGrams
- saturatedFatGrams
- transFatGrams
- fiberGrams
- sugarGrams
- sodiumMg
- potassiumMg
- cholesterolMg
- basisQuantity
- basisUnit
- primaryBarcode
- servings[]
- nutrientDetails[]
```

---

## 8. Event Design

### 8.1 Incoming Events

#### GoalUpdated

Producer:

- `user-service`

Consumer:

- `nutrition-service`

Purpose:

- materialize nutrition target read state locally

Current implementation note:

- `nutrition-service` consumes `GoalUpdated` from `progress-events`
- current local projection supports calorie and protein targets
- nutrition side is also future-ready for carbs and fat target strings when upstream starts emitting them

#### UserPreferencesUpdated

Producer:

- `user-service`

Consumer:

- `nutrition-service`

Purpose:

- materialize nutrition-relevant preference hints locally, especially dietary preference and units

Current implementation note:

- current event payload is intentionally thin
- `nutrition-service` stores only the fields presently emitted by `user-service`

### 8.2 Outgoing Events

#### MealLogged

- topic: `nutrition-events`
- published after successful meal creation

#### MealUpdated

- topic: `nutrition-events`
- published after successful meal correction

#### MealDeleted

- topic: `nutrition-events`
- published after successful meal deletion

Event rules:

- partition by `userId`
- payloads remain meal-level summaries
- catalog and nutrient-detail internals do not need to leak into event contracts unless downstream consumers prove the need

### 8.3 Outbox Rules

`nutrition-service` outbox rows use `BaseOutboxEventEntity` semantics:

- `dateTimeProduced` is the domain occurrence time
- `dateTimePublished` is broker publish time
- `dateTimeCreated` is row creation time

Do not collapse those into ordinary row audit timestamps.

---

## 9. Persistence Direction

Recommended tables:

```text
meal_logs
meal_items
meal_item_nutrient_details
custom_foods
food_brands
food_catalog_items
food_servings
food_barcodes
food_nutrient_profiles
food_nutrient_details
nutrition_targets
nutrition_preference_projections
outbox_events
```

Rules:

- meal logs are user-owned write truth
- meal items snapshot user-facing nutrition at log time
- nutrient detail rows are optional but preferred when detailed nutrition is available
- food master data can evolve through effective-dated rows
- outbox state is transport-facing persistence, not ordinary domain audit

---

## 10. Security Notes

Mandatory direction:

- require JWT authentication for all non-health endpoints
- derive ownership from authenticated principal only
- validate all quantities and nutrition values
- do not trust client-calculated totals unless item facts are also supplied
- avoid logging full nutrition payloads where they may contain user notes or imported proprietary data

Residual risk:

- external barcode or import data can still be incorrect
- snapshotting protects historical integrity, not source accuracy

---

## 11. Rollout Order

### Phase 1

- define nutrition write API
- define meal events
- add base meal/custom-food persistence

### Phase 2

- align persistence with `persistence-support`
- consume `GoalUpdated`
- consume `UserPreferencesUpdated`
- add target-aware daily summary

### Phase 3

- add richer food master data entities
- add meal item nutrient detail support
- expose food catalog read APIs

### Phase 4

- add carb and fat target writes upstream in `user-service`
- consume richer preference payloads when upstream expands them
- harden broker-backed relay, retry, and DLQ handling
