package mr.xe.delid.service.detector;

import lombok.extern.slf4j.Slf4j;
import mr.xe.delid.api.AbstractPackageLicenseDetector;
import mr.xe.delid.enums.PackageType;

@Slf4j
public class DockerPackageLicenseDetector extends AbstractPackageLicenseDetector {
  @Override
  public PackageType getType() {
    return PackageType.DOCKER;
  }

}
