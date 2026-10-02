-- Documents now live in MinIO; the column holds the object key, not a URL.
ALTER TABLE documents RENAME COLUMN url TO object_key;

-- Public URL of the profile photo in the media bucket.
ALTER TABLE drivers ADD COLUMN photo_url VARCHAR(500);
