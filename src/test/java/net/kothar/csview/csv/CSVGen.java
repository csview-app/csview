package net.kothar.csview.csv;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class CSVGen {
    public static void main(String[] args) throws FileNotFoundException {
        var rows = 7_000_000;
        var cols = new String[]{
                "2992929",
                "sdifjisdf8",
                "skskskks",
                "",
                "",
                "",
                "",
                ""
        };

        var output = new PrintWriter(args[0]);
        for (int row = 0; row < rows; row++) {
            output.println(String.join(",", cols));
        }
    }
}
