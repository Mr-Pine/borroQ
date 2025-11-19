package testcases.optionaldemo;

import de.mr_pine.optionaldemo.qual.*;
import java.util.Optional;

// Test subtyping relationships for the Optional Demo Checker.
class JdkAnnotations {
  void optionalOfNullable() {
    // :: error: (assignment.type.incompatible)
    @Present Optional<String> a = Optional.ofNullable(null);
  }

  void optionalOf() {
    @Present Optional<String> a = Optional.of("null");
  }

  void get(@MaybePresent Optional<String> x) {
    // :: error: (method.invocation.invalid)
    x.get();
  }
}
