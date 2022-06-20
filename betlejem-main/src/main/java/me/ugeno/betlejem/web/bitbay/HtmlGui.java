//package me.ugeno.betlejem.web.bitbay;
//
//import me.ugeno.betlejem.common.BetlejemConstants;
//import me.ugeno.betlejem.common.Utils;
//import me.ugeno.betlejem.common.utils.BetlejemException;
//import me.ugeno.betlejem.common.utils.BetlejemProperties;
//import me.ugeno.betlejem.common.utils.Resources;
//import com.google.common.base.Throwables;
//import org.apache.commons.io.FileUtils;
//import org.apache.log4j.Logger;
//import org.openqa.selenium.By;
//import org.openqa.selenium.Cookie;
//import org.openqa.selenium.JavascriptExecutor;
//import org.openqa.selenium.NoSuchElementException;
//import org.openqa.selenium.OutputType;
//import org.openqa.selenium.TakesScreenshot;
//import org.openqa.selenium.TimeoutException;
//import org.openqa.selenium.WebDriver;
//import org.openqa.selenium.WebDriver.Navigation;
//import org.openqa.selenium.WebDriver.Options;
//import org.openqa.selenium.WebDriver.TargetLocator;
//import org.openqa.selenium.WebDriverException;
//import org.openqa.selenium.WebElement;
//import org.openqa.selenium.chrome.ChromeDriver;
//import org.openqa.selenium.firefox.FirefoxDriver;
//import org.openqa.selenium.firefox.FirefoxProfile;
//import org.openqa.selenium.ie.InternetExplorerDriver;
//import org.openqa.selenium.ie.InternetExplorerDriverLogLevel;
//import org.openqa.selenium.ie.InternetExplorerDriverService;
//import org.openqa.selenium.remote.DesiredCapabilities;
//import org.openqa.selenium.support.ui.ExpectedCondition;
//import org.openqa.selenium.support.ui.FluentWait;
//import org.openqa.selenium.support.ui.Select;
//import org.openqa.selenium.support.ui.Wait;
//
//import java.io.BufferedReader;
//import java.io.File;
//import java.io.IOException;
//import java.io.StringReader;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.concurrent.TimeUnit;
//
///**
// * Facade for WebDriver.
// *
// * @author SG0212159
// */
//@SuppressWarnings("UnusedDeclaration")
//public class HtmlGui implements AutoCloseable {
//    private static final String FIREBUG_VERSION = "1.11.1"; // make sure this version is equal with plugin version
//    private static final int NUMBER_OF_FIND_RETRIES = 3;
//    private static final int POOLING_INTERVAL_SECONDS = 5;
//    private static final int INTERVAL = 1000;
//    private static InternetExplorerDriverService ieDriverServer;
//    private static Logger logger = Logger.getLogger(HtmlGui.class);
//    private long ELEMENT_LOAD_TIMEOUT_SECONDS = 15;
//    private WebDriver driver;
//    private String url;
//    private String initialPageUrl;
//    private Cookie initialCookie;
//
//    /**
//     * Can be only created by LoyaltySystem which will determine configuration.
//     * properly.
//     */
//    public HtmlGui(String url) {
//        logger.info("Accessing GUI with: " + url);
//        this.url = url;
//    }
//
//    /**
//     * Returns raw driver object reference (required by PageFactory). Do not use it otherways.
//     */
//    public WebDriver getDriver() {
//        return driver;
//    }
//
//    /**
//     * This cookie will be used whenever new browser is created/opened - at startup.
//     * If it needs to be disabled, set null.
//     *
//     * @param cookie         - initial session cookie, e.g. SSO token.
//     * @param cookieInitPage - page where initial session cookie gets injected.
//     */
//    public void setInitialCookie(Cookie cookie, String cookieInitPage) {
//        this.initialCookie = cookie;
//        this.initialPageUrl = cookieInitPage;
//    }
//
//    /**
//     * Creating firefox profile by adding extensions and setting preferences
//     *
//     * @return firefoxProfile
//     */
//    private FirefoxProfile getFirefoxProfile() {
//        FirefoxProfile firefoxProfile = new FirefoxProfile();
//        firefoxProfile.addExtension(new File(Resources.getPath("jif.firefox.firebug.plugin")));
//        firefoxProfile.addExtension(new File(Resources.getPath("jif.firefox.firepath.plugin")));
//        firefoxProfile.setPreference("extensions.firebug.currentVersion", FIREBUG_VERSION);
//
//        return firefoxProfile;
//    }
//
//    private void openApplication(Browser browser, FirefoxProfile fp) {
//        // Closes browser if already opened as Selenium has problems with operating on 2 browser instances.
//        closeApplication();
//
//        switch (browser) {
//            case iexplorer:
//                if (ieDriverServer == null) {
//                    ieDriverServer = initializeIeDriverServer();
//                }
//
//                DesiredCapabilities capabilities = DesiredCapabilities.internetExplorer();
//                capabilities.setCapability(InternetExplorerDriver.IGNORE_ZOOM_SETTING, true);
//                driver = new InternetExplorerDriver(ieDriverServer, capabilities);
//                break;
//            case firefox:
//                driver = new FirefoxDriver();
//                // TODO: set profile to: fp
//                break;
//            case chrome:
//                System.setProperty("webdriver.gecko.driver", "D:\\Alwi\\Projects\\Workspace_betlejem\\geckodriver.exe");
//
//                driver = new ChromeDriver();
//                break;
//            default:
//                throw new BetlejemException("Browser " + browser + " not supported.");
//        }
//
//        // Additional Selenium setup
//        driver.manage().timeouts().implicitlyWait(BetlejemConstants.WAIT_FOR_ELEMENT_TO_LOAD_TIMEOUT, TimeUnit.SECONDS);
//
//        // Opens new browser window & goes to given URL
//        logger.info("Starting WebDriver - get method...");
//
//        // Set some initial cookie if provided
//        if (initialCookie == null) {
//            driver.get(url);
//        } else {
//            injectInitialCookieAndOpenUrl();
//        }
//    }
//
//    private InternetExplorerDriverService initializeIeDriverServer() {
//        String ieDriverSvr = Resources.getPath("fft.ie.driver.server.executable");
//        logger.info("Setting system property 'webdriver.ie.driver' = " + ieDriverSvr);
//        System.setProperty("webdriver.ie.driver", ieDriverSvr);
//
//        ieDriverServer = new InternetExplorerDriverService.Builder()
//                .usingAnyFreePort()
//                .withLogFile(new File("IE_driver_latest.log"))
//                .withLogLevel(InternetExplorerDriverLogLevel.TRACE).build();
//
//        try {
//            ieDriverServer.start();
//            logger.info("InternetExplorerDriver server started.");
//        } catch (IOException e) {
//            logger.fatal("Could not start InternetExplorerDriver server.");
//            throw Throwables.propagate(e);
//        }
//
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            ieDriverServer.stop();
//            logger.info("InternetExplorerDriver server stopped.");
//        }));
//
//        return ieDriverServer;
//    }
//
//    /**
//     * If there is some initial cookie that is to be set, use initial page to inject it and then redirect to default URL from constructor.
//     */
//    private void injectInitialCookieAndOpenUrl() {
//        if (initialPageUrl == null) {
//            driver.get(url);
//        } else {
//            driver.get(initialPageUrl);
//        }
//
//        addCookieToBrowser(initialCookie);
//        navigate().to(url);
//    }
//
//    /**
//     * Browser has to be already opened.
//     */
//    private void addCookieToBrowser(Cookie newCookie) {
//        String addCookieScript = "document.cookie='" + newCookie.getName() + '=' + newCookie.getValue() + "; domain=" + newCookie.getDomain() + "; path=" + newCookie.getPath() + ";'";
//        logger.info("Setting cookie with script: [" + addCookieScript + ']');
//        ((JavascriptExecutor) driver).executeScript(addCookieScript);
//    }
//
//    /**
//     * (Re)opens default browser window on the URL for which the HtmlGui was created.
//     */
//    public HtmlGui openApplication() {
//        int retryLimit = 3;
//        int attempt = 1;
//        boolean open = false;
//        while (!open && attempt <= retryLimit) {
//            try {
//                openApplication(Browser.chrome, getFirefoxProfile());
//                open = true;
//            } catch (Exception e) {
//                logger.fatal("Browser failed to start. Try " + attempt + " of " + retryLimit + " Error: " + e.getMessage());
//                closeApplication();
//                attempt++;
//            }
//        }
//        return this;
//    }
//
//    /**
//     * Closes browser window.
//     * <p/>
//     * NOTE: The operation is idempotent.
//     */
//    private void closeApplication() {
//        logger.info("Closing browser...");
//        if (driver != null) {
//            try {
//                ((JavascriptExecutor) driver).executeScript("window.onbeforeunload = function(e){};");
//            } catch (Exception e) {
//                logger.warn("Problem with closing popups before browser close.");
//            }
//
//            driver.quit();
//        } else {
//            logger.debug("Browser not initialized.");
//        }
//        driver = null;
//    }
//
//    /**
//     * To be able to use auto closable syntactic sugar...
//     */
//    @Override
//    public void close() {
//        closeApplication();
//    }
//
//    /**
//     * Uses WebDriver to find element with given locator. Retries given number of times.
//     */
//    private WebElement findElement(By locator, boolean retry) {
//        WebDriverException lastException = null;
//
//        // Retry configurable number of times if retry was requested
//        for (int i = 0; i < NUMBER_OF_FIND_RETRIES; i++) {
//            try {
//                return driver.findElement(locator);
//            } catch (WebDriverException e) {
//                lastException = e;
//                if (retry) {
//                    logger.debug("Failed try " + (i + 1) + '/' + NUMBER_OF_FIND_RETRIES + " - element not found on page: >" + locator.toString() + '<');
//                    Utils.delayNextOperations(INTERVAL, false);
//                } else {
//                    break;
//                }
//            }
//        }
//
//
//        logger.warn("Failed " + NUMBER_OF_FIND_RETRIES + " tries of finding element on page: >" + locator.toString() + "< exception received: " + lastException.getClass());
//        throw lastException;
//    }
//
//    private WebElement findElemByXPath(String xpathLocator) {
//        return findElement(By.xpath(xpathLocator), true);
//    }
//
//    public WebElement findElemByXPathWithoutRetry(String xpathLocator) {
//        return findElement(By.xpath(xpathLocator), false);
//    }
//
//    public WebElement findElemByCssSelector(String selector) {
//        return findElement(By.cssSelector(selector), true);
//    }
//
//    public WebElement findElemById(String id) {
//        return findElement(By.id(id), true);
//    }
//
//    public WebElement findElemByLinkText(String linkText) {
//        return findElement(By.linkText(linkText), true);
//    }
//
//    public WebElement findElemByName(String name) {
//        return findElement(By.name(name), true);
//    }
//
//    public WebElement findElemByPartialLinkText(String linkText) {
//        return findElement(By.partialLinkText(linkText), true);
//    }
//
//    public WebElement findElemByTagName(String name) {
//        return findElement(By.tagName(name), true);
//    }
//
//    /**
//     * @see WebDriver#getCurrentUrl()
//     */
//    public String getCurrentUrl() {
//        return driver.getCurrentUrl();
//    }
//
//    /**
//     * @see WebDriver#getTitle()
//     */
//    public String getTitle() {
//        return driver.getTitle();
//    }
//
//    /**
//     * @see WebDriver#findElements(By)
//     */
//    public List<WebElement> findElements(By by) {
//        return driver.findElements(by);
//    }
//
//    public List<WebElement> findElementsByXpath(String xpath) {
//        return driver.findElements(By.xpath(xpath));
//    }
//
//    /**
//     * @see WebDriver#getPageSource()
//     */
//    public String getPageSource() {
//        return driver.getPageSource();
//    }
//
//    /**
//     * @see WebDriver#close()
//     */
//    public void closeWindow() {
//        driver.close();
//    }
//
//    /**
//     * @see WebDriver#getWindowHandles()
//     */
//    public Set<String> getWindowHandles() {
//        return driver.getWindowHandles();
//    }
//
//    /**
//     * @see WebDriver#getWindowHandle()
//     */
//    public String getWindowHandle() {
//        return driver.getWindowHandle();
//    }
//
//    /**
//     * @see WebDriver#switchTo()
//     */
//    public TargetLocator switchTo() {
//        return driver.switchTo();
//    }
//
//    /**
//     * @see WebDriver#navigate()
//     */
//    private Navigation navigate() {
//        return driver.navigate();
//    }
//
//    /**
//     * @see WebDriver#manage()
//     */
//    public Options manage() {
//        return driver.manage();
//    }
//
//    /**
//     * Logs the elements that were found and those that were not.
//     *
//     * @return false if any element is not found during the timeout, true if all elements were found
//     */
//    public boolean isElementPresent(String... xPathsList) {
//        // Lower WebDriver timeout
//        driver.manage().timeouts().implicitlyWait(2, TimeUnit.SECONDS);
//        try {
//            boolean allFound = true;
//            Map<String, Boolean> elementsFound = new HashMap<>();
//
//            for (String xpath : xPathsList) {
//                // Store information if element is present at this time
//                try {
//                    WebElement element = findElement(By.xpath(xpath), false);
//                    elementsFound.put(xpath, element.isDisplayed());
//                } catch (WebDriverException e) {
//                    logger.warn("Element NOT present on web page: >" + xpath + '<');
//                    elementsFound.put(xpath, false);
//                    allFound = false;
//                }
//            }
//
//            // Not all were found while time's up - print out the message...
//            for (Map.Entry<String, Boolean> stringBooleanEntry : elementsFound.entrySet()) {
//                logger.info("Element xpath=" + stringBooleanEntry.getKey() + " was " + (stringBooleanEntry.getValue() ? "found" : "not found"));
//            }
//
//            return allFound;
//        } finally {
//            // Set WebDriver timeout back
//            driver.manage().timeouts().implicitlyWait(BetlejemConstants.WAIT_FOR_ELEMENT_TO_LOAD_TIMEOUT, TimeUnit.SECONDS);
//        }
//    }
//
//    public boolean isElementVisible(WebElement element, String elementDescription) {
//        // Lower WebDriver timeout
//        driver.manage().timeouts().implicitlyWait(2, TimeUnit.SECONDS);
//        try {
//            try {
//                return element.isDisplayed();
//            } catch (WebDriverException e) {
//                logger.warn("Element NOT present on web page: >" + elementDescription + '<');
//                return false;
//            }
//        } finally {
//            // Set WebDriver timeout back
//            driver.manage().timeouts().implicitlyWait(BetlejemConstants.WAIT_FOR_ELEMENT_TO_LOAD_TIMEOUT, TimeUnit.SECONDS);
//        }
//    }
//
//    public String extractTextFromElement(WebElement element) {
//        return (String) ((JavascriptExecutor) driver).executeScript("return arguments[0].innerText", element);
//    }
//
//    /**
//     * Waits until given HTML element is displayed
//     *
//     * @param xpathLocator     xpath expression
//     * @param timeoutInSeconds is seconds
//     * @return WebElement
//     */
//    public WebElement waitUntilPresent(final String xpathLocator, long timeoutInSeconds) {
//        final Wait<WebDriver> waiting = new FluentWait<>(driver)
//                .withTimeout(ELEMENT_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
//                .pollingEvery(POOLING_INTERVAL_SECONDS, TimeUnit.SECONDS)
//                .withMessage("Element " + xpathLocator + " was not appear during " + timeoutInSeconds + " seconds...")
//                .ignoring(NoSuchElementException.class);
//
//        try {
//            waiting.until((ExpectedCondition<Boolean>) d -> {
//                boolean displayed = d.findElement(By.xpath(xpathLocator)).isDisplayed();
//                logger.info("Element for xpath='" + xpathLocator + "\') visible? <= " + displayed);
//                return displayed;
//            });
//        } catch (TimeoutException te) {
//            captureScreenshot();
//            throw te;
//        }
//        return findElemByXPath(xpathLocator);
//    }
//
//    /**
//     * Waits until given HTML element disappears
//     *
//     * @param xpathLocator     xpath expression
//     * @param timeoutInSeconds in seconds
//     */
//    public void waitUntilDisappears(final String xpathLocator, long timeoutInSeconds) {
//        final Wait<WebDriver> waiting = new FluentWait<>(driver)
//                .withTimeout(timeoutInSeconds, TimeUnit.SECONDS)
//                .pollingEvery(POOLING_INTERVAL_SECONDS, TimeUnit.SECONDS)
//                .withMessage("Element " + xpathLocator + " did not disappear during " + timeoutInSeconds + " seconds...")
//                .ignoring(NoSuchElementException.class);
//
//        try {
//            waiting.until((ExpectedCondition<Boolean>) d -> {
//                try {
//                    return !d.findElement(By.xpath(xpathLocator)).isDisplayed();
//                } catch (NoSuchElementException e) {
//                    return true;
//                }
//            });
//        } catch (TimeoutException te) {
//            captureScreenshot();
//            throw te;
//        }
//    }
//
//    /**
//     * Wait until there is element present on page and contains given text.
//     *
//     * @param errorInformation to be displayed in log when the element with given text is not found
//     */
//    public void waitForElementWithTextEquals(final WebElement element, final String expectedText, final String errorInformation) {
//        final Wait<WebDriver> waiting = new FluentWait<>(driver)
//                .withTimeout(ELEMENT_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
//                .pollingEvery(POOLING_INTERVAL_SECONDS, TimeUnit.SECONDS)
//                .withMessage(errorInformation)
//                .ignoring(NoSuchElementException.class);
//
//        logger.info("Waiting for text='" + expectedText + "' (exact) to show up in given WebElement.");
//        try {
//            waiting.until((ExpectedCondition<Boolean>) d -> {
//                try {
//                    String textFound = element.getText();
//                    if (!textFound.equals(expectedText)) {
//                        logger.warn(errorInformation + ". Text found in element='" + textFound + "'.");
//                        return false;
//                    }
//                } catch (Exception e) {
//                    logger.warn(e.getClass().getSimpleName() + " received while waiting for element with text: " + parseLocatorFromSeleniumErrorMsg(e.getMessage()));
//                    return false;
//                }
//                return true;
//            });
//        } catch (TimeoutException te) {
//            captureScreenshot();
//            throw te;
//        }
//    }
//
//    /**
//     * Wait until there is element present on page and contains given substring.
//     *
//     * @param errorInformation to be displayed in log when the element with given text is not found
//     */
//    public void waitForElementContainingText(final WebElement element, final String expectedSubstring, final String errorInformation) {
//        final Wait<WebDriver> waiting = new FluentWait<>(driver)
//                .withTimeout(ELEMENT_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
//                .pollingEvery(POOLING_INTERVAL_SECONDS, TimeUnit.SECONDS)
//                .withMessage(errorInformation)
//                .ignoring(NoSuchElementException.class);
//
//        logger.info("Waiting for text='" + expectedSubstring + "' to be contained in given WebElement.");
//        try {
//            waiting.until((ExpectedCondition<Boolean>) d -> {
//                try {
//                    String textFound = element.getText();
//                    if (!textFound.contains(expectedSubstring)) {
//                        logger.warn(errorInformation + ". Text found in element='" + textFound + "'.");
//                        return false;
//                    } else {
//                        return true;
//                    }
//                } catch (Exception e) {
//                    logger.warn(e.getClass().getSimpleName() + " received while waiting for sub-text: " + parseLocatorFromSeleniumErrorMsg(e.getMessage()));
//                    return false;
//                }
//            });
//        } catch (TimeoutException te) {
//            captureScreenshot();
//            throw te;
//        }
//    }
//
//    /**
//     * Waits for given condition.
//     */
//    public void waitForExpectedCondition(ExpectedCondition<?> webElementExpectedCondition, String errorInformation) {
//        final Wait<WebDriver> waiting = new FluentWait<>(driver)
//                .withTimeout(ELEMENT_LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
//                .pollingEvery(POOLING_INTERVAL_SECONDS, TimeUnit.SECONDS)
//                .withMessage("Expected condition not met - " + errorInformation)
//                .ignoring(NoSuchElementException.class);
//
//        try {
//            waiting.until(webElementExpectedCondition);
//        } catch (TimeoutException te) {
//            captureScreenshot();
//            throw te;
//        }
//    }
//
//    /**
//     * Goes through all open windows - logs their titles until it finds given one - then it switches to it.
//     */
//    public void switchToWindowByTitle(String popupWindowTitle) {
//        boolean windowFoundByTitle = false;
//        String foundWindowsWithTitles = "";
//
//        // Always wait to be sure that popup window is opened - probably requested before invoking this method
//        Utils.delayNextOperations(3000, false);
//        logger.info("Amount of open windows: " + driver.getWindowHandles().size());
//
//        for (String handle : driver.getWindowHandles()) {
//            String windowTitle = driver.switchTo().window(handle).getTitle();
//            logger.info("Window found with title: >" + windowTitle + '<');
//            foundWindowsWithTitles += '>' + windowTitle + "<, ";
//            if (popupWindowTitle.equals(windowTitle)) {
//                windowFoundByTitle = true;
//                logger.info("Switched to window with title: >" + popupWindowTitle + '<');
//                break;
//            }
//        }
//
//        if (!windowFoundByTitle) {
//            throw new NoSuchElementException("Window not found by title=>" + popupWindowTitle + "<. Windows found: " + foundWindowsWithTitles);
//        }
//    }
//
//    /**
//     * Switches to any other opened window. Fails if no other window is found.
//     */
//    public void switchToOtherOpenedWindow() {
//        waitForOpenedWindows(2);
//
//        String currentWindowHandle = driver.getWindowHandle();
//        Set<String> openWindowsHandles = driver.getWindowHandles();
//        boolean anotherWindowFound = false;
//        for (String handle : openWindowsHandles) {
//            driver.switchTo().window(handle);
//            logger.info("Window found - title: " + driver.getTitle());
//
//            if (!handle.equals(currentWindowHandle)) {
//                anotherWindowFound = true;
//                break;
//            }
//        }
//
//        assert anotherWindowFound : "No other windows found";
//    }
//
//    private void waitForOpenedWindows(int expectedAmountOfWindows) {
//        logger.info("Waiting for " + expectedAmountOfWindows + " open windows...");
//
//        int MAX_ATTEMPTS = 10;
//        int attempt = 1;
//        while (true) {
//            int size = driver.getWindowHandles().size();
//            logger.info(" - amount of open windows: " + size + " [attempt " + attempt + ']');
//
//            if (size == expectedAmountOfWindows || attempt > MAX_ATTEMPTS) {
//                break;
//            } else {
//                Utils.delayNextOperations(1000, false);
//                attempt++;
//            }
//        }
//    }
//
//    /**
//     * Executes given script on page.
//     */
//    public Object executeScript(String javascript) {
//        JavascriptExecutor js = (JavascriptExecutor) driver;
//        return js.executeScript(javascript);
//    }
//
//    /**
//     * Opens given url
//     */
//    public void openApplication(String url) {
//        driver.get(url);
//    }
//
//    /**
//     * Waits for all jQuery Ajax calls to finish
//     */
//    public void waitForJQueryAjax(int timeoutInSeconds) {
//        if (driver instanceof JavascriptExecutor) {
//            JavascriptExecutor jsDriver = (JavascriptExecutor) driver;
//
//            for (int i = 0; i < timeoutInSeconds; i++) {
//                Object numberOfAjaxConnections = jsDriver.executeScript("return jQuery.active");
//                // return should be a number
//                if (numberOfAjaxConnections instanceof Long) {
//                    Long n = (Long) numberOfAjaxConnections;
//                    logger.info("Waiting for jQuery Ajax calls to finish. Number of active jQuery Ajax calls: " + n);
//                    if (n == 0L) {
//                        break;
//                    }
//                }
//                Utils.delayNextOperations(1000, false);
//            }
//        } else {
//            logger.error("Web driver: " + driver + " cannot execute javascript");
//        }
//    }
//
//    private void captureScreenshot() {
//        try {
//            String testIdPart = "N/A";
//            String filename = Utils.getDateWithDaysOffset(0, "yyyyddMMHHmmss") + '_' + testIdPart + ".png";
//            String filePath = Resources.getPath(BetlejemConstants.SCREENSHOTS_PATH_PROPERTY) + '/' + filename;
//            File scrFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
//            File screenshot = new File(filePath);
//            FileUtils.copyFile(scrFile, screenshot);
//            logger.info("Taking screenshot:\n file:///" + screenshot.getAbsolutePath().replaceAll("\\\\", "/"));
//            logger.info("Might have been published under:\n " + BetlejemProperties.getValue(BetlejemConstants.HUDSON_SCREENSHOTS_LINK) + filename);
//        } catch (Exception e) {
//            logger.warn("Failed to capture screenshot: " + e.getMessage());
//        }
//    }
//
//    public List<String> getSelectValues(Select selectElement) {
//        List<WebElement> availableMarkets = selectElement.getOptions();
//        List<String> marketCodes = new ArrayList<>();
//        for (WebElement market : availableMarkets) {
//            marketCodes.add(market.getAttribute("value"));
//        }
//        return marketCodes;
//    }
//
//    public List<String> getSelectVisibleTexts(Select selectElement) {
//        List<WebElement> availableMarkets = selectElement.getOptions();
//        List<String> marketCodes = new ArrayList<>();
//        for (WebElement market : availableMarkets) {
//            marketCodes.add(market.getText());
//        }
//        return marketCodes;
//    }
//
//    private String parseLocatorFromSeleniumErrorMsg(String elementNotFoundErrorMessage) {
//        final String stackTraceWarning = " (WARNING: The server did not provide any stack trace information)";
//
//        try {
//            BufferedReader reader = new BufferedReader(new StringReader(elementNotFoundErrorMessage));
//            String line;
//            while ((line = reader.readLine()) != null) {
//                if (line.contains("The xpath expression")) {
//                    return line.replace(stackTraceWarning, "");
//                } else if (line.contains("Unable to find element with")) {
//                    return line.replace(stackTraceWarning, "");
//                } else if (line.contains("Element is no longer valid")) {
//                    return line.replace(stackTraceWarning, "");
//                } else if (line.contains("Unable to get element text")) {
//                    return line.replace(stackTraceWarning, "");
//                }
//                // Implement extraction of information about locator for other messages here...
//            }
//
//            logger.warn("Selenium error not recognized by JIF. Please update framework.");
//            return elementNotFoundErrorMessage;
//        } catch (Exception e) {
//            return "";
//        }
//    }
//
//    public enum Browser {
//        iexplorer,
//        firefox,
//        chrome
//    }
//}
