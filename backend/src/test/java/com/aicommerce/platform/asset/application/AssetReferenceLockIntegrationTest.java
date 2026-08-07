package com.aicommerce.platform.asset.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.aicommerce.platform.creativeplan.infrastructure.persistence.CreativePlanJpaRepository;
import com.aicommerce.platform.product.application.CreateProductCommand;
import com.aicommerce.platform.product.application.ProductCommandService;
import com.aicommerce.platform.product.domain.Product;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @ActiveProfiles("test")
class AssetReferenceLockIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
 @Autowired CreativePlanJpaRepository plans; @Autowired ProductCommandService products; @Autowired JdbcTemplate jdbc;
 @Autowired PlatformTransactionManager transactions;

 @Test void activeReferenceReadLockPreventsLifecycleChangeUntilAssetTransactionCompletes(){
  Product product=products.create(new CreateProductCommand(null,"Lock Product",null,null,null,null,null,null,null,null,null),"product");
  UUID plan=UUID.randomUUID();
  jdbc.update("INSERT INTO creative_plans(creative_plan_uuid,product_uuid,plan_name) VALUES (?,?,?)",plan,product.getProductUuid(),"Locked Plan");
  TransactionTemplate tx=new TransactionTemplate(transactions);
  Boolean updateTimedOut=tx.execute(ignored->{
   assertThat(plans.findForAssetMutation(plan,product.getProductUuid())).isPresent();
   return CompletableFuture.supplyAsync(()->new TransactionTemplate(transactions).execute(inner->{
    jdbc.execute("SET LOCAL lock_timeout = '250ms'");
    try { jdbc.update("UPDATE creative_plans SET lifecycle_status='ARCHIVED',archived_at=now() WHERE creative_plan_uuid=?",plan); return false; }
    catch (DataAccessException expected) { return true; }
   })).orTimeout(2,TimeUnit.SECONDS).join();
  });
  assertThat(updateTimedOut).isTrue();
  assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM creative_plans WHERE creative_plan_uuid=?",String.class,plan)).isEqualTo("ACTIVE");
 }
}
