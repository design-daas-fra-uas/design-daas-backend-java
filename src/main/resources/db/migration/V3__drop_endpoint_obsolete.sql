-- The sync from design-daas now deletes endpoints it no longer has (with their rules), so the
-- obsolete flag from V2 is not needed any more.
ALTER TABLE endpoints
    DROP COLUMN obsolete;
