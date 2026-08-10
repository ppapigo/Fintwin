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

-- 2. Preserve every existing user ID while enabling generated IDs for new OAuth users.
ALTER TABLE users
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;

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

SELECT COUNT(*) AS oauth_identity_count FROM oauth_identities;
SELECT COUNT(*) AS user_count FROM users;
SELECT COUNT(*) AS financial_profile_count FROM financial_profiles;

SELECT provider, provider_subject, COUNT(*) AS duplicate_count
FROM oauth_identities
GROUP BY provider, provider_subject
HAVING COUNT(*) > 1;

SELECT oi.id
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
