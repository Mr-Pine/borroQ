package testcases.optionaldemo;

import de.mr_pine.optionaldemo.qual.*;
import java.util.Optional;

// Test subtyping relationships for the Optional Demo Checker.
class SubtypingTest {
  void allSubtypingRelationships(@Present Optional<String> x, @MaybePresent Optional<String> y) {
    @MaybePresent Optional<String> a = x;
    a = y;
    @Present Optional<String> b = x;
    // :: error: (assignment.type.incompatible)
    b = y;
  }
}
