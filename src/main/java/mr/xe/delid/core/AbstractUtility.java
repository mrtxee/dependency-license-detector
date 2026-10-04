package mr.xe.delid.core;

/**
 * Base class for utility classes that must never be instantiated
 */
public abstract class AbstractUtility {
  protected AbstractUtility() {
    throw new UnsupportedOperationException("Instantiation is not allowed.");
  }
}
