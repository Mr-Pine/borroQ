package testcases.borroq.eval;

import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Move;

public interface Array {
    class Box {
        Box(int value) {
            this.value = value;
        }

        int value;
    }

    default void drop(@Mutable @Move Box value) {
    }

    default void main() {
        @Mutable Box @Mutable [] values = new Box[]{new Box(0), new Box(1), new Box(2)};
        Box x = values[0];
        Box y = values[1];

        drop(x);
        drop(y);
    }
}
