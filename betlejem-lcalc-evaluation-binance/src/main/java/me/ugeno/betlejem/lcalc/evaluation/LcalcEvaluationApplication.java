package me.ugeno.betlejem.lcalc.evaluation;

import me.ugeno.betlejem.common.enc.ObfuscatedPasswordsContextInitializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PostConstruct;

@SuppressWarnings("Duplicates")
@SpringBootApplication
@EnableScheduling
public class LcalcEvaluationApplication {
    private final ConfigurableApplicationContext context;

    @Autowired
    public LcalcEvaluationApplication(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(LcalcEvaluationApplication.class);
        app.addInitializers(new ObfuscatedPasswordsContextInitializer());
        app.setBannerMode(Banner.Mode.CONSOLE);
        app.run(args);
    }

    @PostConstruct
    private void init() {
        context.registerShutdownHook();
    }
}
