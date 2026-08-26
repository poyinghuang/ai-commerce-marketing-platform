package com.aicommerce.platform.connector.drive.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicommerce.platform.connector.drive.application.StorageEnsureRequest;
import com.aicommerce.platform.connector.drive.application.StorageFolderTree;
import com.aicommerce.platform.connector.drive.domain.StorageFolderRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FakeObjectStorageProviderTest {

    private final FakeObjectStorageProvider provider = new FakeObjectStorageProvider();

    @Test
    void returnsOpaqueTreeWithEveryRoleAndNoSharedDrive() {
        UUID product = UUID.fromString("11111111-1111-4111-8111-111111111111");
        StorageFolderTree tree = provider.ensureProductTree(new StorageEnsureRequest(product, "PROD-00000001"));
        assertThat(tree.rootFolderId()).isEqualTo("fake-object-root");
        assertThat(tree.sharedDriveId()).isNull();
        assertThat(tree.productFolderId()).isEqualTo("fake-object-" + product + "-product");
        assertThat(tree.subfolderIds()).hasSize(StorageFolderRole.values().length);
        for (StorageFolderRole role : StorageFolderRole.values()) {
            assertThat(tree.subfolderIds().get(role))
                    .isEqualTo("fake-object-" + product + "-" + role.name().toLowerCase());
        }
        assertThat(tree.subfolderIds().values()).doesNotContain(tree.productFolderId());
    }

    @Test
    void sameProductYieldsTheSameOpaqueIdentifiers() {
        UUID product = UUID.randomUUID();
        StorageEnsureRequest request = new StorageEnsureRequest(product, "PROD-00000002");
        StorageFolderTree first = provider.ensureProductTree(request);
        StorageFolderTree second = provider.ensureProductTree(request);
        assertThat(second).isEqualTo(first);
    }
}
