package testcases.artefactEvaluation;

import de.mr_pine.borroq.qual.mutability.*;

public class BorrowedArguments {
    class A {
        @Mutable
        B b;
    }

    class B {
        String xy;
    }

    void main(@Mutable A a) {
        @Mutable B b = a.b;
        // :: error: permission.insufficient.deep
        foo(a);
        System.out.println(b.xy);
    }

    void foo(@Mutable A a) {
        a.b.xy = "Hello, World!";
    }
}
