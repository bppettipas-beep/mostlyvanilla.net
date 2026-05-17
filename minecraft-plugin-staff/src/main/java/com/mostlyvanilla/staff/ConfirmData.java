package com.mostlyvanilla.staff;

import java.util.UUID;

public class ConfirmData {
    public final UUID staffUuid;
    public final UUID targetUuid;
    public final String targetName;
    public final StaffAction action;

    public ConfirmData(UUID staffUuid, UUID targetUuid, String targetName, StaffAction action) {
        this.staffUuid  = staffUuid;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.action     = action;
    }
}
