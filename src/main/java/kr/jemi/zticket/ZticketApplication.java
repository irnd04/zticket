package kr.jemi.zticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Modulithic(sharedModules = { "common", "config" })
@EnableAsync
@EnableCaching
@EnableScheduling
@SpringBootApplication
public class ZticketApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZticketApplication.class, args);
    }
}
