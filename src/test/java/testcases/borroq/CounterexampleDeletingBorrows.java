package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Borrow;

public class CounterexampleDeletingBorrows {
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
        Box box = new Box(0);
        B value = new B(box);
        A a = new A(value);

        B extracted = a.b;
        Box extractedBox = extracted.x;

        // :: error: permission.insufficient.shallow.borrowed
        Box differentExtractedBox = a.b.x;

        extractedBox.value = 1;

        int val = differentExtractedBox.value;
        System.out.println(val);
    }
}
