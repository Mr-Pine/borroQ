import edu.kit.kastel.property.subchecker.exclusivity.qual.EnsuresMaybeAliased;
import edu.kit.kastel.property.subchecker.exclusivity.qual.MaybeAliased;
import edu.kit.kastel.property.subchecker.exclusivity.qual.Unique;

public interface UniquenessChecker {
    static void use(@MaybeAliased Object o) {
    }

    static void useMut(@Unique Object o) {
    }

    static void basic() {
        @Unique Object x = new Object();
        @MaybeAliased Object y = x;
        @Unique Object z = new Object();
        useMut(z);
        z = y;
        use(z);
        use(x);
    }

    class A {
        @Unique
        Object x;
        @MaybeAliased
        Object y;

        A() {
            this.x = new Object();
            this.y = new Object();
        }

        @Unique
        Object getX(@Unique A this) {
            return x;
        }

        @MaybeAliased
        Object getY(@MaybeAliased A this) {
            return y;
        }

        void setX(@Unique A this, @Unique Object x) {
            this.x = x;
        }
    }

    static void fieldAccess(@Unique A a) {
        Object x = a.x;
        Object y = a.y;
        use(x);
        use(y);
        use(a);
        @Unique A a2 = new @Unique A();
        x = a2.x;
        y = a2.y;
        useMut(a2);
    }

    class C {
        @Unique A a1;
        @Unique A a2;

        void useMut(@Unique C this) {
        }
    }

    static void fieldAssignment(@Unique C c) {
        @Unique C c2 = c;
        c2.a1.x = new Object();
        @Unique A a = c.a2;
        a.x = new Object();
        c.useMut();
    }

    static void parallelGetters() {
        @Unique A a = new A();
        @Unique Object x = a.getX();
        Object y = a.getY();
        useMut(x);
        use(y);
    }

    class B {
        A a;

        B() {
            this.a = new A();
        }
    }

    static void nested(B b) {
        Object x = b.a.x;
        Object y = b.a.y;
        use(x);
        use(y);
    }

    static void setters(@Unique A a) {
        a.setX(new Object());
        use(a);
    }

    // Note: Arrays are ignored by the uniqueness checker
    //       Even programs violation the restrictions
    //       are accepted.
    static void arrays() {
        @Unique Object[] arr = new @Unique Object[]{new Object(), new Object()};
        @Unique Object x = arr[0];
        useMut(x);
    }

    static void arrays2(@Unique A a1, @Unique A a2, int i, int j) {
        @Unique A[] arr = new @Unique A[]{a1, a2};
        @Unique Object x = arr[i].x;
        Object y = arr[j].y;
        useMut(x);
        use(y);
    }

    static void func(@Unique A a, @Unique B b, Object y) {
        a.y = y;
        b.a = a;
    }

    static void callFunc(@Unique A a, @Unique B b) {
        func(a, b, new Object());
        use(a);
    }
}
