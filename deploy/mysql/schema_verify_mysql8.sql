-- Read-only checks to run after schema initialization and before a deployment.
SELECT table_name, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('users', 'oauth_identities', 'financial_profiles')
ORDER BY table_name;

SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('users', 'oauth_identities', 'financial_profiles')
ORDER BY table_name, ordinal_position;

SELECT constraint_name, table_name, constraint_type
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN ('users', 'oauth_identities', 'financial_profiles')
ORDER BY table_name, constraint_type, constraint_name;

SELECT COUNT(*) AS orphan_oauth_identity_count
FROM oauth_identities identity_row
LEFT JOIN users user_row ON user_row.id = identity_row.user_id
WHERE user_row.id IS NULL;

SELECT COUNT(*) AS orphan_financial_profile_count
FROM financial_profiles profile_row
LEFT JOIN users user_row ON user_row.id = profile_row.user_id
WHERE user_row.id IS NULL;

SELECT user_id, profile_version, COUNT(*) AS duplicate_count
FROM financial_profiles
GROUP BY user_id, profile_version
HAVING COUNT(*) > 1;
