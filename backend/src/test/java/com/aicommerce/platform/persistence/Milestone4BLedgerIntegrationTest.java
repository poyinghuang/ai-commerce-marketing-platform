package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import com.aicommerce.platform.delivery.application.Stage4BTransactions;
import com.aicommerce.platform.delivery.domain.PlatformBudgetType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class Milestone4BLedgerIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17.6-alpine3.22");
    @Autowired JdbcTemplate jdbc;
    @Autowired Stage4BTransactions transactions;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void v13TablesHibernateMappingsAndTaipeiBoundaryAreAvailable() {
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('platform_operation_batches','platform_budget_reservations','platform_account_budget_days')",String.class))
                .containsExactlyInAnyOrder("platform_operation_batches","platform_budget_reservations","platform_account_budget_days");
        assertThat(jdbc.queryForObject("SELECT platform_taipei_business_date('2026-01-01T15:59:59Z')",LocalDate.class)).isEqualTo(LocalDate.of(2026,1,1));
        assertThat(jdbc.queryForObject("SELECT platform_taipei_business_date('2026-01-01T16:00:00Z')",LocalDate.class)).isEqualTo(LocalDate.of(2026,1,2));
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success AND version='13'",String.class)).isEqualTo("13");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='audit_logs' AND column_name='stage4b_operation_ordinal'",Integer.class)).isEqualTo(1);
    }

    @Test void stage4BAuditOrdinalIsDatabaseOwnedUniqueAndStage4AAuditsRemainNullable() {
        UUID plan=plan();var campaign=transactions.confirmCampaign(UUID.randomUUID(),plan,0,"ordinal-campaign").operation();
        List<Short> ordinals=jdbc.queryForList("SELECT stage4b_operation_ordinal FROM audit_logs WHERE operation_uuid=? ORDER BY stage4b_operation_ordinal",Short.class,campaign.getOperationUuid());
        assertThat(ordinals).containsExactly((short)0,(short)1,(short)2);
        assertSqlState23514(()->jdbc.update("INSERT INTO audit_logs(audit_uuid,operation_uuid,request_id,actor_type,actor_id,source,action,entity_type,entity_uuid,stage4b_operation_ordinal) VALUES (?,?, 'forged','SYSTEM','test','SYSTEM','UPDATE','FORGED',?,99)",UUID.randomUUID(),campaign.getOperationUuid(),campaign.getEntityUuid()));
        new TransactionTemplate(transactionManager).executeWithoutResult(status->{UUID operation=UUID.randomUUID(),audit=UUID.randomUUID();jdbc.update("INSERT INTO audit_logs(audit_uuid,operation_uuid,request_id,actor_type,actor_id,source,action,entity_type,entity_uuid) VALUES (?,?,'stage4a-compatible','SYSTEM','test','SYSTEM','UPDATE','NON_PLATFORM',?)",audit,operation,UUID.randomUUID());assertThat(jdbc.queryForObject("SELECT stage4b_operation_ordinal FROM audit_logs WHERE audit_uuid=?",Short.class,audit)).isNull();status.setRollbackOnly();});
        String pristine=snapshot();
        assertState("23514",()->tx(()->{UUID operation=UUID.randomUUID();jdbc.update("INSERT INTO audit_logs(audit_uuid,operation_uuid,request_id,actor_type,actor_id,source,action,entity_type,entity_uuid) VALUES (?,?,'audit-before-batch','SYSTEM','test','SYSTEM','UPDATE','NON_PLATFORM',?)",UUID.randomUUID(),operation,UUID.randomUUID());jdbc.update("INSERT INTO platform_operation_batches(operation_batch_uuid,operation_uuid,platform_account_uuid,client_request_uuid,requested_actor_type,requested_actor_id,currency,business_date,reserved_amount,created_at,version) VALUES (?,?, '00000000-0000-4000-8000-00000000005b',?,'LOCAL_ADMIN','local-admin','TWD',current_date,0,current_timestamp,0)",UUID.randomUUID(),operation,UUID.randomUUID());jdbc.execute("SET CONSTRAINTS trg_platform_audit_ownership_from_batch IMMEDIATE");},false));
        assertThat(snapshot()).isEqualTo(pristine);
    }

    @Test
    void successfulBudgetAuthorizationIsAnchoredAndAllLedgerRowsRejectMutationAndDelete() {
        BigDecimal dayBefore=jdbc.queryForObject("SELECT COALESCE(sum(reserved_amount),0) FROM platform_account_budget_days",BigDecimal.class);
        UUID plan=plan();
        var campaign=transactions.confirmCampaign(UUID.randomUUID(),plan,0,"stage4b-ledger-campaign").operation();
        var adSet=transactions.confirmAdSet(campaign.getEntityUuid(),UUID.randomUUID(),PlatformBudgetType.DAILY,
                new BigDecimal("50"),0,0,"stage4b-ledger-adset").operation();
        UUID batch=jdbc.queryForObject("SELECT operation_batch_uuid FROM platform_operation_batches WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid());
        UUID reservation=jdbc.queryForObject("SELECT budget_reservation_uuid FROM platform_budget_reservations WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid());
        UUID day=jdbc.queryForObject("SELECT account_budget_day_uuid FROM platform_budget_reservations WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid());
        assertThat(jdbc.queryForObject("SELECT reserved_amount FROM platform_operation_batches WHERE operation_batch_uuid=?",BigDecimal.class,batch)).isEqualByComparingTo("50");
        BigDecimal expectedDay=dayBefore.add(new BigDecimal("50"));assertThat(jdbc.queryForObject("SELECT reserved_amount FROM platform_account_budget_days WHERE account_budget_day_uuid=?",BigDecimal.class,day)).isEqualByComparingTo(expectedDay);

        assertSqlState23514(() -> jdbc.update("UPDATE platform_operation_batches SET reserved_amount=49 WHERE operation_batch_uuid=?",batch));
        assertSqlState23514(() -> jdbc.update("DELETE FROM platform_operation_batches WHERE operation_batch_uuid=?",batch));
        assertSqlState23514(() -> jdbc.update("UPDATE platform_budget_reservations SET reserved_amount=49 WHERE budget_reservation_uuid=?",reservation));
        assertSqlState23514(() -> jdbc.update("DELETE FROM platform_budget_reservations WHERE budget_reservation_uuid=?",reservation));
        assertSqlState23514(() -> jdbc.update("UPDATE platform_account_budget_days SET reserved_amount=51 WHERE account_budget_day_uuid=?",day));
        assertSqlState23514(() -> jdbc.update("DELETE FROM platform_account_budget_days WHERE account_budget_day_uuid=?",day));
        assertThat(jdbc.queryForObject("SELECT reserved_amount FROM platform_account_budget_days WHERE account_budget_day_uuid=?",BigDecimal.class,day)).isEqualByComparingTo(expectedDay);
    }

    @Test void arbitraryAccountPostV13Stage4BOperationCannotCommitWithoutBatch() {
        UUID account=UUID.randomUUID(),plan=UUID.randomUUID(),campaign=UUID.randomUUID(),operation=UUID.randomUUID(),request=UUID.randomUUID();
        jdbc.update("INSERT INTO platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) VALUES (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",account,"universal-"+account,account.toString().replace("-","").repeat(2));
        jdbc.update("INSERT INTO campaign_plans(campaign_uuid,campaign_name) VALUES (?,'Universal')",plan);
        jdbc.update("INSERT INTO platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) VALUES (?,?,?,'OUTCOME_SALES','Asia/Taipei')",campaign,plan,account);
        String payload="{\"schemaVersion\":1,\"operationType\":\"CREATE_CAMPAIGN\",\"entityType\":\"CAMPAIGN\",\"entityUuid\":\""+campaign+"\",\"platformCampaignUuid\":\""+campaign+"\",\"campaignUuid\":\""+plan+"\",\"objective\":\"OUTCOME_SALES\",\"desiredState\":\"PAUSED\",\"accountTimezone\":\"Asia/Taipei\"}";
        assertSqlState23514(()->new TransactionTemplate(transactionManager).executeWithoutResult(s->jdbc.update("INSERT INTO platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_campaign_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id) VALUES (?,?, 'CREATE_CAMPAIGN','CAMPAIGN',?,?,?,?::jsonb,?,'LOCAL_ADMIN','local-admin','universal')",operation,account,campaign,request,"a".repeat(64),payload,"b".repeat(64))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operations WHERE operation_uuid=?",Integer.class,operation)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_operation_batches WHERE operation_uuid=?",Integer.class,operation)).isZero();
    }

    @Test void malformedBatchFirstInsertAndDeferredCommitMatrixRollsBackEntireGraph() {
        Graph graph=graph();String pristine=snapshot();
        assertState("23503",()->tx(()->insertBatch(graph,UUID.randomUUID(),BigDecimal.ZERO),true));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->{UUID operation=UUID.randomUUID();insertOperation(graph,operation,UUID.randomUUID(),"25","30");insertBatch(graph,operation,new BigDecimal("5"));},true));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->{UUID operation=UUID.randomUUID(),batch=insertBatch(graph,operation,new BigDecimal("5"));insertReservation(graph,batch,UUID.randomUUID(),graph.day,"INCREASE","25","30","5");},true));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->insertReservation(graph,UUID.randomUUID(),UUID.randomUUID(),graph.day,"INCREASE","25","30","5"),false));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->{UUID operation=UUID.randomUUID();insertBatch(graph,operation,new BigDecimal("5"));insertOperation(graph,operation,UUID.randomUUID(),"25","30");},true));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->{UUID operation=UUID.randomUUID(),request=UUID.randomUUID(),batch=insertBatch(graph,operation,new BigDecimal("5"),request);insertReservation(graph,batch,operation,graph.day,"INCREASE","25","30","5");insertOperation(graph,operation,request,"25","30");},true));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23505",()->tx(()->{UUID operation=UUID.randomUUID(),request=UUID.randomUUID(),batch=insertBatch(graph,operation,new BigDecimal("10"),request);insertReservation(graph,batch,operation,graph.day,"INCREASE","25","30","5");insertReservation(graph,batch,operation,graph.day,"INCREASE","25","30","5");},false));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23503",()->tx(()->{UUID operation=UUID.randomUUID(),request=UUID.randomUUID(),batch=insertBatch(graph,operation,new BigDecimal("5"),request);insertReservation(graph,batch,operation,UUID.randomUUID(),"INCREASE","25","30","5");insertOperation(graph,operation,request,"25","30");},true));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->{UUID operation=UUID.randomUUID(),request=UUID.randomUUID();insertBatch(graph,operation,BigDecimal.ZERO,request);insertUnapprovedOperation(graph,operation,request);},false));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->insertBatch(graph,graph.adSetCreateOperation,BigDecimal.ZERO),false));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23514",()->tx(()->{UUID operation=UUID.randomUUID(),request=UUID.randomUUID(),batch=insertBatch(graph,operation,new BigDecimal("4"),request);insertReservation(graph,batch,operation,graph.day,"INCREASE","25","30","4");insertOperation(graph,operation,request,"25","30");},true));assertThat(snapshot()).isEqualTo(pristine);
        assertState("23503",()->tx(()->{UUID operation=UUID.randomUUID(),request=UUID.randomUUID(),batch=insertBatch(graph,operation,new BigDecimal("5"),request);insertReservation(graph,batch,operation,graph.day,UUID.randomUUID(),graph.account,"INCREASE","25","30","5");insertOperation(graph,operation,request,"25","30");},true));assertThat(snapshot()).isEqualTo(pristine);
        UUID otherAccount=UUID.randomUUID();jdbc.update("INSERT INTO platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) VALUES (?,'FAKE','TEST',?,?, 'TWD','Asia/Taipei')",otherAccount,"wrong-"+otherAccount,hex(otherAccount));
        String afterAccount=snapshot();
        assertState("23514",()->tx(()->{UUID operation=UUID.randomUUID(),request=UUID.randomUUID(),batch=insertBatch(graph,operation,new BigDecimal("5"),request);insertReservation(graph,batch,operation,graph.day,graph.adSet,otherAccount,"INCREASE","25","30","5");insertOperation(graph,operation,request,"25","30");},true));assertThat(snapshot()).isEqualTo(afterAccount);
        assertState("23514",()->tx(()->jdbc.update("INSERT INTO platform_account_budget_days(platform_account_uuid,business_date,currency,account_budget_day_uuid,reserved_amount,ceiling_amount,created_at,updated_at,version) VALUES (?,platform_taipei_business_date(statement_timestamp()),'TWD',?,99,1,TIMESTAMPTZ '1999-01-01',TIMESTAMPTZ '2099-01-01',99)",otherAccount,UUID.randomUUID()),true));assertThat(snapshot()).isEqualTo(afterAccount);
    }

    @Test void isolatedExtraAggregateCeilingAndBatchCapFailuresNameInvariantAndRollbackCompleteGraph() {
        Graph graph=graph();String pristine=snapshot();
        assertStateInvariant("23514","account budget day ledger sum mismatch",()->tx(()->jdbc.update("UPDATE platform_account_budget_days SET reserved_amount=reserved_amount+1,version=version+1,updated_at=statement_timestamp()+interval '1 second' WHERE account_budget_day_uuid=?",graph.day),true));
        assertThat(snapshot()).isEqualTo(pristine);
        assertStateInvariant("23514","invalid account budget day update",()->tx(()->jdbc.update("UPDATE platform_account_budget_days SET ceiling_amount=999 WHERE account_budget_day_uuid=?",graph.day),false));
        assertThat(snapshot()).isEqualTo(pristine);
        assertStateInvariant("23514","reserved_amount",()->tx(()->insertBatch(graph,UUID.randomUUID(),new BigDecimal("301")),false));
        assertThat(snapshot()).isEqualTo(pristine);
    }

    @Test void forgedAnchorsAreDatabaseOwnedAndZeroReleaseIsValidWithoutDayMutation() {
        Graph graph=graph();String pristine=snapshot();
        LocalDate databaseDate=jdbc.queryForObject("SELECT platform_taipei_business_date(statement_timestamp())",LocalDate.class);
        new TransactionTemplate(transactionManager).executeWithoutResult(status->{UUID operation=UUID.randomUUID(),request=UUID.randomUUID(),batch=insertBatch(graph,operation,BigDecimal.ZERO,request);insertReservation(graph,batch,operation,graph.day,"DECREASE_NO_RELEASE","25","20","0");insertOperation(graph,operation,request,"25","20");jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");Map<String,Object> anchored=row("platform_operation_batches","operation_batch_uuid",batch);assertThat(anchored.get("currency")).isEqualTo("TWD");assertThat(anchored.get("version")).isEqualTo(0L);assertThat(jdbc.queryForObject("SELECT business_date FROM platform_operation_batches WHERE operation_batch_uuid=?",LocalDate.class,batch)).isEqualTo(databaseDate);assertThat(jdbc.queryForObject("SELECT count(*) FROM platform_budget_reservations WHERE operation_uuid=? AND created_at=(SELECT created_at FROM platform_operation_batches WHERE operation_uuid=?)",Integer.class,operation,operation)).isEqualTo(1);status.setRollbackOnly();});
        assertThat(snapshot()).isEqualTo(pristine);
    }

    @Test void committedZeroReleaseUsesDatabaseDateAndLeavesAggregateCeilingVersionAndTimestampsUnchanged() {
        Graph graph=graph();Map<String,Object> before=row("platform_account_budget_days","account_budget_day_uuid",graph.day);UUID operation=UUID.randomUUID(),request=UUID.randomUUID();
        tx(()->{UUID batch=insertBatch(graph,operation,BigDecimal.ZERO,request);insertReservation(graph,batch,operation,graph.day,"DECREASE_NO_RELEASE","25","20","0");insertOperation(graph,operation,request,"25","20");},true);
        Map<String,Object> after=row("platform_account_budget_days","account_budget_day_uuid",graph.day);assertThat(after).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT reservation_kind,reserved_amount FROM platform_budget_reservations WHERE operation_uuid=?",operation)).containsEntry("reservation_kind","DECREASE_NO_RELEASE");
        assertThat(jdbc.queryForObject("SELECT business_date FROM platform_budget_reservations WHERE operation_uuid=?",LocalDate.class,operation)).isEqualTo(jdbc.queryForObject("SELECT platform_taipei_business_date(statement_timestamp())",LocalDate.class));
        assertThat((BigDecimal)jdbc.queryForMap("SELECT reservation_kind,reserved_amount,business_date FROM platform_budget_reservations WHERE operation_uuid=?",operation).get("reserved_amount")).isEqualByComparingTo("0");
    }

    private UUID plan(){UUID id=UUID.randomUUID();jdbc.update("""
      INSERT INTO campaign_plans(campaign_uuid,campaign_name,start_date,end_date,objective,platform,budget_daily,budget_total,currency)
      VALUES (?,'Stage 4B ledger',?,?, 'OUTCOME_SALES','META',100.0000,300.0000,'TWD')
      """,id,LocalDate.now().plusDays(10),LocalDate.now().plusDays(20));return id;}
    private Graph graph(){UUID p=plan();var campaign=transactions.confirmCampaign(UUID.randomUUID(),p,0,"direct-sql-campaign").operation();var adSet=transactions.confirmAdSet(campaign.getEntityUuid(),UUID.randomUUID(),PlatformBudgetType.DAILY,new BigDecimal("25"),0,0,"direct-sql-adset").operation();UUID account=jdbc.queryForObject("SELECT platform_account_uuid FROM platform_ad_sets WHERE platform_ad_set_uuid=?",UUID.class,adSet.getEntityUuid());UUID ad=approvedAd(account,adSet.getEntityUuid(),p);return new Graph(account,adSet.getEntityUuid(),jdbc.queryForObject("SELECT account_budget_day_uuid FROM platform_budget_reservations WHERE operation_uuid=?",UUID.class,adSet.getOperationUuid()),adSet.getOperationUuid(),ad);}
    private UUID insertBatch(Graph graph,UUID operation,BigDecimal amount){return insertBatch(graph,operation,amount,UUID.randomUUID());}
    private UUID insertBatch(Graph graph,UUID operation,BigDecimal amount,UUID request){UUID batch=UUID.randomUUID();jdbc.update("INSERT INTO platform_operation_batches(operation_batch_uuid,operation_uuid,platform_account_uuid,client_request_uuid,requested_actor_type,requested_actor_id,expected_entity_version,currency,business_date,reserved_amount,created_at,version) VALUES (?,?,?,?,'LOCAL_ADMIN','local-admin',0,'USD',DATE '1999-01-01',?,TIMESTAMPTZ '2099-01-01 00:00:00Z',99)",batch,operation,graph.account,request,amount);return batch;}
    private void insertReservation(Graph graph,UUID batch,UUID operation,UUID day,String kind,String previous,String next,String reserved){jdbc.update("INSERT INTO platform_budget_reservations(budget_reservation_uuid,operation_batch_uuid,operation_uuid,platform_account_uuid,account_budget_day_uuid,platform_ad_set_uuid,reservation_kind,previous_budget_amount,new_budget_amount,reserved_amount,currency,business_date,created_at) VALUES (?,?,?,?,?,?,?,?::numeric,?::numeric,?::numeric,'USD',DATE '1999-01-01',TIMESTAMPTZ '2099-01-01 00:00:00Z')",UUID.randomUUID(),batch,operation,graph.account,day,graph.adSet,kind,previous,next,reserved);}
    private void insertReservation(Graph graph,UUID batch,UUID operation,UUID day,UUID adSet,UUID account,String kind,String previous,String next,String reserved){jdbc.update("INSERT INTO platform_budget_reservations(budget_reservation_uuid,operation_batch_uuid,operation_uuid,platform_account_uuid,account_budget_day_uuid,platform_ad_set_uuid,reservation_kind,previous_budget_amount,new_budget_amount,reserved_amount,currency,business_date,created_at) VALUES (?,?,?,?,?,?,?,?::numeric,?::numeric,?::numeric,'USD',DATE '1999-01-01',TIMESTAMPTZ '2099-01-01 00:00:00Z')",UUID.randomUUID(),batch,operation,account,day,adSet,kind,previous,next,reserved);}
    private void insertOperation(Graph graph,UUID operation,UUID request,String previous,String next){String payload="{\"schemaVersion\":1,\"operationType\":\"UPDATE_BUDGET\",\"entityType\":\"AD_SET\",\"entityUuid\":\""+graph.adSet+"\",\"platformAdSetUuid\":\""+graph.adSet+"\",\"expectedEntityVersion\":0,\"budgetType\":\"DAILY\",\"currency\":\"TWD\",\"previousBudgetAmount\":"+previous+",\"newBudgetAmount\":"+next+"}";jdbc.update("INSERT INTO platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_ad_set_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id) VALUES (?,?,'UPDATE_BUDGET','AD_SET',?,?,?,?::jsonb,?,'LOCAL_ADMIN','local-admin','direct-sql')",operation,graph.account,graph.adSet,request,hex(operation),payload,hex(request));}
    private void insertUnapprovedOperation(Graph graph,UUID operation,UUID request){Map<String,Object> evidence=jdbc.queryForMap("SELECT product_uuid,asset_uuid,generation_output_uuid,review_decision_uuid,approved_checksum_sha256 FROM platform_ads WHERE platform_ad_uuid=?",graph.ad);String payload="{\"schemaVersion\":1,\"operationType\":\"CREATE_AD\",\"entityType\":\"AD\",\"entityUuid\":\""+graph.ad+"\",\"platformAdUuid\":\""+graph.ad+"\",\"platformAdSetUuid\":\""+graph.adSet+"\",\"productUuid\":\""+evidence.get("product_uuid")+"\",\"assetUuid\":\""+evidence.get("asset_uuid")+"\",\"generationOutputUuid\":\""+evidence.get("generation_output_uuid")+"\",\"reviewDecisionUuid\":\""+evidence.get("review_decision_uuid")+"\",\"approvedChecksumSha256\":\""+evidence.get("approved_checksum_sha256")+"\",\"creativeMappingKey\":\"IMAGE_PRIMARY_V1\",\"desiredState\":\"PAUSED\"}";jdbc.update("INSERT INTO platform_operations(operation_uuid,platform_account_uuid,operation_type,entity_type,platform_ad_uuid,client_request_uuid,idempotency_key,request_payload,request_sha256,requested_actor_type,requested_actor_id,request_id) VALUES (?,?,'CREATE_AD','AD',?,?,?,?::jsonb,?,'LOCAL_ADMIN','local-admin','direct-sql')",operation,graph.account,graph.ad,request,hex(operation),payload,hex(request));}
    private UUID approvedAd(UUID account,UUID adSet,UUID plan){UUID product=UUID.randomUUID(),source=UUID.randomUUID(),asset=UUID.randomUUID(),template=UUID.randomUUID(),templateVersion=UUID.randomUUID(),batch=UUID.randomUUID(),job=UUID.randomUUID(),output=UUID.randomUUID(),review=UUID.randomUUID(),ad=UUID.randomUUID();jdbc.update("INSERT INTO products(product_uuid,product_id,product_name,lifecycle_status) VALUES (?,?,'SQL evidence','ACTIVE')",product,"PROD-"+String.format("%08d",Math.abs(product.hashCode())%100000000));jdbc.update("INSERT INTO campaign_products(campaign_product_uuid,campaign_uuid,product_uuid) VALUES (?,?,?)",UUID.randomUUID(),plan,product);jdbc.update("INSERT INTO assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) VALUES (?,?,?,'IMAGE','SOURCE',?)",source,product,plan,"d".repeat(64));jdbc.update("INSERT INTO assets(asset_uuid,product_uuid,campaign_uuid,asset_type,purpose,checksum_sha256) VALUES (?,?,?,'IMAGE','GENERATED',?)",asset,product,plan,"e".repeat(64));jdbc.update("INSERT INTO ai_prompt_templates(prompt_template_uuid,template_key,generation_type,display_name) VALUES (?,?,'IMAGE','SQL image')",template,"sql."+template);jdbc.update("INSERT INTO ai_prompt_template_versions(prompt_template_version_uuid,prompt_template_uuid,version_number,template_text,input_schema,content_sha256,created_by) VALUES (?,?,1,'image','{}'::jsonb,?,'sql')",templateVersion,template,"a".repeat(64));jdbc.update("INSERT INTO ai_generation_batches(generation_batch_uuid,product_uuid,status,currency,estimated_cost,reserved_cost,requested_job_count,succeeded_job_count,created_by) VALUES (?,?,'COMPLETED','TWD',0,0,1,1,'sql')",batch,product);jdbc.update("INSERT INTO ai_generation_jobs(generation_job_uuid,generation_batch_uuid,product_uuid,prompt_template_version_uuid,generation_type,provider_key,model_key,status,rendered_prompt,input_snapshot,estimated_cost,reserved_cost,actual_cost,currency,submitted_at,started_at,completed_at) VALUES (?,?,?,?,'IMAGE','stub','stub','SUCCEEDED','image','{}'::jsonb,0,0,0,'TWD',current_timestamp,current_timestamp,current_timestamp)",job,batch,product,templateVersion);jdbc.update("INSERT INTO ai_generation_outputs(generation_output_uuid,generation_job_uuid,generation_batch_uuid,product_uuid,generation_type,model_label,input_units,output_units,actual_cost,currency,safety_findings,provider_metadata,source_asset_uuid,generated_asset_uuid,generation_mode,workflow_key,workflow_version,image_width,image_height,media_type,size_bytes,source_checksum_sha256,output_checksum_sha256,protected_pixels_sha256,preservation_algorithm,preservation_status,preservation_details) VALUES (?,?,?,?,'IMAGE','stub',0,0,0,'TWD','[]'::jsonb,'{}'::jsonb,?,?,'BACKGROUND_COMPOSITE','sql-v1','1',1,1,'image/png',1,?,?,?,'RGBA_MASK_EXACT_V1','PASSED','{\"changedPixelCount\":0,\"protectedPixelCount\":1}'::jsonb)",output,job,batch,product,source,asset,"d".repeat(64),"e".repeat(64),"f".repeat(64));new TransactionTemplate(transactionManager).executeWithoutResult(s->{jdbc.update("INSERT INTO ai_review_decisions(review_decision_uuid,generation_output_uuid,decision,reviewer_type,reviewer_id,request_id,reviewed_output_version,decided_at) VALUES (?,?,'APPROVED','LOCAL_ADMIN','sql','sql-review',0,current_timestamp)",review,output);jdbc.update("UPDATE ai_generation_outputs SET review_status='APPROVED',version=1 WHERE generation_output_uuid=?",output);});jdbc.update("INSERT INTO platform_ads(platform_ad_uuid,platform_ad_set_uuid,platform_account_uuid,product_uuid,asset_uuid,generation_output_uuid,review_decision_uuid,approved_checksum_sha256,creative_mapping_key) VALUES (?,?,?,?,?,?,?,?, 'IMAGE_PRIMARY_V1')",ad,adSet,account,product,asset,output,review,"e".repeat(64));return ad;}
    private void tx(Runnable work,boolean forceDeferred){new TransactionTemplate(transactionManager).executeWithoutResult(status->{work.run();if(forceDeferred)jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");});}
    private String snapshot(){return jdbc.queryForObject("""
      SELECT jsonb_build_object(
        'accounts',(SELECT jsonb_agg(to_jsonb(t) ORDER BY platform_account_uuid) FROM platform_accounts t),
        'plans',(SELECT jsonb_agg(to_jsonb(t) ORDER BY campaign_uuid) FROM campaign_plans t),
        'products',(SELECT jsonb_agg(to_jsonb(t) ORDER BY product_uuid) FROM products t),
        'campaignProducts',(SELECT jsonb_agg(to_jsonb(t) ORDER BY campaign_product_uuid) FROM campaign_products t),
        'assets',(SELECT jsonb_agg(to_jsonb(t) ORDER BY asset_uuid) FROM assets t),
        'promptTemplates',(SELECT jsonb_agg(to_jsonb(t) ORDER BY prompt_template_uuid) FROM ai_prompt_templates t),
        'promptVersions',(SELECT jsonb_agg(to_jsonb(t) ORDER BY prompt_template_version_uuid) FROM ai_prompt_template_versions t),
        'generationBatches',(SELECT jsonb_agg(to_jsonb(t) ORDER BY generation_batch_uuid) FROM ai_generation_batches t),
        'generationJobs',(SELECT jsonb_agg(to_jsonb(t) ORDER BY generation_job_uuid) FROM ai_generation_jobs t),
        'generationOutputs',(SELECT jsonb_agg(to_jsonb(t) ORDER BY generation_output_uuid) FROM ai_generation_outputs t),
        'reviews',(SELECT jsonb_agg(to_jsonb(t) ORDER BY review_decision_uuid) FROM ai_review_decisions t),
        'campaigns',(SELECT jsonb_agg(to_jsonb(t) ORDER BY platform_campaign_uuid) FROM platform_campaigns t),
        'adsets',(SELECT jsonb_agg(to_jsonb(t) ORDER BY platform_ad_set_uuid) FROM platform_ad_sets t),
        'ads',(SELECT jsonb_agg(to_jsonb(t) ORDER BY platform_ad_uuid) FROM platform_ads t),
        'metrics',(SELECT jsonb_agg(to_jsonb(t) ORDER BY metric_snapshot_uuid) FROM platform_metric_snapshots t),
        'operations',(SELECT jsonb_agg(to_jsonb(t) ORDER BY operation_uuid) FROM platform_operations t),
        'attempts',(SELECT jsonb_agg(to_jsonb(t) ORDER BY operation_attempt_uuid) FROM platform_operation_attempts t),
        'batches',(SELECT jsonb_agg(to_jsonb(t) ORDER BY operation_batch_uuid) FROM platform_operation_batches t),
        'reservations',(SELECT jsonb_agg(to_jsonb(t) ORDER BY budget_reservation_uuid) FROM platform_budget_reservations t),
        'days',(SELECT jsonb_agg(to_jsonb(t) ORDER BY account_budget_day_uuid) FROM platform_account_budget_days t),
        'audit',(SELECT jsonb_agg(to_jsonb(t) ORDER BY audit_uuid) FROM audit_logs t),
        'changes',(SELECT jsonb_agg(to_jsonb(t) ORDER BY audit_uuid,change_order) FROM audit_log_changes t))::text
      """,String.class);}
    private static String hex(UUID id){return id.toString().replace("-","").repeat(2);}
    private static void assertState(String state,org.assertj.core.api.ThrowableAssert.ThrowingCallable call){assertThatThrownBy(call).satisfies(failure->{Throwable current=failure;while(current.getCause()!=null)current=current.getCause();assertThat(current).isInstanceOf(SQLException.class);assertThat(((SQLException)current).getSQLState()).isEqualTo(state);});}
    private static void assertStateInvariant(String state,String invariant,org.assertj.core.api.ThrowableAssert.ThrowingCallable call){assertThatThrownBy(call).satisfies(failure->{Throwable current=failure;while(current.getCause()!=null)current=current.getCause();assertThat(current).isInstanceOf(SQLException.class);assertThat(((SQLException)current).getSQLState()).isEqualTo(state);assertThat(current.getMessage()).containsIgnoringCase(invariant);});}
    private Map<String,Object> row(String table,String key,UUID id){return jdbc.queryForMap("SELECT * FROM "+table+" WHERE "+key+"=?",id);}
    private int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    private record SqlCase(String name,String sql,Object...arguments){}
    private record Graph(UUID account,UUID adSet,UUID day,UUID adSetCreateOperation,UUID ad){}
    private static void assertSqlState23514(org.assertj.core.api.ThrowableAssert.ThrowingCallable call){
        assertThatThrownBy(call).isInstanceOf(DataIntegrityViolationException.class).satisfies(failure->{
            Throwable current=failure;while(current.getCause()!=null)current=current.getCause();
            assertThat(current).isInstanceOf(SQLException.class);assertThat(((SQLException)current).getSQLState()).isEqualTo("23514");
        });
    }
}
