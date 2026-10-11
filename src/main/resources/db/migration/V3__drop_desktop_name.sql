-- Desktops are identified by their description only; the name column is no longer used.
ALTER TABLE desktops
    DROP COLUMN name;
