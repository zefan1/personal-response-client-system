package com.privateflow.modules.supervision;

import org.springframework.stereotype.Service;

@Service
public class SupervisionAdminReadService {

  private final SupervisionEventRepository eventRepository;

  public SupervisionAdminReadService(SupervisionEventRepository eventRepository) {
    this.eventRepository = eventRepository;
  }

  public SupervisionEventPage events(SupervisionEventQuery query) {
    return eventRepository.findPage(query);
  }

  public SupervisionMetadata metadata() {
    return eventRepository.metadata();
  }
}
