package me.ugeno.betlejem.lcalc;

public enum Op {
    SELL(-1.),
    PASS(0.),
    BUY(1.),
    ;

    private final double suggestion;

    Op(double v) {
        suggestion = v;
    }

    public static Op parseFromStr(String valStr) {
        for (Op v : values()) {
            if (String.format("%+.2f", v.getSuggestion()).equalsIgnoreCase(valStr)) {
                return v;
            }
        }

        throw new RuntimeException("Invalid operation given to parse from string: " + valStr);
    }

    @Override
    public String toString() {
        return String.format("%8s=%5.2f", name(), suggestion);
    }

    public double getSuggestion() {
        return suggestion;
    }

    public int[] asOneHot() {
        if (suggestion < 0) {
            return new int[]{1, 0, 0}; // sell
        } else if (suggestion == 0) {
            return new int[]{0, 1, 0}; // pass
        } else {
            return new int[]{0, 0, 1}; // buy
        }
    }
}


