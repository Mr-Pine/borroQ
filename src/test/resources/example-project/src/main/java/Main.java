import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

public class Main {
    static class A {
        A() {
            b = new B();
        }
        B b;
    }

    static class B {
        int x;
    }

    public static void main(String[] args) {
        @Mutable A a = new A();
        @Mutable B c = a.b;
        c.x = 5;
        @Immutable B d = a.b;
        // BorroQ errors here because a.b is aliased by d
        a.b.x = 10;
        System.out.println(d.x);
    }
}
