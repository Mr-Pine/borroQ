package testcases.borroq.eval;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Borrow;
import de.mr_pine.borroq.qual.release.Release;

public interface MutClassFields {
    class Box {
        Box(int value) {
            this.value = value;
        }

        int value;
    }

    class Cat {
        Cat(@Mutable @Borrow Box meows, @Mutable @Borrow Box howHungry) {
            this.meows = meows;
            this.howHungry = howHungry;
        }

        @Mutable Box meows;
        @Mutable Box howHungry;
    }

    default void main() {
        @Immutable Cat nyan = new Cat(new Box(52), new Box(99));

        // :: error: permission.insufficient.shallow.assignment.target.receiver
        nyan.howHungry.value = 0;
    }
}
