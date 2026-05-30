package com.khankiddo.learning;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.khankiddo.learning.mapper")
public class KhanKiddoLearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(KhanKiddoLearningApplication.class, args);
    }
}
