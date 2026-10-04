package mr.xe.delid.service;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.api.AbstractPackageLicenseDetector;
import mr.xe.delid.api.PackageLicenseDetectorFactory;
import mr.xe.delid.enums.PackageType;

@Slf4j
public class PackageLicenseDetectorFactoryImpl implements PackageLicenseDetectorFactory {

  private final Map<PackageType, AbstractPackageLicenseDetector> repositories;

  /**
   * The constructor uses Java SPI to discover all implementations and stores them in an immutable collection.
   */
  public PackageLicenseDetectorFactoryImpl() {
    final Map<PackageType, AbstractPackageLicenseDetector> map = new HashMap<>();
    final ServiceLoader<AbstractPackageLicenseDetector> loader =
        ServiceLoader.load(AbstractPackageLicenseDetector.class);
    for (AbstractPackageLicenseDetector repository : loader) {
      map.put(repository.getType(), repository);
    }

    this.repositories = Map.copyOf(map);
  }

  public AbstractPackageLicenseDetector get(PackageType type) {
    final AbstractPackageLicenseDetector repository = repositories.get(type);
    if (repository == null) {
      log.warn("Skip package due to missing repository type implementation {}", type);
    }
    return repository;
  }

}
