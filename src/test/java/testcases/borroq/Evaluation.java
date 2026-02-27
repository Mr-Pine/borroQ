package testcases.borroq;

import de.mr_pine.borroq.qual.Scope;
import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

public interface Evaluation {
    static void use(@Immutable Object o){};
    static void useMut(@Mutable Object o){};

    static void useA(@Immutable A a){};
    static void useMutA(@Mutable A a){};

    static void basic() {
        @Mutable Object x = new Object();
        @Immutable Object y = x;
        @Mutable Object z = new Object();
        useMut(z);
        z = y;
        use(z);
        use(x);
    }

    class A {
        @Mutable
        Object x;
        @Immutable
        Object y;

        @Mutable Object getX(@Mutable @Scope("x") A this) {
             return x;
        }

        @Immutable Object getY(@Immutable @Scope("y") A this) {
            return y;
        }

        void setX(@Mutable @Scope("x") A this, @Mutable Object x) {
            this.x = x;
        }
    }

    static void fieldAccess(@Mutable A a) {
        @Mutable Object x = a.x;
        Object y = a.y;
        use(x);
        use(y);
        use(a);
        a = new @Mutable A();
        x = a.x;
        y = a.y;
        useMut(a);
    }

    static void parallelGetters(@Mutable A a) {
        @Mutable Object x = a.getX();
        Object y = a.getY();
        useMut(x);
        use(y);
    }

    class B {
        A a;
    }

    static void nested(B b) {
        Object x = b.a.x;
        Object y = b.a.y;
        use(x);
        use(y);
    }

    static void setters(@Mutable A a) {
        a.setX(new Object());
        // :: error: permission.insufficient.deep
        useMutA(a);
    }

    static void arrays() {
        @Mutable Object[] arr = new @Mutable Object[]{new Object(), new Object()};
        @Mutable Object x = arr[0];
        useMut(x);
    }

    static void arrays2(@Mutable A a1, @Mutable A a2, int i, int j) {
        @Mutable A[] arr = new @Mutable A[]{a1, a2};
        @Mutable Object x = arr[i].x;
        Object y = arr[j].y;
        useMut(x);
        use(y);
    }

    static void func(@Mutable A a, @Mutable B b, Object y) {
        a.y = y;
        b.a = a;
    }

    static void callFunc(@Mutable A a, @Mutable B b) {
        func(a, b, new Object());
        // :: error: permission.insufficient.shallow
        useA(a);
    }
}
