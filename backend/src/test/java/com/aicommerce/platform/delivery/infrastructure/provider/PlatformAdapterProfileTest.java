package com.aicommerce.platform.delivery.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformAdapterProfileTest {
 private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(DeterministicFakePlatformAdapter.class);

 @Test void explicitFakeAdapterExistsOnlyInLocalOrTestAndNeverDefaultOrProduction(){
  assertPresent("local","platform.adapter=fake",true);
  assertPresent("test","platform.adapter=fake",true);
  assertPresent("production","platform.adapter=fake",false);
  assertPresent("default","platform.adapter=fake",false);
  assertPresent("local","platform.adapter=disabled",false);
  new ApplicationContextRunner().withUserConfiguration(DeterministicFakePlatformAdapter.class).withInitializer(context->context.getEnvironment().setActiveProfiles("test")).run(context->assertThat(context).doesNotHaveBean(DeterministicFakePlatformAdapter.class));
 }

 private void assertPresent(String profile,String property,boolean expected){runner.withInitializer(context->context.getEnvironment().setActiveProfiles(profile)).withPropertyValues(property).run(context->{if(expected)assertThat(context).hasSingleBean(DeterministicFakePlatformAdapter.class);else assertThat(context).doesNotHaveBean(DeterministicFakePlatformAdapter.class);});}
}
