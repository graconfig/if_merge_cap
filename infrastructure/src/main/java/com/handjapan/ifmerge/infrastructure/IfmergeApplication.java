package com.handjapan.ifmerge.infrastructure;

import com.handjapan.ifmerge.infrastructure.config.IfmergeProperties;
import com.handjapan.ifmerge.infrastructure.config.SapAiCoreProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * IFmerge CAP Spring Boot 入口。
 *
 * ComponentScan で 3 モジュールの全パッケージを取り込む。
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.handjapan.ifmerge.infrastructure",
        "com.handjapan.ifmerge.application",
        "com.handjapan.ifmerge.domain"
})
@EnableConfigurationProperties({SapAiCoreProperties.class, IfmergeProperties.class})
public class IfmergeApplication {

    public static void main(String[] args) {
        SpringApplication.run(IfmergeApplication.class, args);
    }
}
