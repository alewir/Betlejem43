package me.ugeno.betlejem.lcalc.tradebot.binance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.sun.istack.NotNull;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by alwi on 20/01/2022.
 * All rights reserved.
 */
@SuppressWarnings("WeakerAccess")
public class TxRegistry {
    @NotNull
    private final List<Tx> txList;

    @JsonCreator
    public TxRegistry(List<Tx> txList) {
        this.txList = txList;
    }

    @NotNull
    public List<Tx> getTxList() {
        return txList;
    }

    public int size() {
        return txList.size();
    }

    public void removeAll(List<Tx> pastTxToRemove) {
        txList.removeAll(pastTxToRemove);
    }

    public void add(Tx newTx) {
        txList.add(newTx);
    }

    public Stream<Tx> stream() {
        return txList.stream();
    }

    @Override
    public String toString() {
        return "TxRegistry{" +
                "txList=" + txList +
                '}';
    }
}
