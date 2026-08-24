package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @ActiveProfiles("test")
class Milestone2EDriveSchemaIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired JdbcTemplate jdbc; @Autowired Flyway flyway;
    @Test void v7CreatesOnlyApprovedDriveTablesAndHibernateValidates(){
        assertThat(List.of(flyway.info().applied()).stream().filter(i->i.getVersion()!=null).map(i->i.getVersion().getVersion()))
                .containsExactly("1","2","3","4","5","6","6.1","7","8","9","10","11","12","13","14","15");
        assertThat(table("product_storage_folders")).isTrue();assertThat(table("product_storage_subfolders")).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
    }
    @Test void constraintsImmutabilityAndDeleteProtectionRejectDirectSql(){
        UUID product=product();UUID folder=UUID.randomUUID();UUID child=UUID.randomUUID();
        jdbc.update("INSERT INTO product_storage_folders(storage_folder_uuid,product_uuid,storage_provider,root_folder_id,product_folder_id) VALUES (?,?,?,?,?)",folder,product,"GOOGLE_DRIVE","root","product-folder");
        jdbc.update("INSERT INTO product_storage_subfolders(storage_subfolder_uuid,storage_folder_uuid,folder_role,provider_folder_id) VALUES (?,?,?,?)",child,folder,"IMAGES","images-folder");
        assertRejected("UPDATE product_storage_folders SET root_folder_id='changed' WHERE storage_folder_uuid=?",folder);
        assertRejected("UPDATE product_storage_subfolders SET provider_folder_id='changed' WHERE storage_subfolder_uuid=?",child);
        assertRejected("DELETE FROM product_storage_subfolders WHERE storage_subfolder_uuid=?",child);
        assertRejected("DELETE FROM product_storage_folders WHERE storage_folder_uuid=?",folder);
        assertThatThrownBy(()->jdbc.update("INSERT INTO product_storage_subfolders(storage_subfolder_uuid,storage_folder_uuid,folder_role,provider_folder_id) VALUES (?,?,?,?)",UUID.randomUUID(),folder,"INVALID","bad"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO product_storage_subfolders(storage_subfolder_uuid,storage_folder_uuid,folder_role,provider_folder_id) VALUES (?,?,?,?)",UUID.randomUUID(),folder,"IMAGES","duplicate-role"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO product_storage_folders(storage_folder_uuid,product_uuid,storage_provider,root_folder_id,product_folder_id) VALUES (?,?,?,?,?)",UUID.randomUUID(),product,"OTHER","root2","product2"))
                .isInstanceOf(DataAccessException.class);
    }
    private UUID product(){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO products(product_uuid,product_id,product_name,lifecycle_status,version) VALUES (?,?,?,?,0)",id,"PROD-"+String.format("%08d",Math.abs(id.hashCode())%100000000),"Drive Product","ACTIVE");return id;}
    private boolean table(String name){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=current_schema() AND table_name=?)",Boolean.class,name));}
    private void assertRejected(String sql,Object...args){assertThatThrownBy(()->jdbc.update(sql,args)).isInstanceOf(DataAccessException.class);}
}
