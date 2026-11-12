package com.noticehub.config;

import lombok.Getter;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Getter
public class Readiness {

  private volatile boolean ready;

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    ready = true;
  }
}
