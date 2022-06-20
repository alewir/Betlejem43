//package me.ugeno.betlejem.web.old.exchange.web.bitbay.pages;
//
//
//import org.fluentlenium.core.FluentPage;
//import org.fluentlenium.core.annotation.PageUrl;
//import org.fluentlenium.core.domain.FluentWebElement;
//import org.openqa.selenium.support.FindBy;
//
//import javax.swing.*;
//import java.util.concurrent.TimeUnit;
//
//import static me.ugeno.betlejem.web.old.Config.DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT;
//
///**
// * Created by alwi on 09/12/2017.
// * All rights reserved.
// */
//@PageUrl("https://auth.bitbay.net/login-password")
//public class BB_PageLoginStep2 extends FluentPage {
//
//    @FindBy(id = "password")
//    private FluentWebElement fldPassword;
//
//    @FindBy(xpath = "//button[contains(@class, 'send-btn')]")
//    private FluentWebElement btnNext;
//
//    public BB_PageExchangeBtcPln enterPasswordAndWaitForAction() {
////        String password = JOptionPane.showInputDialog(null, "Enter password.");
//        String password = "HakunaMatata3";
////
//        fldPassword.write(password); // Fetch password from encrypted file
//
//        JOptionPane.showMessageDialog(null, "Feed captcha and click OK.");
//
//        btnNext.click();
//
//        return newInstance(BB_PageExchangeBtcPln.class);
//    }
//
//    public BB_PageLoginStep2 waitForPageToLoad() {
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(fldPassword).present();
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(fldPassword).clickable();
//
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnNext).present();
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnNext).clickable();
//
//        return this;
//    }
//}
