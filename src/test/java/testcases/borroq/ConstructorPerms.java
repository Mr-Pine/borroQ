package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

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
        B(@Mutable Object x) {

        }
    }

    default void b() {
        Object x = new Object();
        B b = new B(x);

        // :: error: permission.insufficient.shallow
        System.out.println(x);
        System.out.println(b);
    }

    class C {
        @Mutable A a;

        // :: error: permission.insufficient.deep
        @Mutable C() {
            this.a = new A();
            System.out.println(a);
        }
    }
}
