-- ============================================================
-- CookGenie initial schema
-- ============================================================

-- ------------------------------------------------------------
-- 1. 사용자 & 냉장고
-- ------------------------------------------------------------

CREATE TABLE users (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    email              VARCHAR(255) NOT NULL,
    nickname           VARCHAR(50)  NOT NULL,
    provider           VARCHAR(20),
    provider_id        VARCHAR(100),
    profile_image_url  VARCHAR(255),
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE fridges (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    owner_id    BIGINT      NOT NULL,
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    CONSTRAINT fk_fridges_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE fridge_members (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    fridge_id   BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    role        VARCHAR(20) NOT NULL,
    joined_at   DATETIME,
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    CONSTRAINT uk_fridge_members_fridge_user UNIQUE (fridge_id, user_id),
    CONSTRAINT fk_fridge_members_fridge FOREIGN KEY (fridge_id) REFERENCES fridges (id),
    CONSTRAINT fk_fridge_members_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- 2. 식재료 마스터 & 영양정보
-- ------------------------------------------------------------

CREATE TABLE categories (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(30) NOT NULL,
    icon_url    VARCHAR(255),
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE ingredients (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(50) NOT NULL,
    category_id      BIGINT,
    ingredient_type  VARCHAR(20) NOT NULL,
    default_unit     VARCHAR(10),
    barcode          VARCHAR(30),
    data_source      VARCHAR(20) NOT NULL,
    is_verified      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       DATETIME    NOT NULL,
    updated_at       DATETIME    NOT NULL,
    CONSTRAINT fk_ingredients_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_ingredients_barcode ON ingredients (barcode);

CREATE TABLE nutrition_infos (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    ingredient_id     BIGINT       NOT NULL,
    reference_amount  INT          NOT NULL DEFAULT 100,
    reference_unit    VARCHAR(10),
    calories          INT,
    carbohydrate_g    DECIMAL(5, 1),
    protein_g         DECIMAL(5, 1),
    fat_g             DECIMAL(5, 1),
    sugar_g           DECIMAL(5, 1),
    sodium_mg         DECIMAL(7, 1),
    fiber_g           DECIMAL(5, 1),
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,
    CONSTRAINT uk_nutrition_infos_ingredient UNIQUE (ingredient_id),
    CONSTRAINT fk_nutrition_infos_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE ingredient_units (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    ingredient_id     BIGINT        NOT NULL,
    unit              VARCHAR(10)   NOT NULL,
    gram_equivalent   DECIMAL(6, 1) NOT NULL,
    created_at        DATETIME      NOT NULL,
    updated_at        DATETIME      NOT NULL,
    CONSTRAINT fk_ingredient_units_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- 3. 냉장고 재고
-- ------------------------------------------------------------

CREATE TABLE fridge_items (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    fridge_id         BIGINT        NOT NULL,
    ingredient_id     BIGINT        NOT NULL,
    quantity          DECIMAL(6, 2) NOT NULL,
    unit              VARCHAR(10)   NOT NULL,
    storage_location  VARCHAR(20)   NOT NULL,
    purchased_at      DATE,
    expiry_date       DATE,
    memo              VARCHAR(200),
    created_at        DATETIME      NOT NULL,
    updated_at        DATETIME      NOT NULL,
    CONSTRAINT fk_fridge_items_fridge FOREIGN KEY (fridge_id) REFERENCES fridges (id),
    CONSTRAINT fk_fridge_items_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_fridge_items_fridge_expiry ON fridge_items (fridge_id, expiry_date);

-- ------------------------------------------------------------
-- 4. 레시피
-- ------------------------------------------------------------

CREATE TABLE recipes (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    title                  VARCHAR(100) NOT NULL,
    recipe_type            VARCHAR(20)  NOT NULL,
    cooking_type           VARCHAR(20),
    source_url             VARCHAR(255),
    author_nickname        VARCHAR(50),
    serving_size           INT,
    calories_per_serving   INT,
    carbohydrate_g         DECIMAL(5, 1),
    protein_g              DECIMAL(5, 1),
    fat_g                  DECIMAL(5, 1),
    view_count             INT          NOT NULL DEFAULT 0,
    like_count             INT          NOT NULL DEFAULT 0,
    save_count             INT          NOT NULL DEFAULT 0,
    created_at             DATETIME     NOT NULL,
    updated_at             DATETIME     NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE tags (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(20) NOT NULL,
    created_at  DATETIME    NOT NULL,
    updated_at  DATETIME    NOT NULL,
    CONSTRAINT uk_tags_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE recipe_tags (
    recipe_id  BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    PRIMARY KEY (recipe_id, tag_id),
    CONSTRAINT fk_recipe_tags_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id),
    CONSTRAINT fk_recipe_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE recipe_ingredients (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_id              BIGINT        NOT NULL,
    ingredient_id          BIGINT,
    ingredient_name_text   VARCHAR(50)   NOT NULL,
    quantity_text          VARCHAR(20),
    quantity_value         DECIMAL(6, 2),
    unit                   VARCHAR(10),
    created_at             DATETIME      NOT NULL,
    updated_at             DATETIME      NOT NULL,
    CONSTRAINT fk_recipe_ingredients_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id),
    CONSTRAINT fk_recipe_ingredients_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ------------------------------------------------------------
-- 5. 식단 기록 & 목표
-- ------------------------------------------------------------

CREATE TABLE meal_logs (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                  BIGINT      NOT NULL,
    meal_date                DATE        NOT NULL,
    meal_type                VARCHAR(20) NOT NULL,
    log_type                 VARCHAR(20) NOT NULL,
    recipe_id                BIGINT,
    servings                 DECIMAL(3, 1),
    total_calories           INT,
    total_carbohydrate_g     DECIMAL(5, 1),
    total_protein_g          DECIMAL(5, 1),
    total_fat_g              DECIMAL(5, 1),
    created_at               DATETIME    NOT NULL,
    updated_at               DATETIME    NOT NULL,
    CONSTRAINT fk_meal_logs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_meal_logs_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_meal_logs_user_date ON meal_logs (user_id, meal_date);

CREATE TABLE meal_log_items (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    meal_log_id      BIGINT        NOT NULL,
    ingredient_id    BIGINT,
    quantity         DECIMAL(6, 2) NOT NULL,
    unit             VARCHAR(10)   NOT NULL,
    calories         INT,
    carbohydrate_g   DECIMAL(5, 1),
    protein_g        DECIMAL(5, 1),
    fat_g            DECIMAL(5, 1),
    created_at       DATETIME      NOT NULL,
    updated_at       DATETIME      NOT NULL,
    CONSTRAINT fk_meal_log_items_meal_log FOREIGN KEY (meal_log_id) REFERENCES meal_logs (id),
    CONSTRAINT fk_meal_log_items_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE nutrition_goals (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                   BIGINT      NOT NULL,
    target_calories           INT,
    target_carbohydrate_g     DECIMAL(5, 1),
    target_protein_g          DECIMAL(5, 1),
    target_fat_g              DECIMAL(5, 1),
    weight_kg                 DECIMAL(5, 1),
    height_cm                 DECIMAL(5, 1),
    activity_level            VARCHAR(10),
    effective_date            DATE        NOT NULL,
    created_at                DATETIME    NOT NULL,
    updated_at                DATETIME    NOT NULL,
    CONSTRAINT fk_nutrition_goals_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_nutrition_goals_user_date ON nutrition_goals (user_id, effective_date);
