package com.privateflow.modules.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.match.config.MatchConfig;
import com.privateflow.modules.match.config.MatchConfigProvider;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NicknamePrefixRemovalProcessorTest {

  @Mock
  private MatchConfigProvider configProvider;

  @Test
  void removesOnlyTheLongestConfiguredNicknamePrefix() {
    when(configProvider.get()).thenReturn(configWithRules("V-", "VIP-"));
    NicknamePrefixRemovalProcessor processor = new NicknamePrefixRemovalProcessor(configProvider);

    assertThat(processor.clean("  vip- 张三! ")).isEqualTo("张三");
    verify(configProvider).get();
  }

  @Test
  void doesNotTreatBusinessTagValuesAsNicknamePrefixes() {
    when(configProvider.get()).thenReturn(configWithRules("VIP-"));
    NicknamePrefixRemovalProcessor processor = new NicknamePrefixRemovalProcessor(configProvider);

    assertThat(processor.clean("PENDING 张三")).isEqualTo("PENDING 张三");
  }

  @Test
  void dependsOnMatchConfigurationWithoutAnyTagsModuleType() {
    Constructor<?>[] constructors = NicknamePrefixRemovalProcessor.class.getDeclaredConstructors();
    List<Class<?>> dependencyTypes = Arrays.stream(constructors)
        .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes()))
        .toList();

    assertThat(dependencyTypes).containsExactly(MatchConfigProvider.class);
    assertThat(dependencyTypes)
        .noneMatch(type -> type.getPackageName().startsWith("com.privateflow.modules.tags"));
  }

  private MatchConfig configWithRules(String... rules) {
    return new MatchConfig(List.of(rules), 5, 2000, 0.5d, 2);
  }
}
