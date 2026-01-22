package testcases.borroq.eval;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;
import de.mr_pine.borroq.qual.release.Borrow;
import de.mr_pine.borroq.qual.release.Release;

public interface MutClassFields2 {
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

        void eat(@Immutable @Release Cat this) {
            // :: error: permission.insufficient.shallow.assignment.target.receiver
            meows.value = meows.value - 5;
        }
    }

    default void main() {
        Box box1 = new Box(52);
        Box box2 = new Box(99);
        Cat nyan = new Cat(box1, box2);
        nyan.eat();
    }
}
