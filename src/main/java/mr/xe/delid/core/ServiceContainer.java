package mr.xe.delid.core;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.api.PurlRepositoryClient;
import mr.xe.delid.api.LicenseSignatureMatcher;
import mr.xe.delid.api.PackageLicenseDetectorFactory;
import mr.xe.delid.service.PurlRepositoryClientImpl;
import mr.xe.delid.service.LicenseSignatureMatcherImpl;
import mr.xe.delid.service.PackageLicenseDetectorFactoryImpl;

/**
 * Inversion of control (IoC) container over the service lifecycle.
 * Organised according to the Singleton Container pattern.
 */
@Slf4j
public final class ServiceContainer extends AbstractUtility {

    @Getter
    private static PurlRepositoryClient purlRepositoryClient;
    @Getter
    private static PackageLicenseDetectorFactory packageLicenseDetectorFactory;
    @Getter
    private static ObjectMapper objectMapper;
    @Getter
    private static LicenseSignatureMatcher licenseSignatureMatcher;

    public static void init() {
        purlRepositoryClient = new PurlRepositoryClientImpl();
        objectMapper = new ObjectMapper();
        objectMapperSetup();
        packageLicenseDetectorFactory = new PackageLicenseDetectorFactoryImpl();
        licenseSignatureMatcher = new LicenseSignatureMatcherImpl();
    }


    private static void objectMapperSetup() {
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.registerModule(new JavaTimeModule());
    }

}
