package kr.jemi.zticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ZticketApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZticketApplication.class, args);
    }

}
