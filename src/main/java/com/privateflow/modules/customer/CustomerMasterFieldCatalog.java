package com.privateflow.modules.customer;

import java.util.List;

/** The approved business fields of the customer single source of truth. */
public final class CustomerMasterFieldCatalog {

  private static final List<FieldDefinition> FIELDS = List.of(
      field("customerName", "客户姓名", "客户身份"),
      field("nickname", "客户昵称", "客户身份"),
      field("phone", "手机号", "客户身份"),
      field("wechatId", "微信号", "客户身份"),
      field("sourceChannel", "来源渠道", "留资与分配"),
      field("leadType", "客资类型", "留资与分配"),
      field("leadCaptureMethod", "留资方式", "留资与分配"),
      field("platformLeadAt", "平台留资时间", "留资与分配"),
      field("advertisingType", "广告类型", "广告追踪"),
      field("globalAdvertisementId", "全域广告ID", "广告追踪"),
      field("standardAdvertisementId", "标准广告ID", "广告追踪"),
      field("contentId", "内容ID", "广告追踪"),
      field("videoId", "视频ID", "广告追踪"),
      field("orderNumber", "订单号", "广告追踪"),
      field("conversionTrace", "转化追溯", "广告追踪"),
      field("previousAssignedKeeper", "上次分配人", "留资与分配"),
      field("previousPlatformLeadAt", "上次留资时间", "留资与分配"),
      field("assignedKeeper", "分配管家", "留资与分配"),
      field("assignedAt", "分配日期", "留资与分配"),
      field("assignmentMonth", "分配月份", "留资与分配"),
      field("intendedStore", "意向门店", "意向与购卡"),
      field("intendedProject", "意向项目", "意向与购卡"),
      field("purchasedProject", "已购项目", "意向与购卡"),
      field("experienceCardType", "体验卡类型", "意向与购卡"),
      field("pendingOrderStatus", "挂单情况", "意向与购卡"),
      field("purchaseDate", "购卡时间", "意向与购卡"),
      field("customerLevel", "客户等级", "客户档案"),
      field("personalityType", "性格类型", "客户档案"),
      field("postpartumMonths", "产后月份", "客户档案"),
      field("parity", "胎次", "客户档案"),
      field("deliveryMethod", "分娩方式", "客户档案"),
      field("breastfeeding", "哺乳情况", "客户档案"),
      field("lochiaPeriod", "恶露/月经情况", "客户档案"),
      field("pregnancyWeight", "孕期增重", "客户档案"),
      field("currentWeight", "当前体重", "客户档案"),
      field("bodyConcerns", "客户关注点", "客户档案"),
      field("diastasisRecti", "腹直肌分离", "客户档案"),
      field("urineLeakage", "漏尿情况", "客户档案"),
      field("pubicLumbago", "耻骨/腰痛", "客户档案"),
      field("prevRepairExp", "既往修复经历", "客户档案"),
      field("postpartumCheck", "产后检查", "客户档案"),
      field("exerciseHabits", "运动习惯", "客户档案"),
      field("intentLevel", "意向等级", "客户档案"),
      field("customerProfileSummary", "客户档案摘要", "客户档案"),
      field("internalNote", "备注", "客户档案"),
      field("firstTrackingCapture", "第一次追踪捕捉", "跟进"),
      field("secondTrackingCapture", "第二次追踪捕捉", "跟进"),
      field("thirdTrackingCapture", "第三次追踪捕捉", "跟进"),
      field("lastFollowupAt", "最近跟进时间", "跟进"),
      field("followupNotes", "跟进记录", "跟进"),
      field("nextFollowupAt", "下次跟进时间", "跟进"),
      field("nextFollowupDir", "下次跟进方向", "跟进"),
      field("appointmentDate", "预约日期", "预约与到店"),
      field("appointmentTime", "预约时间", "预约与到店"),
      field("appointmentStore", "预约门店", "预约与到店"),
      field("appointmentItem", "预约项目", "预约与到店"),
      field("appointmentStatus", "预约状态", "预约与到店"),
      field("arrived", "是否到店", "预约与到店"),
      field("arrivalHandoverRecord", "到店衔接记录", "预约与到店"),
      field("arrivalProjectType", "项目类型", "到店反馈"),
      field("arrivalExperienceProject", "到店体验项目", "到店反馈"),
      field("historicalExperienceCount", "历史体验次数", "到店反馈"),
      field("customerReport", "客户报告", "到店反馈"),
      field("receptionTeacher", "接待老师", "到店反馈"),
      field("receptionConsultant", "接待顾问", "到店反馈"),
      field("voucherRedeemed", "是否核券", "到店反馈"),
      field("customerStage", "客户阶段", "成交结果"),
      field("transactionAmount", "成交金额", "成交结果"),
      field("transactionAt", "成交时间", "成交结果"),
      field("transactionPrimaryReason", "成交主因", "成交结果"));

  private CustomerMasterFieldCatalog() {
  }

  public static List<FieldDefinition> fields() {
    return FIELDS;
  }

  public static String labelOf(String fieldName) {
    return FIELDS.stream()
        .filter(field -> field.name().equals(fieldName))
        .map(FieldDefinition::label)
        .findFirst()
        .orElse(fieldName);
  }

  private static FieldDefinition field(String name, String label, String category) {
    return new FieldDefinition(name, label, category);
  }

  public record FieldDefinition(String name, String label, String category) {
  }
}
