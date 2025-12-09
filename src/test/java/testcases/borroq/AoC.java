// https://github.com/ShawnWebDev/AdventOfCode25/blob/master/src/main/java/com/webdev/day1/Day1Modulo.java
package testcases.borroq;

import de.mr_pine.borroq.qual.mutability.Immutable;
import de.mr_pine.borroq.qual.mutability.Mutable;

import java.io.File;
import java.io.FileNotFoundException;

import java.util.Scanner;

public interface AoC {
    public static int runDay1() {
        int zeroCount = 0;
        @Immutable String filename = "src/main/resources/day1_Input.txt";
        @Immutable File input = new File(filename);
        int dialPos = 50;

        try {
            @Mutable Scanner sc = new Scanner(input);

            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                char firstChar = line.charAt(0);
                String firstCharString = String.valueOf(firstChar);
                String leftPrefix = "L";
                int direction = firstCharString.equals(leftPrefix) ? -1 : 1;
                String amountSubstring = line.substring(1);
                int amount = Integer.parseInt(amountSubstring);

                for (int i = 0; i < amount; i++) {
                    dialPos = (dialPos + direction) % 100;

                    if (dialPos == 0) {
                        zeroCount++;
                    }
                }
            }
            sc.close();

        } catch (FileNotFoundException e){
            String errorMessage = "File not found";
            System.out.println(errorMessage);
        }

        return zeroCount;
    }
}