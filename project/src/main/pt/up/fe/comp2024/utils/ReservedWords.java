package pt.up.fe.comp2024.utils;

import java.util.HashMap;

public class ReservedWords {
    private static final HashMap<String, Boolean> reservedWordsMap = new HashMap<>();

    static {
        String[] reservedWords = {
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
                "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
                "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
                "new", "null", "package", "private", "protected", "public", "return", "short", "static",
                "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
                "void", "volatile", "while"
        };

        for (String word : reservedWords) {
            reservedWordsMap.put(word, true);
        }
    }

    public static boolean isReservedWord(String word) {
        return reservedWordsMap.containsKey(word);
    }
}