package com.privateflow.modules.customer.booking;

import com.privateflow.common.events.CustomerMessageSentEvent;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Extracts the explicit appointment block used in employee/customer chat. */
@Component
public class AppointmentMessageParser {

  private static final Pattern LABELED = Pattern.compile(
      "(?m)^\\s*(预约人|预约时间|预约日期|预约门店|预约项目)\\s*[：:]\\s*(.+?)\\s*$");
  private static final Pattern ISO_DATE = Pattern.compile("(20\\d{2})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?");
  private static final Pattern MONTH_DATE = Pattern.compile("(\\d{1,2})月(\\d{1,2})日?");
  private static final Pattern WEEKDAY_TIME = Pattern.compile(
      "(?:(?:本周|下周|这周)?\\s*)?周([一二三四五六日天])\\s*(上午|下午|晚上|早上|中午)?\\s*(\\d{1,2})(?:点|时)(?:(\\d{1,2})分|半)?");
  private static final Pattern CLOCK_TIME = Pattern.compile(
      "(?:(上午|下午|晚上|早上|中午)\\s*)?(\\d{1,2})(?:(?:点|时)(?:(\\d{1,2})分|半)?|:(\\d{2}))");

  private final Clock clock;

  public AppointmentMessageParser() {
    this(Clock.system(ZoneId.of("Asia/Shanghai")));
  }

  AppointmentMessageParser(Clock clock) {
    this.clock = clock;
  }

  public Optional<AppointmentDetails> parse(
      List<CustomerMessageSentEvent.ChatMessage> rawMessages,
      String sentText) {
    String text = join(rawMessages, sentText);
    if (text.isBlank()) {
      return Optional.empty();
    }
    var labels = labels(text);
    String rawDate = first(labels, "预约日期");
    String rawTime = first(labels, "预约时间");
    String rawDateTime = rawDate + " " + rawTime;
    LocalDate date = parseDate(rawDate);
    String time = parseTime(rawTime);
    if (date == null || time == null) {
      Matcher weekday = WEEKDAY_TIME.matcher(text);
      if (weekday.find()) {
        date = dateForWeekday(weekday.group(1));
        if (time == null) {
          time = timeFromWeekday(weekday);
        }
      }
    }
    if (date == null) {
      date = parseDate(rawDateTime);
    }
    if (time == null) {
      Matcher clockTime = CLOCK_TIME.matcher(rawDateTime);
      if (clockTime.find()) {
        time = timeFromClock(clockTime);
      }
    }
    String store = clean(first(labels, "预约门店"));
    String projectText = clean(first(labels, "预约项目"));
    String personName = clean(first(labels, "预约人"));
    List<String> projects = splitProjects(projectText);
    if (date == null || time == null || store.isBlank() || projects.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new AppointmentDetails(personName, date, time, store, projects));
  }

  private String join(List<CustomerMessageSentEvent.ChatMessage> rawMessages, String sentText) {
    StringBuilder text = new StringBuilder();
    if (rawMessages != null) {
      for (var message : rawMessages) {
        if (message != null && message.text() != null && !message.text().isBlank()) {
          if (text.length() > 0) text.append('\n');
          text.append(message.text().trim());
        }
      }
    }
    if (sentText != null && !sentText.isBlank()) {
      if (text.length() > 0) text.append('\n');
      text.append(sentText.trim());
    }
    return text.toString();
  }

  private java.util.Map<String, String> labels(String text) {
    java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
    Matcher matcher = LABELED.matcher(text);
    while (matcher.find()) {
      result.put(matcher.group(1), clean(matcher.group(2)));
    }
    return result;
  }

  private String first(java.util.Map<String, String> labels, String key) {
    String value = labels.get(key);
    if ((value == null || value.isBlank()) && "预约日期".equals(key)) value = labels.get("预约时间");
    return value == null ? "" : value;
  }

  private LocalDate parseDate(String raw) {
    if (raw == null || raw.isBlank()) return null;
    Matcher iso = ISO_DATE.matcher(raw);
    if (iso.find()) {
      try { return LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3))); }
      catch (DateTimeException | NumberFormatException ignored) { return null; }
    }
    Matcher month = MONTH_DATE.matcher(raw);
    if (month.find()) {
      try {
        LocalDate today = today();
        LocalDate value = LocalDate.of(today.getYear(), Integer.parseInt(month.group(1)), Integer.parseInt(month.group(2)));
        return value.isBefore(today) ? value.plusYears(1) : value;
      } catch (DateTimeException | NumberFormatException ignored) { return null; }
    }
    return null;
  }

  private String parseTime(String raw) {
    if (raw == null || raw.isBlank()) return null;
    Matcher matcher = CLOCK_TIME.matcher(raw);
    return matcher.find() ? timeFromClock(matcher) : null;
  }

  private String timeFromWeekday(Matcher matcher) {
    String period = matcher.group(2);
    int hour = Integer.parseInt(matcher.group(3));
    int minute = matcher.group(4) == null ? (matcher.group(0).contains("半") ? 30 : 0) : Integer.parseInt(matcher.group(4));
    return formatTime(adjustHour(hour, period), minute);
  }

  private String timeFromClock(Matcher matcher) {
    String period = matcher.group(1);
    int hour = Integer.parseInt(matcher.group(2));
    String minuteText = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
    int minute = minuteText == null ? (matcher.group(0).contains("半") ? 30 : 0) : Integer.parseInt(minuteText);
    return formatTime(adjustHour(hour, period), minute);
  }

  private int adjustHour(int hour, String period) {
    if (("下午".equals(period) || "晚上".equals(period)) && hour < 12) return hour + 12;
    if ("中午".equals(period) && hour < 11) return hour + 12;
    return hour;
  }

  private String formatTime(int hour, int minute) {
    return hour < 0 || hour > 23 || minute < 0 || minute > 59 ? null : String.format(Locale.ROOT, "%02d:%02d", hour, minute);
  }

  private LocalDate dateForWeekday(String value) {
    DayOfWeek target = switch (value) {
      case "一" -> DayOfWeek.MONDAY; case "二" -> DayOfWeek.TUESDAY; case "三" -> DayOfWeek.WEDNESDAY;
      case "四" -> DayOfWeek.THURSDAY; case "五" -> DayOfWeek.FRIDAY; case "六" -> DayOfWeek.SATURDAY;
      default -> DayOfWeek.SUNDAY;
    };
    LocalDate today = today();
    long days = (target.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
    return today.plus(days, ChronoUnit.DAYS);
  }

  private List<String> splitProjects(String value) {
    if (value == null || value.isBlank()) return List.of();
    Set<String> unique = new LinkedHashSet<>();
    for (String item : value.split("[、,，/和及]+")) {
      String clean = clean(item);
      if (!clean.isBlank()) unique.add(clean);
    }
    return List.copyOf(unique);
  }

  private String clean(String value) {
    if (value == null) return "";
    return value.replaceAll("^[：:\\s]+|[\\s]+$", "").trim();
  }

  private LocalDate today() { return LocalDate.now(clock); }

  public record AppointmentDetails(String personName, LocalDate date, String time, String store, List<String> projects) {
    public AppointmentDetails {
      projects = projects == null ? List.of() : List.copyOf(new ArrayList<>(projects));
    }
  }
}
