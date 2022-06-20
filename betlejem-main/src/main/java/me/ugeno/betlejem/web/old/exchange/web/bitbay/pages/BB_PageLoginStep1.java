//package me.ugeno.betlejem.web.old.exchange.web.bitbay.pages;
//
//import org.fluentlenium.core.FluentPage;
//import org.fluentlenium.core.annotation.PageUrl;
//import org.fluentlenium.core.domain.FluentWebElement;
//import org.openqa.selenium.support.FindBy;
//
//import java.util.concurrent.TimeUnit;
//
//import static me.ugeno.betlejem.web.old.Config.DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT;
//
///**
// * Created by alwi on 09/12/2017.
// * All rights reserved.
// */
//@SuppressWarnings("SameParameterValue")
//@PageUrl("https://auth.bitbay.net/login")
//public class BB_PageLoginStep1 extends FluentPage {
//
//    @FindBy(xpath = "//a[@href='register']")
//    private FluentWebElement registerLink;
//
//    @FindBy(id = "email")
//    private FluentWebElement fldUsername;
//
//    @FindBy(xpath = "//button[contains(@class, 'send-btn')]")
//    private FluentWebElement btnNext;
//
//    public BB_PageLoginStep2 enterUsernameAndContinue(String username) {
//        fldUsername.write(username);
//
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnNext).clickable();
//        btnNext.click();
//
//        return newInstance(BB_PageLoginStep2.class);
//    }
//
//    public BB_PageLoginStep1 waitForPageToLoad() {
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(registerLink).present();
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(registerLink).clickable();
//
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(fldUsername).present();
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(fldUsername).clickable();
//
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnNext).present();
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnNext).clickable();
//
//        return this;
//    }
//}
