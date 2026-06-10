package com.mostlyvanilla.crates;

import java.util.List;

public record CrateType(String id, String displayName, String keyType, List<CrateReward> rewards) {}
