package com.mostlyvanilla.antidupe;

public class PlayerDupeData {

    // Total item count (player inventory + container) captured when a container was opened.
    // -1 means no session is active.
    public int sessionOpenTotal = -1;

    // Items the player legitimately picked up from the ground during the open session.
    public int sessionPickups = 0;
}
