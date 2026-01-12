// https://github.com/ShawnWebDev/AdventOfCode25/blob/master/src/main/java/com/webdev/day1/Day1Modulo.java
package testcases.borroq;

import java.io.File;
import java.io.FileNotFoundException;

import java.util.Scanner;

public class AoCInference {
    public String runDay1() {
        int zeroCount = 0;
        String filename = "src/main/resources/day1_Input.txt";
        File input = new File(filename);
        int dialPos = 50;

        try {
            Scanner sc = new Scanner(input);
            Scanner s2 = sc;
            s2.hasNextLine();

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

        return String.valueOf((long) zeroCount);
    }
}