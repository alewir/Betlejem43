package me.ugeno.betlejem.lcalc;

/**
 * Created by alwi on 25/10/2017.
 * All rights reserved.
 */
@SuppressWarnings("JavaDoc")
class Balance extends BalanceInternal {
    Balance(double initPri) {
        super(null, Op.PASS);

        updateValue(initPri, 0., 0.);
    }

    /**
     * @param parent    must not be null - given operation is calculated using this one as input.
     * @param operation
     * @param price     of secondary currency in value of primary currency e.g. BTC price in USD
     * @param maxLoss
     */
    Balance(Balance parent, Op operation, Double price, double maxLoss) {
        super(parent, operation);
        reBalance(operation, price, maxLoss, parent.getPri(), parent.getSec());
    }

    private void reBalance(Op operation, Double price, double maxLoss, double prevBalancePri, double prevBalanceSec) {
        double txCostPri = 0;
        double txAmntSec = 0;
        if (operation.getSuggestion() < 0) { // sell secondary currency, e.g. BTC
            txAmntSec = operation.getSuggestion() * prevBalanceSec;
            txCostPri = txAmntSec * price;
        } else if (operation.getSuggestion() > 0) { // buy secondary currency
            txCostPri = operation.getSuggestion() * prevBalancePri;
            txAmntSec = txCostPri / price;
        }

        double pri = prevBalancePri - txCostPri;
        double sec = prevBalanceSec + txAmntSec;

        // decrease balances with maximum allowed loss for single transaction...
        if (operation.getSuggestion() < 0) { // sell secondary => receive less primary than value of secondary given
            pri -= (Math.abs(txCostPri) * maxLoss / 100);
        } else if (operation.getSuggestion() > 0) { // buy secondary => receive less secondary than amount of primary paid
            sec -= (Math.abs(txAmntSec) * maxLoss / 100);
        }

        updateValue(pri, sec, price);
    }

    boolean isSignificant(Op op) {
        if (op.getSuggestion() < 0) { // sell secondary currency
            if (op.getSuggestion() * getSec() != 0) {
                return true;
            }
        } else if (op.getSuggestion() > 0) { // buy secondary currency
            if (op.getSuggestion() * getPri() != 0) {
                return true;
            }
        } else if (op == Op.PASS) {
            return true;
        }

        return false;
    }

    /**
     * @param targetPrimaryPart
     * @param price             of secondary currency in primary value e.g. USD per BTC
     */
    void split(double targetPrimaryPart, Double price) {
        double initWealth = getWealth();
        double pri = initWealth * targetPrimaryPart;
        double sec = (initWealth - pri) / price;

        updateValue(pri, sec, price);
    }

    /**
     * Sum of primary and secondary sub balance given as a value of primary currency.
     *
     * @return
     */
    double getWealth() {
        return getPri() + getEquivalent();
    }

    /**
     * Secondary currency in as a value of primary currency.
     *
     * @return
     */
    double getEquivalent() {
        return getSec() * getPrice();
    }

    public double getPri() {
        return super.getPri();
    }

    public double getSec() {
        return super.getSec();
    }

    /**
     * Price of secondary currency in value of primary currency (e.g. USD per BTC price).
     *
     * @return
     */
    public double getPrice() {
        return super.getPrice();
    }

    public Op getOperation() {
        return super.getOperation();
    }

    public Balance getParent() {
        return super.getParent();
    }

    @Override
    public String toString() {
        return String.format("pri=%9.5f sec=%12.8f price=%9.5f wealth=%9.2f %7s", getPri(), getSec(), getPrice(), getWealth(), getOperation());
    }
}
