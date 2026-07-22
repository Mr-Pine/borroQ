package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

public class ImmutFieldDeepAssignment {
    static class A {
        A() {
            b = new B();
        }
        @Immutable B b;
    }

    static class B {
        int x;
    }

    public static void main(String[] args) {
        @Mutable A a = new A();
        // :: error: permission.insufficient.shallow
        a.b.x = 10;
    }
}
