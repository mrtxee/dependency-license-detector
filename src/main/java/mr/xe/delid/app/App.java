package mr.xe.delid.app;

import mr.xe.delid.core.ServiceContainer;
import org.apache.commons.lang3.StringUtils;

/**
 * Entry point of the dependency license detection application.
 *
 * <p>Only assembles the dependencies (composition root) and delegates control to the
 * {@link PackageLicenseDetectionScenario}. Contains no business logic.
 *
 * <p>Initialisation errors, including missing required environment variables, are
 * deliberately not caught: the process exits with a non-zero exit code and prints a stack
 * trace, instead of terminating successfully without a result.
 */
public final class App {

    private static final String USAGE = "Usage: App <package-purl> [repository-url]";

    private App() {
    }

    /**
     * Initialises the services and detects the licenses of a single package.
     *
     * @param args command line arguments: {@code args[0]} is the package PURL and is required,
     *             {@code args[1]} is the repository base URL and falls back to the
     *             {@code ARTIFACTORY_BASE_URL} environment variable when omitted or blank; the
     *             remaining configuration is read from environment variables
     * @throws IllegalArgumentException if the package PURL argument is missing
     */
    public static void main(String[] args) {
        final String packagePurl = packagePurlOf(args);
        final String repositoryUrl = repositoryUrlOf(args);
        ServiceContainer.init();
        PackageLicenseDetectionScenario.detectLicenses(packagePurl, repositoryUrl);
    }

    private static String packagePurlOf(String[] args) {
        if (args == null || args.length < 1 || StringUtils.isBlank(args[0])) {
            throw new IllegalArgumentException(USAGE);
        }
        return args[0];
    }

    private static String repositoryUrlOf(String[] args) {
        return args.length < 2 ? null : args[1];
    }

}
