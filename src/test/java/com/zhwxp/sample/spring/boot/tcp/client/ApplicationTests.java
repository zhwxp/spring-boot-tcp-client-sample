package com.zhwxp.sample.spring.boot.tcp.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "tcp.client.scheduler.enabled=false")
public class ApplicationTests {

    @Test
    public void contextLoads() {
    }

}
