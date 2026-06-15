package com.mostlyvanilla.staff;

import java.util.UUID;

public class TransferData {
    final UUID staffUuid;
    final UUID fromUuid;
    final UUID toUuid;
    final String fromName;
    final String toName;
    int step;

    public TransferData(UUID staffUuid, UUID fromUuid, String fromName, UUID toUuid, String toName, int step) {
        this.staffUuid = staffUuid;
        this.fromUuid  = fromUuid;
        this.fromName  = fromName;
        this.toUuid    = toUuid;
        this.toName    = toName;
        this.step      = step;
    }
}
