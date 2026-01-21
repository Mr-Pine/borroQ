package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Borrow;

public interface ConstructorPerms {
    class A {
        @Mutable
        A() {

        }

        @Immutable
        A(int x) {

        }
    }

    default void a() {
        @Immutable A x = new A();
        // :: error: permission.insufficient.shallow.assignment.expression
        @Mutable A y = new A(0);
    }

    class B {
        B(@Mutable @Borrow Object x) {

        }
    }

    default void b() {
        Object x = new Object();
        B b = new B(x);

        // :: error: permission.insufficient.shallow
        System.out.println(x);
        System.out.println(b);
    }
}
