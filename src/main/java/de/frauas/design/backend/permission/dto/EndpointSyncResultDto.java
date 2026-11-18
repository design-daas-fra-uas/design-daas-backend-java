package de.frauas.design.backend.permission.dto;

import lombok.Builder;
import lombok.Data;

/** Response payload for PUT /endpoints/sync: how many endpoints the sync changed. */
@Data
@Builder
public class EndpointSyncResultDto {
    /** Names that were not known before. */
    private int added;
    /** Known names whose description changed. */
    private int updated;
    /** Known names that are no longer in the list (deleted with their rules). */
    private int deleted;
}
