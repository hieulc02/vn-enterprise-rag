CREATE USER keycloak WITH ENCRYPTED PASSWORD 'keycloak_password';
CREATE DATABASE keycloak_db OWNER keycloak;
GRANT ALL PRIVILEGES ON DATABASE keycloak_db TO keycloak;
\c keycloak_db;
GRANT ALL ON SCHEMA public to keycloak;