-- Endpoints are no longer created by hand: design-daas sends its route list at startup
-- (PUT /endpoints/sync). An endpoint missing from that list is marked obsolete instead of
-- deleted, so its rules and group memberships survive a route that is only gone for a while.
ALTER TABLE endpoints
    ADD COLUMN obsolete BOOLEAN NOT NULL DEFAULT FALSE;
