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
//@PageUrl("https://bitbay.net/en")
//public class BB_PageHome extends FluentPage {
//    @FindBy(id = "loginButton")
//    private FluentWebElement btnLogin;
//
//    public BB_PageLoginStep1 goToLoginPage() {
//        btnLogin.click();
//
//        return newInstance(BB_PageLoginStep1.class);
//    }
//
//    public BB_PageHome waitForPageToLoad() {
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnLogin).present();
//        await().atMost(DEFAULT_WEB_ELEMENT_WAIT_TIMEOUT, TimeUnit.SECONDS).until(btnLogin).clickable();
//        return this;
//    }
//}
//
//
