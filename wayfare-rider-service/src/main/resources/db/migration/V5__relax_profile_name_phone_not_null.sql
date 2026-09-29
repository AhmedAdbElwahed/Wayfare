-- Profiles are now created by the AccountRegistered consumer at registration
-- time, before the rider has told us anything about themselves — so the row
-- has to be insertable with only an id. name/phone become required again at
-- the API layer (CreateRiderRequest/@NotBlank), not in the schema.
ALTER TABLE profiles ALTER COLUMN name DROP NOT NULL;
ALTER TABLE profiles ALTER COLUMN phone DROP NOT NULL;
