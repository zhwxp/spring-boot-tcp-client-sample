package com.zhwxp.sample.spring.boot.tcp.client.scheduler;

import com.zhwxp.sample.spring.boot.tcp.client.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tcp.client.scheduler.enabled", matchIfMissing = true)
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
