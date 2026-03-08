package testcases.borroq.misc;

import de.mr_pine.borroq.qual.mutability.Mutable;

public interface ArrayMut {
    class Box {
        Box(int value) {
            this.value = value;
        }

        int value;
    }

    default void drop(@Mutable Box value) {
    }

    default void main() {
        Box box0 = new Box(0);
        Box box1 = new Box(1);
        Box box2 = new Box(2);
        @Mutable Box [] values = new @Mutable Box[]{box0, box1, box2};
        @Mutable Box x = values[0];

        drop(x);
    }
}
