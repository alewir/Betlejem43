package me.ugeno.betlejem.binance.prices;

import me.ugeno.betlejem.common.enc.ObfuscatedPasswordsContextInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.PostConstruct;

@SuppressWarnings("Duplicates")
@SpringBootApplication
@EnableScheduling
public class BinancePriceScrapperApplication {
    private static final int MAX_ENQUIRY_POOL_SIZE = 50;

    private final ConfigurableApplicationContext context;

    @Autowired
    public BinancePriceScrapperApplication(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BinancePriceScrapperApplication.class);
        app.addInitializers(new ObfuscatedPasswordsContextInitializer());
        app.setBannerMode(Banner.Mode.CONSOLE);
        app.run(args);
    }

    @PostConstruct
    private void init() {
        context.registerShutdownHook();
    }

    @Bean
    public TaskExecutor gobblerTaskExecutor() {
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(MAX_ENQUIRY_POOL_SIZE);
        threadPoolTaskExecutor.setThreadNamePrefix("CSA-PoolThread-");
        return threadPoolTaskExecutor;
    }
}
