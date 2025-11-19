package testcases.optionaldemo;

import de.mr_pine.optionaldemo.qual.*;

import java.util.Optional;

// Test subtyping relationships for the Optional Demo Checker.
class FlowSensitive {
    void get(@MaybePresent Optional<String> x) {
        if (x.isPresent()) {
            x.get();
        } else {
            // :: error: (method.invocation.invalid)
            x.get();
        }
    }
}
