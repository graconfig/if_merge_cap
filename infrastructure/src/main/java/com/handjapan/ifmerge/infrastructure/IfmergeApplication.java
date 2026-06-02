package com.handjapan.ifmerge.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
public class IfmergeApplication {

    public static void main(String[] args) {
        SpringApplication.run(IfmergeApplication.class, args);
    }
}
