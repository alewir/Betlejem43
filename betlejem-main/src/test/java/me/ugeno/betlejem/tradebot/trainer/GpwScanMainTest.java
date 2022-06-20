package me.ugeno.betlejem.tradebot.trainer;

import org.assertj.core.api.AbstractBigDecimalAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GpwScanMainTest {

    @Test
    void normalize() {
        BigDecimal[] boundaries = {
                new BigDecimal(-3),
                new BigDecimal(-2),
                new BigDecimal(-1),
                BigDecimal.ZERO,
                new BigDecimal(1),
                new BigDecimal(2),
                new BigDecimal(3),
                new BigDecimal(Integer.MAX_VALUE)
        };

        checkRank(boundaries, -3.1, 1);
        checkRank(boundaries, -3, 2);
        checkRank(boundaries, -2.99, 2);
        checkRank(boundaries, -1.5, 3);
        checkRank(boundaries, -0.1, 4);
        checkRank(boundaries, 0, 5);
        checkRank(boundaries, 0.01, 6);
        checkRank(boundaries, .1, 6);
        checkRank(boundaries, 0.99, 6);
        checkRank(boundaries, 1, 7);
        checkRank(boundaries, 1.5, 7);
        checkRank(boundaries, 2, 8);
        checkRank(boundaries, 3., 9);
    }

    private AbstractBigDecimalAssert<?> checkRank(BigDecimal[] boundaries, Integer delta, Integer expectedRank) {
        return checkRank(boundaries, Double.valueOf(delta), expectedRank);
    }

    private AbstractBigDecimalAssert<?> checkRank(BigDecimal[] boundaries, Double delta, Integer expectedRank) {
        System.out.println(delta + " <---- " + expectedRank);
        return assertThat(TrainingDataGenerator.normalize(new BigDecimal(delta), boundaries)).isEqualTo(new BigDecimal(expectedRank));
    }
}
