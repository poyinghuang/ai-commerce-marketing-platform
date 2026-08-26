package com.aicommerce.platform.connector.drive.infrastructure.provider;

import com.aicommerce.platform.connector.drive.application.*;
import com.aicommerce.platform.connector.drive.domain.StorageFolderRole;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("(local | test) & !production")
@ConditionalOnProperty(name = "platform.storage.provider", havingValue = "stub", matchIfMissing = true)
public class StubStorageProvider implements StorageProvider {
    @Override public StorageFolderTree ensureProductTree(StorageEnsureRequest request){
        String prefix="stub-"+request.productUuid(); Map<StorageFolderRole,String> children=new EnumMap<>(StorageFolderRole.class);
        for(StorageFolderRole role:StorageFolderRole.values())children.put(role,prefix+"-"+role.name().toLowerCase(Locale.ROOT));
        return new StorageFolderTree("stub-root",null,prefix+"-product",children);
    }
}
