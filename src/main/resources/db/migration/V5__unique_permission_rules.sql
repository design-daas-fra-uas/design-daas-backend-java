-- At most one rule per subject and target and kind. The API already refuses duplicates;
-- this makes the database refuse them too.
--
-- Existing duplicates are removed first, keeping the row the permission check uses today:
-- a deny wins over an allow, otherwise the oldest row (lowest id) is kept.
-- The extra derived table lets MariaDB delete from a table it also reads in the subquery.

DELETE FROM endpoint_user_access
WHERE id IN (SELECT id
             FROM (SELECT a.id
                   FROM endpoint_user_access a
                            JOIN endpoint_user_access b
                                 ON a.user_id = b.user_id
                                     AND a.endpoint_id = b.endpoint_id
                                     AND (b.allow_access < a.allow_access
                                         OR (b.allow_access = a.allow_access AND b.id < a.id))) AS duplicates);
ALTER TABLE endpoint_user_access
    ADD CONSTRAINT uq_endpoint_user_access UNIQUE (user_id, endpoint_id);

DELETE FROM endpoint_user_group_access
WHERE id IN (SELECT id
             FROM (SELECT a.id
                   FROM endpoint_user_group_access a
                            JOIN endpoint_user_group_access b
                                 ON a.user_group_id = b.user_group_id
                                     AND a.endpoint_id = b.endpoint_id
                                     AND (b.allow_access < a.allow_access
                                         OR (b.allow_access = a.allow_access AND b.id < a.id))) AS duplicates);
ALTER TABLE endpoint_user_group_access
    ADD CONSTRAINT uq_endpoint_user_group_access UNIQUE (user_group_id, endpoint_id);

DELETE FROM endpoint_group_user_access
WHERE id IN (SELECT id
             FROM (SELECT a.id
                   FROM endpoint_group_user_access a
                            JOIN endpoint_group_user_access b
                                 ON a.user_id = b.user_id
                                     AND a.endpoint_group_id = b.endpoint_group_id
                                     AND (b.allow_access < a.allow_access
                                         OR (b.allow_access = a.allow_access AND b.id < a.id))) AS duplicates);
ALTER TABLE endpoint_group_user_access
    ADD CONSTRAINT uq_endpoint_group_user_access UNIQUE (user_id, endpoint_group_id);

DELETE FROM endpoint_group_user_group_access
WHERE id IN (SELECT id
             FROM (SELECT a.id
                   FROM endpoint_group_user_group_access a
                            JOIN endpoint_group_user_group_access b
                                 ON a.user_group_id = b.user_group_id
                                     AND a.endpoint_group_id = b.endpoint_group_id
                                     AND (b.allow_access < a.allow_access
                                         OR (b.allow_access = a.allow_access AND b.id < a.id))) AS duplicates);
ALTER TABLE endpoint_group_user_group_access
    ADD CONSTRAINT uq_endpoint_group_user_group_access UNIQUE (user_group_id, endpoint_group_id);
