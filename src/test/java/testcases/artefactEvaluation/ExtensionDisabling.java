package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

public class ExtensionDisabling {
    // :: error: extension.used
    ExtensionDisabling() {
    }

    // :: error: extension.used
    void drop_immut(@Immutable Object o) {
    }

    // :: error: extension.used
    void main() {
        Object l = new Object();
        // :: error: extension.used
        while (true) {
            // :: error: extension.used
            drop_immut(l);
        }
    }

    // :: error: extension.used
    class A {
        int xy;
    }

    // :: error: extension.used
    void foo(@Mutable FlexibleArgumentExtension.A a) {
        // :: error: extension.used
        // :: error: permission.insufficient.shallow
        a.xy = consumeValue(getValue());
    }

    // :: error: extension.used
    static int getValue() {
        // :: error: extension.used
        return 10;
    }

    // :: error: extension.used
    static int consumeValue(int x) {
        // :: error: extension.used
        return x + 5;
    }

    // :: error: extension.used
    static void ensureMutable(@Mutable Object x) {
    }

    // :: error: extension.used
    void bar() {
        @Immutable Object x = new Object();

        // :: error: extension.used
        // :: error: type.parameter.mutability.incompatible
        @Mutable Object[] arr = new @Immutable Object[]{x};

        // :: error: extension.used
        @Mutable Object y = arr[0];
        ensureMutable(y);
    }
}
