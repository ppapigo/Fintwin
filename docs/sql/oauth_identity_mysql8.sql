-- FinTwin OAuth Identity schema transition for MySQL 8.4
-- Review and back up the target database before running. This file is documentation only.

-- 1. Preflight: these queries must return no duplicate IDs and no orphan profile users.
SELECT id, COUNT(*) AS duplicate_count
FROM users
GROUP BY id
HAVING COUNT(*) > 1;

SELECT fp.user_id, COUNT(*) AS orphan_profile_count
FROM financial_profiles fp
LEFT JOIN users u ON u.id = fp.user_id
WHERE u.id IS NULL
GROUP BY fp.user_id;

SELECT TABLE_NAME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'oauth_identities';

SHOW CREATE TABLE users;

-- Capture the existing Financial Profile -> User FK name before changing the referenced column.
-- The dynamic statement preserves the deployed constraint name instead of assuming a Hibernate-generated name.
SET @profile_user_fk = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'financial_profiles'
      AND COLUMN_NAME = 'user_id'
      AND REFERENCED_TABLE_NAME = 'users'
      AND REFERENCED_COLUMN_NAME = 'id'
    LIMIT 1
);

SET @profile_user_fk_existed = (@profile_user_fk IS NOT NULL);
SET @profile_user_fk = COALESCE(@profile_user_fk, 'fk_financial_profile_user');
SET @drop_profile_user_fk = IF(
    @profile_user_fk_existed,
    CONCAT('ALTER TABLE financial_profiles DROP FOREIGN KEY `',
           REPLACE(@profile_user_fk, '`', '``'), '`'),
    'SELECT 1'
);
PREPARE drop_profile_user_fk FROM @drop_profile_user_fk;
EXECUTE drop_profile_user_fk;
DEALLOCATE PREPARE drop_profile_user_fk;

-- 2. Preserve every existing user ID while enabling generated IDs for new OAuth users.
ALTER TABLE users
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

-- Restore the same FK name and referential behavior immediately after changing users.id.
SET @restore_profile_user_fk = CONCAT(
    'ALTER TABLE financial_profiles ADD CONSTRAINT `',
    REPLACE(@profile_user_fk, '`', '``'),
    '` FOREIGN KEY (user_id) REFERENCES users (id) ON UPDATE NO ACTION ON DELETE NO ACTION'
);
PREPARE restore_profile_user_fk FROM @restore_profile_user_fk;
EXECUTE restore_profile_user_fk;
DEALLOCATE PREPARE restore_profile_user_fk;

-- 3. Create the provider identity table. Subject comparison is case-sensitive.
CREATE TABLE oauth_identities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_login_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_oauth_identity_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT fk_oauth_identity_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_oauth_identity_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 4. Postflight: verify structure, constraints, and unchanged existing user/profile counts.
SHOW CREATE TABLE oauth_identities;

SELECT COLUMN_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'oauth_identities'
  AND COLUMN_NAME = 'provider_subject';

SELECT COUNT(*) AS oauth_identity_count FROM oauth_identities;
SELECT COUNT(*) AS user_count FROM users;
SELECT COUNT(*) AS financial_profile_count FROM financial_profiles;

SELECT COUNT(*) - COUNT(DISTINCT provider, provider_subject) AS duplicate_identity_count
FROM oauth_identities;

SELECT COUNT(*) AS orphan_identity_count
FROM oauth_identities oi
LEFT JOIN users u ON u.id = oi.user_id
WHERE u.id IS NULL;

-- 5. Safe rollback procedure:
--    a. Disable FINTWIN_OAUTH_ENABLED and deploy the previous application version first.
--    b. Keep users.id AUTO_INCREMENT; explicit legacy IDs remain valid and no data is lost.
--    c. Preserve OAuth rows by renaming instead of dropping the table.
--    d. Drop the renamed table only after a verified backup and retention decision.
-- Replace the suffix with an approved deployment identifier before running.
-- RENAME TABLE oauth_identities TO oauth_identities_rollback_YYYYMMDDHHMM;
