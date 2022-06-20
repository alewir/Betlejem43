package me.ugeno.betlejem.lcalc.tradebot.binance;

import me.ugeno.betlejem.common.enc.ObfuscatedPasswordsContextInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.annotation.PostConstruct;

@SuppressWarnings("Duplicates")
@SpringBootApplication
@EnableScheduling
public class LcalcTradebotApplication {
    private final ConfigurableApplicationContext context;

    @Autowired
    public LcalcTradebotApplication(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(LcalcTradebotApplication.class);
        app.addInitializers(new ObfuscatedPasswordsContextInitializer());
        app.setBannerMode(Banner.Mode.CONSOLE);
        app.run(args);
    }

    @PostConstruct
    private void init() {
        context.registerShutdownHook();
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(3);
        threadPoolTaskScheduler.setThreadNamePrefix("ThreadPoolTaskScheduler");
        return threadPoolTaskScheduler;
    }
}
