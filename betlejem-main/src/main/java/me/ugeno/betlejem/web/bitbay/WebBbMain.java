package me.ugeno.betlejem.web.bitbay;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by alwi on 23/11/2017.
 * All rights reserved.
 */
public class WebBbMain {
    public static void main(String[] args) {

        String driverExePath = String.format("%s/chromedriver.2.33.win32.exe", System.getProperty("user.dir"));
        System.out.println("Driver path: " + driverExePath);

        System.setProperty("webdriver.chrome.driver", driverExePath);
        ChromeDriver driver = new ChromeDriver();

        System.out.println("ChromeDriver created.");
        driver.manage().timeouts().implicitlyWait(20, TimeUnit.SECONDS);
        WebDriverWait wait = new WebDriverWait(driver, 20);

        driver.get("https://app.bitbay.net/market/btc-pln");

        WebElement emailLink = wait.until(ExpectedConditions.elementToBeClickable(By.id("email")));
        emailLink.sendKeys("alewir" + Keys.RETURN);

        JOptionPane.showMessageDialog(null, "Perform log in and click OK.");

        WebElement btnCurrencyPair = wait.until(ExpectedConditions.elementToBeClickable(By.id("currency-pair-button")));
        btnCurrencyPair.click();

        WebElement btnFromPair = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id='market-search-tabs']/li[contains(text(), 'PLN')]")));
        btnFromPair.click();

        WebElement btnToPair = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id='currencies-list']/*/div[@data-currency='btc']/currency/i")));
        btnToPair.click();

        WebElement bidDataList = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id='me-data']")));
        List<WebElement> bidPricesElement = bidDataList.findElements(By.xpath(".//div[contains(@class, 'data-row')]"));

        System.out.println("BID list:"); // TODO: calculate price for selling sec
        for (WebElement e : bidPricesElement) {
            parsePriceListRow(e);
        }

        WebElement askDataList = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[@id='ask-data']")));
        List<WebElement> askPricesElement = askDataList.findElements(By.xpath(".//div[contains(@class, 'data-row')]"));

        System.out.println("ASK list:"); // TODO: calculate price for buying sec
        for (WebElement e : askPricesElement) {
            parsePriceListRow(e);
        }

        try {
            Thread.sleep(10000);
        } catch (InterruptedException ignored) {
        }

// <li class="tab active" data-currency="pln">PLN</li>
        // /html/body/div[1]/app-drawer-layout/div[4]/div[3]/div/div/div/div/div[1]/ul/li[1]
// <currency class="layout horizontal center"> <i class="mdi-currency-bitcoin currency-icon" style="background-color: #ffb400;"></i> btc </currency >
        driver.close();
    }

    private static void parsePriceListRow(WebElement e) {
        String rowHtml = e.getAttribute("innerHTML");

        // realisation rate
        Pattern p = Pattern.compile("realisation rate.*\\n.*\\n.*<span>(.*)</span>");
        Matcher m = p.matcher(rowHtml);
        boolean matchFound = m.find();
        if (matchFound) {
            System.out.println("PRICE: " + m.group(1));
        }

        // amount
        p = Pattern.compile("amount.*\\n.*\\n.*<span>(.*)</span>");
        m = p.matcher(rowHtml);
        matchFound = m.find();
        if (matchFound) {
            System.out.println("VOLUME: " + m.group(1));
        }

        // price
        p = Pattern.compile("price.*\\n.*<span>(.*)</span>");
        m = p.matcher(rowHtml);
        matchFound = m.find();
        if (matchFound) {
            System.out.println("VALUE: " + m.group(1));
        }

        System.out.println();
    }
}
