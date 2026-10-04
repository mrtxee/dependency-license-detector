package mr.xe.delid.dto;

import java.util.List;

/**
 * Deserialization model for the local license classifier (licenses.json).
 */
public record SbtSpdxEntry(List<String> nameLikeIn, String spdxId, String type) {
}
