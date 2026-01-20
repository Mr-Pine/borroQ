package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Borrow;

public class CounterexampleIf {
    static class Box {
        Box(int value) {
            this.value = value;
        }
        int value;
    }
    static class B {
        B(@Mutable @Borrow Box x) {
            this.x = x;
        }
        @Mutable Box x;
    }
    static class A {
        A(@Mutable @Borrow B b) {
            this.b = b;
        }

        @Mutable B b;
    }

    public static void main(String[] args) {
        Box box0 = new Box(0);
        Box box1 = new Box(1);
        B value = new B(box0);
        B otherValue = new B(box1);

        
    }
}
