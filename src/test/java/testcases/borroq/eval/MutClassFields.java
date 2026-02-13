package testcases.borroq.eval;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

public interface MutClassFields {
    class Box {
        Box(int value) {
            this.value = value;
        }

        int value;
    }

    class Cat {
        Cat(@Mutable Box meows, @Mutable Box howHungry) {
            this.meows = meows;
            this.howHungry = howHungry;
        }

        @Mutable Box meows;
        @Mutable Box howHungry;
    }

    default void main() {
        Box box1 = new Box(52);
        Box box2 = new Box(99);
        @Immutable Cat nyan = new Cat(box1, box2);

        // :: error: permission.insufficient.shallow.assignment.target.receiver
        nyan.howHungry.value = 0;
    }
}
