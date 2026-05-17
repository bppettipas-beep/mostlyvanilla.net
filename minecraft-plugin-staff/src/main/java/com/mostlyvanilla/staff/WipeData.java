package com.mostlyvanilla.staff;

import java.util.UUID;

public class WipeData {
    public final UUID staffUuid;
    public final UUID targetUuid;
    public final String targetName;
    public final int step; // 1 = first confirmation, 2 = final confirmation

    public WipeData(UUID staffUuid, UUID targetUuid, String targetName, int step) {
        this.staffUuid  = staffUuid;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.step       = step;
    }
}
