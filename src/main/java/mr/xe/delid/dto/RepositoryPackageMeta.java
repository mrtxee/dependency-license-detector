package mr.xe.delid.dto;

import java.util.List;

/**
 * Container for the package metadata files
 *
 * @param manifest manifest file.
 * @param licenses license files.
 */
public record RepositoryPackageMeta(MetaFile manifest, List<MetaFile> licenses, List<MetaFile> copyrights) {
  public record MetaFile(String name, String text) {
  }
}
