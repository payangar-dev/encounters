package com.payangar.encounters.config;

import com.payangar.encounters.Constants;
import com.payangar.encounters.platform.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * One-shot backup hook that runs before AutoConfig registration. Detects a
 * pre-migration YACL config file ({@code <configDir>/encounters.json5})
 * carrying any of the legacy flat fields ({@code lightningOverchargeChance},
 * {@code netherPortalInvasionMaxConcurrent}, {@code patrolSkirmishMobsPerSide})
 * and renames it to {@code encounters.json5.old} so Cloth can generate a
 * fresh config from defaults. Custom values are not migrated automatically —
 * the user consults the {@code .old} file to port them by hand.
 */
public final class LegacyConfigMigrator {

    private static final String LEGACY_FILE_NAME = Constants.MOD_ID + ".json5";
    private static final String BACKUP_FILE_NAME = LEGACY_FILE_NAME + ".old";

    private static final String[] LEGACY_MARKERS = {
            "lightningOverchargeChance",
            "netherPortalInvasionMaxConcurrent",
            "patrolSkirmishMobsPerSide"
    };

    private LegacyConfigMigrator() {}

    static void run() {
        Path configDir = Services.PLATFORM.getConfigDir();
        Path legacy = configDir.resolve(LEGACY_FILE_NAME);
        if (!Files.isRegularFile(legacy)) return;

        String content;
        try {
            content = Files.readString(legacy);
        } catch (IOException e) {
            Constants.LOG.warn("Could not read legacy config at {} — leaving it in place", legacy, e);
            return;
        }

        if (!containsLegacyMarker(content)) return;

        Path backup = configDir.resolve(BACKUP_FILE_NAME);
        try {
            Files.move(legacy, backup, StandardCopyOption.REPLACE_EXISTING);
            Constants.LOG.info("Legacy config backed up to {}, regenerated with new schema", backup.getFileName());
        } catch (IOException e) {
            Constants.LOG.warn("Could not back up legacy config {} to {}", legacy, backup, e);
        }
    }

    private static boolean containsLegacyMarker(String content) {
        for (String marker : LEGACY_MARKERS) {
            if (content.contains(marker)) return true;
        }
        return false;
    }
}
