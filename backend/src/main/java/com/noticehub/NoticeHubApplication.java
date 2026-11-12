package com.noticehub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.noticehub.mapper")
public class NoticeHubApplication {

  public static void main(String[] args) {
    SpringApplication.run(NoticeHubApplication.class, args);
  }
}
