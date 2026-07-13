package com.privateflow.modules.customer.sync;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mock-externals", havingValue = "true")
public class MockSheetClient implements SheetClient {

  @Override
  public List<SheetRow> fetchIncrementalRows(SheetSource source, LocalDateTime modifiedAfter, int limit) {
    String sourceTable = source.sourceTable();
    if ("推广组客资登记表".equals(sourceTable)) {
      return List.of(new SheetRow("mock-promo-1", Map.of(
          "手机号/微信", "13800000001",
          "意向门店", "城南店",
          "对接管家", "小张",
          "下单项目", "产后修复团购体验券",
          "来源渠道", "团购平台")));
    }
    if ("私域客资管理表".equals(sourceTable)) {
      return List.of(new SheetRow("mock-private-1", Map.ofEntries(
          Map.entry("联系方式", "13800000001"),
          Map.entry("备注称呼", "李女士"),
          Map.entry("客资渠道", "私域"),
          Map.entry("客资类型", "TUAN_GOU"),
          Map.entry("管家", "小张"),
          Map.entry("意向门店", "城南店"),
          Map.entry("意向项目", "腹直肌修复"),
          Map.entry("客户阶段", "已加微"),
          Map.entry("客户关注点", "腹直肌分离"),
          Map.entry("跟进记录", "已沟通团购体验券"),
          Map.entry("下次跟进方向", "确认到店时间"))));
    }
    if ("新客管理衔接表".equals(sourceTable)) {
      return List.of(new SheetRow("mock-arrival-1", Map.of(
          "手机号码", "13800000001",
          "客户姓名", "李女士",
          "所属门店", "城南店",
          "是否到店", "否",
          "体验项目", "腹直肌修复",
          "约课管家", "小张")));
    }
    return List.of();
  }
}
