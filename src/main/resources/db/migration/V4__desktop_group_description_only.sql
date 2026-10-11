-- Desktop groups are identified by their description only; the name column is no longer used.
-- Fails if existing groups share a description or have none: resolve those rows first.
ALTER TABLE desktop_groups
    DROP COLUMN name;
ALTER TABLE desktop_groups MODIFY description VARCHAR (255) NOT NULL;
ALTER TABLE desktop_groups
    ADD CONSTRAINT uq_desktop_groups_description UNIQUE (description);
