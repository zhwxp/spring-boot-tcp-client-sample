package com.zhwxp.sample.spring.boot.tcp.client.scheduler;

import com.zhwxp.sample.spring.boot.tcp.client.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MessageJobScheduler {

    private MessageService messageService;

    @Autowired
    public MessageJobScheduler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Scheduled(fixedDelay = 1000L)
    public void sendMessageJob() {
        messageService.sendMessage();
    }

}
