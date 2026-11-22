-- One-time bootstrap. Run as root against your local MySQL 8.4:
-- SECURITY: replace every <SET-BEFORE-RUNNING> with a secret from your secret store.
-- Do not run this file unchanged and do not commit the edited copy.
--   mysql -u root -p < db-setup/01-create-users.sql
--
-- Two users, deliberately:
--   lms_app      owns the schema and runs Flyway. Full rights on lms_catalog.
--   mcp_readonly is what the MySQL MCP server connects as. SELECT, and nothing else.
-- That grant is the first of the two independent controls on what the agent can
-- do to this database; the second is a preToolUse hook.

CREATE DATABASE IF NOT EXISTS lms_catalog
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'lms_app'@'%' IDENTIFIED BY '<SET-BEFORE-RUNNING>';
GRANT ALL PRIVILEGES ON lms_catalog.* TO 'lms_app'@'%';

CREATE USER IF NOT EXISTS 'mcp_readonly'@'%' IDENTIFIED BY '<SET-BEFORE-RUNNING>';
GRANT SELECT ON lms_catalog.* TO 'mcp_readonly'@'%';

FLUSH PRIVILEGES;
