package mr.xe.delid.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Deserialization model for the required components list of the product under test (required-software.json).
 */
@Data
public class RequiredSoftwareFile {
  private List<Group> groups;

  @Data
  public static class Group {
    private String title;
    @JsonProperty("isMandatory")
    private boolean mandatory;
    private List<Alternative> alternatives;

    @Data
    public static class Alternative {
      private String id;
      private boolean areVersionsAboveSuitable;
    }
  }
}
