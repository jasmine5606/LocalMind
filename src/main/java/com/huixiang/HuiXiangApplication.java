package com.huixiang;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.huixiang.mapper")
@SpringBootApplication
public class HuiXiangApplication {

    public static void main(String[] args) {
        SpringApplication.run(HuiXiangApplication.class, args);
    }

}
