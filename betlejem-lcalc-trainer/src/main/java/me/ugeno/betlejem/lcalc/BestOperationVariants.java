package me.ugeno.betlejem.lcalc;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by alwi on 08/11/2017.
 * All rights reserved.
 */
class BestOperationVariants {
    private Map<Integer, Op> bestOp = new LinkedHashMap<>();

    /**
     * ratio 1:3 1:1 3:1 1:0 => fractionOfBase: 0.25 0.50 0.70 1.00
     */
    void addVariant(double fractionOfBase, Op operation) {
        int ratioBorder = (int) (fractionOfBase * 100);
        bestOp.put(ratioBorder, operation);
    }

    /**
     * Primary & secondary amounts must be recalculated to same currency.
     *
     * @param fractionOfBase base : equivalent ratio
     * @return operation for particular range depending on primary:secondary ratio.
     */
    Op getOperationVariantFor(double fractionOfBase) {
        int ratio = (int) (fractionOfBase * 100);
        // ratio 1:3 1:1 3:1 1:0 => percentages of base currency: 0-25 25-50 50-75 (initial range = 75-100)
        for (Integer ratioBorder : bestOp.keySet()) { // assuming that the keySet is a sorted set & was filled in in order
            if (ratio <= ratioBorder) {
                return bestOp.get(ratioBorder);
            }
        }

        return null;
    }

    Collection<Op> getBestOpList() {
        return bestOp.values();
    }

    @Override
    public String toString() {
        StringBuilder info = new StringBuilder();
        for (int rangeBorder : bestOp.keySet()) {
            info.append(String.format("base<=%d :: %7s\t", rangeBorder, bestOp.get(rangeBorder).name()));
        }
        return info.toString();
    }
}
