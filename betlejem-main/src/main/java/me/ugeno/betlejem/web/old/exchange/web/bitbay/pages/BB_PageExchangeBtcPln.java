//package me.ugeno.betlejem.web.old.exchange.web.bitbay.pages;
//
//import org.fluentlenium.core.FluentPage;
//import org.fluentlenium.core.annotation.PageUrl;
//import org.fluentlenium.core.domain.FluentList;
//import org.fluentlenium.core.domain.FluentWebElement;
//import org.openqa.selenium.WebElement;
//import org.openqa.selenium.support.FindBy;
//import me.ugeno.betlejem.web.old.exchange.web.bitbay.pages.entities.DataRow;
//import me.ugeno.betlejem.web.old.exchange.web.bitbay.pages.entities.DataTable;
//
//import java.math.BigDecimal;
//import java.util.List;
//import java.util.concurrent.TimeUnit;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//import static org.fluentlenium.core.filter.FilterConstructor.withClass;
//import static me.ugeno.betlejem.web.old.Config.DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT;
//
///**
// * Created by alwi on 09/12/2017.
// * All rights reserved.
// */
//@PageUrl("https://app.bitbay.net/market/btc-pln")
//public class BB_PageExchangeBtcPln extends FluentPage {
//
//    public static final String ELEM_ID_TABLE_ASK_DATA = "#ask-data";
//    public static final String ELEM_ID_TABLE_BID_DATA = "#me-data";
//
//    @FindBy(id = "currency-pair-button")
//    private FluentWebElement btnCurrencyPair;
//
//    @FindBy(xpath = "//*[@id='market-search-tabs']/li[contains(text(), 'PLN')]")
//    private FluentWebElement btnFromPln;
//
//    @FindBy(xpath = "//*[@id='currencies-list']/*/div[@data-currency='btc']/currency/i")
//    private FluentWebElement btnToBtc;
//
//    @FindBy(xpath = "//second-value[./span[contains(text(),'PLN')]]")
//    private FluentWebElement lblBalancePln;
//
//    @FindBy(xpath = "//first-value[./span[contains(text(),'BTC')]]")
//    private FluentWebElement lblBalanceBtc;
//
//    @FindBy(id = "spread-value")
//    private FluentWebElement lblSpreadValue;
//
//    @FindBy(xpath = "//*[@id='me-data']/*/div[contains(@class, 'data-row')]")
//    FluentList<FluentWebElement> tableBidElemList;
//
//    @FindBy(xpath = "//*[@id='ask-data']/*/div[contains(@class, 'data-row')]")
//    FluentList<FluentWebElement> tableAskElemList;
//
//    public BB_PageExchangeBtcPln selectBtcToPln() {
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnCurrencyPair).present();
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnCurrencyPair).clickable();
//        btnCurrencyPair.click();
//
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnFromPln).present();
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnFromPln).clickable();
//        btnFromPln.click();
//
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnToBtc).present();
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnToBtc).clickable();
//        btnToBtc.click();
//
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnCurrencyPair).present();
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnCurrencyPair).clickable();
//        btnCurrencyPair.click();
//
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(tableBidElemList).present();
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(tableBidElemList).size().greaterThan(1);
//
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(tableAskElemList).present();
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(tableAskElemList).size().greaterThan(1);
//
//        return this;
//    }
//
//    public BigDecimal readBalancePln() {
//        String balancePlnStr = lblBalancePln.text();
//        System.out.printf("Balance PLN: '%s'%n", balancePlnStr);
//
//        return new BigDecimal(balancePlnStr.replaceAll("PLN", "").trim());
//    }
//
//    public BigDecimal readBalanceBtc() {
//        String balanceBtcStr = lblBalanceBtc.text();
//        System.out.printf("Balance BTC: '%s'%n", balanceBtcStr);
//
//        return new BigDecimal(balanceBtcStr.replaceAll("BTC", "").trim());
//    }
//
//    public BigDecimal readSpread() {
//        String spreadValStr = lblSpreadValue.text();
//        return new BigDecimal(spreadValStr.trim());
//    }
//
//    /**
//     * Average from visible BIDs.
//     */
//    public DataTable readCurrentSellPrice() {
//        System.out.println("BID list:");
//        DataTable bidData = new DataTable();
//        for (FluentWebElement rowElem : tableBidElemList) {
//            DataRow dataRow = retrieveDataRow(rowElem);
//            bidData.addRow(dataRow);
//        }
//        System.out.println();
//
//        return bidData;
//    }
//
//    /**
//     * Average from visible ASKs.
//     */
//    public DataTable readCurrentBuyPrice() {
//        System.out.println("ASK list:");
//        DataTable askData = new DataTable();
//        for (FluentWebElement rowElem : tableAskElemList) {
//            DataRow dataRow = retrieveDataRow(rowElem);
//            askData.addRow(dataRow);
//        }
//        System.out.println();
//
//        return askData;
//    }
//
//    /**
//     * Sell secondary price (BID).
//     */
//    public DataTable readCurrentSellPriceFast() {
//        System.out.println("BID list (fast):");
//
//        DataTable bidData = parseDataTable(ELEM_ID_TABLE_BID_DATA);
//
//        System.out.println();
//        return bidData;
//    }
//
//    /**
//     * Sell secondary price (ASK).
//     */
//    public DataTable readCurrentBuyPriceFast() {
//        System.out.println("ASK list (fast):");
//
//        DataTable askData = parseDataTable(ELEM_ID_TABLE_ASK_DATA);
//
//        System.out.println();
//        return askData;
//    }
//
//    private DataTable parseDataTable(String tableId) {
//        List<FluentWebElement> rowElemList = readTable(tableId);
//        DataTable bidData = new DataTable();
//        for (FluentWebElement rowElem : rowElemList) {
//            DataRow dataRow = parseDataRow(rowElem.getWrappedElement());
//            bidData.addRow(dataRow);
//        }
//        return bidData;
//    }
//
//    public DataRow clickAsk(int index) {
//        List<FluentWebElement> rowElemList = readTable(ELEM_ID_TABLE_ASK_DATA);
//        FluentWebElement rowElem = rowElemList.get(index);
//        rowElem.click();
//        return parseDataRow(rowElem.getWrappedElement());
//    }
//
//    public DataRow clickBid(int index) {
//        List<FluentWebElement> rowElemList = readTable(ELEM_ID_TABLE_BID_DATA);
//        FluentWebElement rowElem = rowElemList.get(index);
//        rowElem.click();
//        return parseDataRow(rowElem.getWrappedElement());
//    }
//
//    private List<FluentWebElement> readTable(String selector) {
//        FluentWebElement bidDataList = el(selector);
//        return bidDataList.$("div", withClass().contains("data-row"));
//    }
//
//    public BB_PageExchangeBtcPln waitForPageToLoad() {
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(lblBalanceBtc).present();
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(lblBalancePln).present();
//
//        return this;
//    }
//
//    private DataRow retrieveDataRow(FluentWebElement dataRowElem) {
//        String realisationRateStr = dataRowElem.$("div", withClass("realisation rate")).$("div").el("span").html().trim();
//        String amountStr = dataRowElem.$("div", withClass("amount")).el("span").html().trim();
//        String priceStr = dataRowElem.$("div", withClass("price")).el("span").html().trim();
//
//        System.out.println("Realisation rate: " + realisationRateStr);
//        System.out.println("Amount: " + amountStr);
//        System.out.println("Price: " + priceStr);
//        System.out.println("---------------------------------");
//        return new DataRow(realisationRateStr, amountStr, priceStr);
//    }
//
//    private static DataRow parseDataRow(WebElement e) {
//        String rowHtml = e.getAttribute("innerHTML");
//
//        // realisation rate
//        Pattern p = Pattern.compile("realisation rate.*\\n.*\\n.*<span>(.*)</span>");
//        Matcher m = p.matcher(rowHtml);
//        boolean matchFound = m.find();
//        String realisationRateStr = null;
//        if (matchFound) {
//            realisationRateStr = m.group(1);
//        }
//
//        // amount
//        p = Pattern.compile("amount.*\\n.*\\n.*<span>(.*)</span>");
//        m = p.matcher(rowHtml);
//        matchFound = m.find();
//        String amountStr = null;
//        if (matchFound) {
//            amountStr = m.group(1);
//        }
//
//        // price
//        p = Pattern.compile("price.*\\n.*<span>(.*)</span>");
//        m = p.matcher(rowHtml);
//        matchFound = m.find();
//        String priceStr = null;
//        if (matchFound) {
//            priceStr = m.group(1);
//        }
//
//        System.out.println("Realisation rate: " + realisationRateStr);
//        System.out.println("Amount: " + amountStr);
//        System.out.println("Price: " + priceStr);
//        System.out.println("---------------------------------");
//
//        return new DataRow(realisationRateStr, amountStr, priceStr);
//    }
//}
