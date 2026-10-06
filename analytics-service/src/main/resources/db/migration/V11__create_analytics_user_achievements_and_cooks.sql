-- Badges v2 (Documents/architecture/me-achievements-spec.md, section 2). A badge is earned once and never
-- taken back: the first time a read computes it as earned, its date and evidence are written here, and
-- every later read shows the stored date even if the rollups it came from change afterwards. The three
-- retired badges (STREAK_7, STREAK_30, FIRST_WEIGHT_LOGGED) are kept here for whoever earned them.

create table analytics_user_achievements (
    user_id uuid not null,
    code varchar(40) not null,
    earned_on date not null,
    evidence_json varchar(1000),
    created timestamp with time zone not null,
    constraint pk_analytics_user_achievements primary key (user_id, code)
);

-- Recipe cooks, projected from nutrition-service RecipeCooked events, for the kitchen badges. A counter
-- row per user is enough: the badges need how many cooks and when the first and last one finished.

create table analytics_user_cooks (
    user_id uuid primary key,
    cooks integer not null default 0,
    first_cook_at timestamp with time zone,
    last_cook_at timestamp with time zone
);
