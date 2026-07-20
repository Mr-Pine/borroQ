package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.mutability.*;

public class FlexibleArgumentExtension {
    class A {
        int xy;
    }

    void main(@Mutable A a) {
        a.xy = consumeValue(getValue());
    }

    static int getValue() {
        return 10;
    }

    static int consumeValue(int x) {
        return x + 5;
    }
}
