package testcases.borroq;

import de.mr_pine.borroq.qual.Scope;
import de.mr_pine.borroq.qual.mutability.Mutable;

public interface ArgumentPermissionChaining {
    class A {
        @Mutable Object x;
        @Mutable Object y;
    }

    default void doSomething(@Mutable Object a, @Mutable Object b) {}
    default void doSomethingElse(@Mutable @Scope("x") A a, @Mutable @Scope("y") A b) {}
    default void doSomethingElseElse(@Mutable @Scope("x") A a, @Mutable @Scope("x") A b) {}

    default void main() {
        @Mutable A a = new A();
        doSomething(a.x, a.y);
        a = new @Mutable A();
        // :: error: permission.insufficient.deep
        doSomething(a.x, a.x);
        a = new @Mutable A();
        doSomethingElse(a, a);
        a = new @Mutable A();
        // :: error: permission.insufficient.deep
        doSomethingElseElse(a, a);
    }
}
