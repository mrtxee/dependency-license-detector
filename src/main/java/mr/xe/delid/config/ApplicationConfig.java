package mr.xe.delid.config;

import java.util.Set;

import mr.xe.delid.core.AbstractUtility;

/**
 * Application configuration
 */
public final class ApplicationConfig extends AbstractUtility {

    /**
     * Required settings
     */
    public static final String DETECTED_LICENSES_FILE = getRequiredEnv("DETECTED_LICENSES_FILE", "detected-licenses.json");
    public static final String TEMP_DIR = getRequiredEnv("TEMP_DIR", "tmp");
    public static final String WORKDIR_BASE_PATH = getRequiredEnv("WORKDIR_BASE_PATH", "/");

    /**
     * Static settings
     */
    public static final Set<String> LICENSE_FILE_NAMES = Set.of(
            "license",
            "licenses"
    );
    public static final Set<String> COPYRIGHT_FILE_NAMES = Set.of(
            "notice",
            "copying",
            "copyright",
            "authors",
            "acknowledgements"
    );
    public static final String UNIDENTIFIED_LICENSE = "UNIDENTIFIED_LICENSE";
    /**
     * Two texts (licenses) are considered matching when their similarity reaches this level (range 0..100):
     */
    public static final int TEXT_MATCH_FACTOR = 94;

    private static String getRequiredEnv(String name) {
        return getRequiredEnv(name, null);
    }

    private static String getRequiredEnv(String name, String defaultValue) {
        final String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            if (defaultValue != null) {
                return defaultValue;
            }
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }

}
