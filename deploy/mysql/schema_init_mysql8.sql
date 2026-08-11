-- FinTwin MySQL 8.4 initial schema.
-- Apply only to a new, empty MYSQL_DATABASE. This script never drops or truncates data.
-- In compose.prod.yaml MySQL runs it only when creating a brand-new named volume.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS oauth_identities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    last_login_at DATETIME(6) NOT NULL,
    provider ENUM('GOOGLE', 'KAKAO') NOT NULL,
    provider_subject VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_oauth_identity_provider_subject UNIQUE (provider, provider_subject),
    INDEX idx_oauth_identity_user (user_id),
    CONSTRAINT fk_oauth_identities_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS financial_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    cash_assets DECIMAL(19,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    deposits DECIMAL(19,2) NOT NULL,
    investment_assets DECIMAL(19,2) NOT NULL,
    loan_interest_rate DECIMAL(7,4) NOT NULL,
    monthly_fixed_expenses DECIMAL(19,2) NOT NULL,
    monthly_income DECIMAL(19,2) NOT NULL,
    monthly_investments DECIMAL(19,2) NOT NULL,
    monthly_savings DECIMAL(19,2) NOT NULL,
    monthly_variable_expenses DECIMAL(19,2) NOT NULL,
    previous_profile_id BIGINT NULL,
    total_loan_balance DECIMAL(19,2) NOT NULL,
    profile_version INT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_financial_profiles_user_version UNIQUE (user_id, profile_version),
    CONSTRAINT fk_financial_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
