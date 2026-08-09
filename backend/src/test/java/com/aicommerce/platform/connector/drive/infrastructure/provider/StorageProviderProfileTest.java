package com.aicommerce.platform.connector.drive.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import com.aicommerce.platform.connector.drive.application.StorageProvider;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class StorageProviderProfileTest {
    @ParameterizedTest(name="{0}") @MethodSource("profiles")
    void selectsExactlyOneProvider(String scenario,String[] profiles,Class<? extends StorageProvider> expected){
        try(var context=new AnnotationConfigApplicationContext()){context.getEnvironment().setActiveProfiles(profiles);
            context.register(StubStorageProvider.class,GoogleDriveStorageProvider.class);context.refresh();
            Map<String,StorageProvider> beans=context.getBeansOfType(StorageProvider.class);assertThat(beans).hasSize(1);
            assertThat(beans.values().iterator().next()).isExactlyInstanceOf(expected);}
    }
    static Stream<Arguments> profiles(){return Stream.of(Arguments.of("local",new String[]{"local"},StubStorageProvider.class),
            Arguments.of("test",new String[]{"test"},StubStorageProvider.class),Arguments.of("default",new String[0],GoogleDriveStorageProvider.class),
            Arguments.of("production",new String[]{"production"},GoogleDriveStorageProvider.class),Arguments.of("production,local",new String[]{"production","local"},GoogleDriveStorageProvider.class),
            Arguments.of("production,test",new String[]{"production","test"},GoogleDriveStorageProvider.class));}
}
