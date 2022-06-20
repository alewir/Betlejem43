//package me.ugeno.betlejem.web.old;
//
//import org.fluentlenium.adapter.junit.FluentTest;
//import org.junit.Before;
//import org.junit.BeforeClass;
//import org.junit.Test;
//import org.openqa.selenium.WebDriver;
//import org.openqa.selenium.chrome.ChromeDriver;
//import org.openqa.selenium.chrome.ChromeOptions;
//import me.ugeno.betlejem.web.old.exchange.web.bitbay.pages.BB_PageExchangeBtcPln;
//import me.ugeno.betlejem.web.old.exchange.web.bitbay.pages.BB_PageHome;
//import me.ugeno.betlejem.web.old.exchange.web.bitbay.pages.BB_PageLoginStep1;
//import me.ugeno.betlejem.web.old.exchange.web.bitbay.pages.BB_PageLoginStep2;
//import me.ugeno.betlejem.web.old.exchange.web.bitbay.pages.entities.DataRow;
//import me.ugeno.betlejem.web.old.exchange.web.bitbay.pages.entities.DataTable;
//
//import java.math.BigDecimal;
//
//public class ExampleTestJ extends FluentTest {
//
//    @Override
//    public WebDriver newWebDriver() {
//        ChromeOptions options = new ChromeOptions();
//        options.addArguments("disable-infobars");
//        return new ChromeDriver(options);
//    }
//
//    private BB_PageExchangeBtcPln exchangeBtcPln;
//
//    @BeforeClass
//    public static void classSetUp() throws Exception {
//        String driverExePath = String.format("%s/%s", System.getProperty("user.dir"), Config.DRIVER_BIN_FILENAME);
//        System.out.println("Driver path: " + driverExePath);
//        System.setProperty(Config.DRIVER_ENV_VAR, driverExePath);
//    }
//
//    @Before
//    public void setUp() throws Exception {
//        BB_PageHome homePage = goTo(newInstance(BB_PageHome.class))
//                .waitForPageToLoad();
//
//        BB_PageLoginStep1 loginPageStep1 = homePage.goToLoginPage()
//                .waitForPageToLoad();
//
//        BB_PageLoginStep2 loginPageStep2 = loginPageStep1
//                .enterUsernameAndContinue("alewir")
//                .waitForPageToLoad();
//
//        exchangeBtcPln = loginPageStep2
//                .enterPasswordAndWaitForAction()
//                .waitForPageToLoad();
//
//        exchangeBtcPln
//                .selectBtcToPln();
//    }
//
//    @Test
//    public void testExecuteTx() {
//        System.out.println("PLN: " + exchangeBtcPln.readBalanceBtc());
//        System.out.println("BTC: " + exchangeBtcPln.readBalancePln());
//
//        BigDecimal avgPrice = getAvgFromBidAndAsk();
//        System.out.println("Avg price       : " + avgPrice);
//
//        DataRow firstBid = exchangeBtcPln.clickBid(0);
//        BigDecimal buyPrice = firstBid.getUnitPrice();
//
//        DataRow firstAsk = exchangeBtcPln.clickAsk(0);
//        BigDecimal sellPrice = firstAsk.getUnitPrice();
//
//        BigDecimal sellCost = sellPrice.subtract(avgPrice);
//        BigDecimal buyCost = buyPrice.subtract(avgPrice);
//
//        System.out.println("Can buy for     : " + buyPrice);
//        System.out.println("Can sell for    : " + sellPrice);
//        System.out.println("Max allowed diff: " + avgPrice.multiply(new BigDecimal(0.002d)).divide(BigDecimal.ONE, 2, BigDecimal.ROUND_HALF_DOWN));
//        System.out.println("Sell diff       : " + sellCost);
//        System.out.println("Buy diff        : " + buyCost);
//        System.out.println("Sell cost %     : " + sellCost.divide(avgPrice, 4, BigDecimal.ROUND_HALF_DOWN));
//        System.out.println("Buy cost  %     : " + buyCost.divide(avgPrice, 4, BigDecimal.ROUND_HALF_DOWN));
//
//        exchangeBtcPln.waitForPageToLoad();
//    }
//
//    @Test
//    public void testReadTablesBothWays() {
//        System.out.println("PLN: " + exchangeBtcPln.readBalanceBtc());
//        System.out.println("BTC: " + exchangeBtcPln.readBalancePln());
//
//        DataTable bids = exchangeBtcPln.readCurrentSellPrice();
//        DataTable bidsF = exchangeBtcPln.readCurrentSellPriceFast();
//        DataTable asks = exchangeBtcPln.readCurrentBuyPrice();
//        DataTable asksF = exchangeBtcPln.readCurrentBuyPriceFast();
//
//        BigDecimal bidPrice = bids.getWeightAvgPrice(2);
//        BigDecimal askPrice = asks.getWeightAvgPrice(2);
//
//        BigDecimal bidPriceF = bidsF.getWeightAvgPrice(2);
//        BigDecimal askPriceF = asksF.getWeightAvgPrice(2);
//
//        System.out.println("Sell price (BID)     : " + bidPrice.toPlainString());
//        System.out.println("Sell price (BID) fast: " + bidPriceF.toPlainString());
//        System.out.println("Total val  (BID) fast: " + bidsF.getTotalVolumeValue());
//
//        System.out.println("Buy price (ASK)      : " + askPrice.toPlainString());
//        System.out.println("Buy price (ASK)  fast: " + askPriceF.toPlainString());
//        System.out.println("Total val (ASK)  fast: " + asksF.getTotalVolumeValue());
//
//        BigDecimal askTotal = asks.getTotalAmount();
//        BigDecimal bidTotal = bids.getTotalAmount();
//
//        BigDecimal askTotalF = asksF.getTotalAmount();
//        BigDecimal bidTotalF = bidsF.getTotalAmount();
//
//        BigDecimal totalBoth = askTotal.add(bidTotal);
//        BigDecimal totalBothF = askTotalF.add(bidTotalF);
//
//        BigDecimal wAvg = askPrice.multiply(askTotal).add(bidPrice.multiply(bidTotal)).divide(totalBoth, 2, BigDecimal.ROUND_HALF_DOWN);
//        BigDecimal wAvgF = askPriceF.multiply(askTotalF).add(bidPriceF.multiply(bidTotalF)).divide(totalBothF, 2, BigDecimal.ROUND_HALF_DOWN);
//
//        System.out.println("Avg price     : " + wAvg);
//        System.out.println("Avg price fast: " + wAvgF);
//    }
//
//    private BigDecimal getAvgFromBidAndAsk() {
//        DataTable bids = exchangeBtcPln.readCurrentSellPriceFast();
//        DataTable asks = exchangeBtcPln.readCurrentBuyPriceFast();
//
//        BigDecimal bidPrice = bids.getWeightAvgPrice(2);
//        BigDecimal askPrice = asks.getWeightAvgPrice(2);
//
//        BigDecimal askTotal = asks.getTotalAmount();
//        BigDecimal bidTotal = bids.getTotalAmount();
//
//        BigDecimal totalBoth = askTotal.add(bidTotal);
//
//        System.out.println("PLN: " + exchangeBtcPln.readBalanceBtc());
//        System.out.println("BTC: " + exchangeBtcPln.readBalancePln());
//
//        System.out.println("Sell price (BID): " + bidPrice.toPlainString());
//        System.out.println("Total val  (BID): " + bids.getTotalVolumeValue());
//
//        System.out.println("Buy price (ASK) : " + askPrice.toPlainString());
//        System.out.println("Total val (ASK) : " + asks.getTotalVolumeValue());
//
//        System.out.println("Spread (avg)    : " + (askPrice.subtract(bidPrice)));
//        System.out.println("Spread (page)   : " + exchangeBtcPln.readSpread().toPlainString());
//
//        return askPrice.multiply(askTotal).add(bidPrice.multiply(bidTotal)).divide(totalBoth, 2, BigDecimal.ROUND_HALF_DOWN);
//    }
//}
