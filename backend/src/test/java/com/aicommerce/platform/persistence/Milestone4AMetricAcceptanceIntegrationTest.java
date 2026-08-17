package com.aicommerce.platform.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.Timestamp;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers @SpringBootTest @ActiveProfiles("test")
class Milestone4AMetricAcceptanceIntegrationTest {
 @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine3.22");
 @Autowired JdbcTemplate jdbc;

 @Test void appendOnlyRevisionsSupportDeterministicLatestAndAsOfAndRejectInvalidRows(){
  Fixture f=fixture("TWD","Asia/Taipei"); Instant fetched1=Instant.now().minusSeconds(120),fetched2=fetched1.plusSeconds(60);
  UUID first=insert(f,1,fetched1,"1".repeat(64),null,null);
  UUID second=insert(f,2,fetched2,"2".repeat(64),12L,java.math.BigDecimal.TEN);
  assertThat(jdbc.queryForObject("select metric_snapshot_uuid from platform_metric_snapshots where platform_campaign_uuid=? order by revision_number desc limit 1",UUID.class,f.campaign())).isEqualTo(second);
  assertThat(jdbc.queryForObject("select metric_snapshot_uuid from platform_metric_snapshots where platform_campaign_uuid=? and fetched_at<=? order by revision_number desc limit 1",UUID.class,f.campaign(),Timestamp.from(fetched1))).isEqualTo(first);
  assertThat(jdbc.queryForMap("select impressions,spend from platform_metric_snapshots where metric_snapshot_uuid=?",first).values()).allMatch(java.util.Objects::isNull);

  assertThatThrownBy(()->insert(f,4,fetched2.plusSeconds(60),"3".repeat(64),1L,java.math.BigDecimal.ONE)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->insert(f,2,fetched2.plusSeconds(60),"4".repeat(64),1L,java.math.BigDecimal.ONE)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->insert(f,3,fetched1,"5".repeat(64),1L,java.math.BigDecimal.ONE)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->insert(f,3,fetched2.plusSeconds(60),"2".repeat(64),1L,java.math.BigDecimal.ONE)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("insert into platform_metric_snapshots(metric_snapshot_uuid,platform_account_uuid,entity_type,platform_campaign_uuid,window_start,window_end,timezone,currency,impressions,revision_number,fetched_at,freshness_status,source_fingerprint) values (?,?, 'CAMPAIGN',?,?::timestamptz,?::timestamptz,'Asia/Taipei','TWD',-1,3,?,'FRESH',?)",UUID.randomUUID(),f.account(),f.campaign(),f.start().toString(),f.end().toString(),Timestamp.from(fetched2.plusSeconds(60)),"6".repeat(64))).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("update platform_metric_snapshots set freshness_status='DELAYED' where metric_snapshot_uuid=?",first)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->jdbc.update("delete from platform_metric_snapshots where metric_snapshot_uuid=?",first)).isInstanceOf(RuntimeException.class);
 }

 @Test void metricAccountCurrencyTimezoneLifecycleAndConcurrentNextRevisionAreEnforced() throws Exception {
  Fixture f=fixture("TWD","Asia/Taipei"); Instant firstFetch=Instant.now().minusSeconds(60);
  insert(f,1,firstFetch,"7".repeat(64),1L,java.math.BigDecimal.ONE);
  var gate=new CountDownLatch(1); var winners=new AtomicInteger();
  try(var pool=Executors.newFixedThreadPool(2)){
   for(int i=0;i<2;i++){final int n=i;pool.submit(()->{try{gate.await();insert(f,2,firstFetch.plusSeconds(30+n),String.valueOf(8+n).repeat(64),2L,java.math.BigDecimal.TWO);winners.incrementAndGet();}catch(Exception ignored){}});} gate.countDown();
  }
  assertThat(winners).hasValue(1);
  assertThat(jdbc.queryForObject("select count(*) from platform_metric_snapshots where platform_campaign_uuid=?",Integer.class,f.campaign())).isEqualTo(2);

  Fixture wrongCurrency=fixture("USD","Asia/Taipei");
  assertThatThrownBy(()->insert(wrongCurrency,1,Instant.now(),"a".repeat(64),1L,java.math.BigDecimal.ONE,"TWD","Asia/Taipei")).isInstanceOf(RuntimeException.class);
  Fixture wrongTimezone=fixture("TWD","UTC");
  assertThatThrownBy(()->insert(wrongTimezone,1,Instant.now(),"b".repeat(64),1L,java.math.BigDecimal.ONE,"TWD","Asia/Taipei")).isInstanceOf(RuntimeException.class);
  Fixture archived=fixture("TWD","Asia/Taipei");
  jdbc.update("update platform_accounts set lifecycle_status='ARCHIVED',archived_at=current_timestamp,updated_at=current_timestamp,version=1 where platform_account_uuid=?",archived.account());
  assertThatThrownBy(()->insert(archived,1,Instant.now(),"c".repeat(64),1L,java.math.BigDecimal.ONE)).isInstanceOf(RuntimeException.class);
 }

 @Test void baseWindowAttributionFreshnessAndFingerprintInvalidCasesExposeExactConstraintAndPreserveState(){
  Fixture f=fixture("TWD","Asia/Taipei"); Instant fetched=Instant.parse("2026-08-17T01:00:00Z");
  insert(f,1,fetched,"d".repeat(64),1L,java.math.BigDecimal.ONE);
  int baseline=count(f);
  for(String column:java.util.List.of("impressions","reach","clicks","conversions","spend","revenue")){
   String sql="insert into platform_metric_snapshots(metric_snapshot_uuid,platform_account_uuid,entity_type,platform_campaign_uuid,window_start,window_end,timezone,currency,"+column+",revision_number,fetched_at,freshness_status,source_fingerprint) values (?,?,'CAMPAIGN',?,?,?,?,?,-1,2,?,'FRESH',?)";
   assertSqlStateAndCount("23514",f,baseline,()->jdbc.update(sql,UUID.randomUUID(),f.account(),f.campaign(),Timestamp.from(f.start()),Timestamp.from(f.end()),"Asia/Taipei","TWD",Timestamp.from(fetched.plusSeconds(60)),fingerprint(column)));
  }
  assertRawRejected(f,baseline,f.start(),f.start(),7,1,"FRESH","e".repeat(64),"23514");
  assertRawRejected(f,baseline,f.end(),f.start(),7,1,"FRESH","f".repeat(64),"23514");
  assertRawRejected(f,baseline,f.start(),f.end(),6,1,"FRESH","0".repeat(64),"23514");
  assertRawRejected(f,baseline,f.start(),f.end(),7,0,"FRESH","1a".repeat(32),"23514");
  assertRawRejected(f,baseline,f.start(),f.end(),7,1,"STALE","2a".repeat(32),"23514");
  assertRawRejected(f,baseline,f.start(),f.end(),7,1,"FRESH","ABCDEF".repeat(10)+"ABCD","23514");
  assertRawRejected(f,baseline,f.start(),f.end(),7,1,"FRESH","d".repeat(64),"23505");
 }

 private UUID insert(Fixture f,int revision,Instant fetched,String fingerprint,Long impressions,java.math.BigDecimal spend){return insert(f,revision,fetched,fingerprint,impressions,spend,"TWD","Asia/Taipei");}
 private UUID insert(Fixture f,int revision,Instant fetched,String fingerprint,Long impressions,java.math.BigDecimal spend,String currency,String timezone){UUID id=UUID.randomUUID();jdbc.update("insert into platform_metric_snapshots(metric_snapshot_uuid,platform_account_uuid,entity_type,platform_campaign_uuid,window_start,window_end,timezone,currency,impressions,spend,revision_number,fetched_at,freshness_status,source_fingerprint) values (?,?, 'CAMPAIGN',?,?,?,?,?,?,?,?,?,'FRESH',?)",id,f.account(),f.campaign(),Timestamp.from(f.start()),Timestamp.from(f.end()),timezone,currency,impressions,spend,revision,Timestamp.from(fetched),fingerprint);return id;}
 private void assertRawRejected(Fixture f,int baseline,Instant start,Instant end,int clickDays,int viewDays,String freshness,String fingerprint,String state){assertSqlStateAndCount(state,f,baseline,()->jdbc.update("insert into platform_metric_snapshots(metric_snapshot_uuid,platform_account_uuid,entity_type,platform_campaign_uuid,window_start,window_end,timezone,attribution_click_days,attribution_view_days,currency,impressions,revision_number,fetched_at,freshness_status,source_fingerprint) values (?,?,'CAMPAIGN',?,?,?,?,?,?,?,1,2,?,?,?)",UUID.randomUUID(),f.account(),f.campaign(),Timestamp.from(start),Timestamp.from(end),"Asia/Taipei",clickDays,viewDays,"TWD",Timestamp.from(Instant.parse("2026-08-17T02:00:00Z")),freshness,fingerprint));}
 private void assertSqlStateAndCount(String state,Fixture f,int baseline,org.assertj.core.api.ThrowableAssert.ThrowingCallable call){assertThatThrownBy(call).isInstanceOf(DataAccessException.class).satisfies(error->assertThat(sqlState(error)).isEqualTo(state));assertThat(count(f)).isEqualTo(baseline);}
 private int count(Fixture f){return jdbc.queryForObject("select count(*) from platform_metric_snapshots where platform_campaign_uuid=?",Integer.class,f.campaign());}
 private String sqlState(Throwable error){Throwable current=error;while(current!=null){if(current instanceof SQLException sql)return sql.getSQLState();current=current.getCause();}throw new AssertionError("missing SQLException",error);}
 private String fingerprint(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 private Fixture fixture(String currency,String timezone){UUID account=UUID.randomUUID(),plan=UUID.randomUUID(),campaign=UUID.randomUUID();jdbc.update("insert into platform_accounts(platform_account_uuid,provider_key,environment,account_reference,external_account_fingerprint,currency,timezone) values (?,'FAKE','TEST',?,?,?,?)",account,"metric-"+account,account.toString().replace("-","").repeat(2),currency,timezone);jdbc.update("insert into campaign_plans(campaign_uuid,campaign_name) values (?,'Metric')",plan);jdbc.update("insert into platform_campaigns(platform_campaign_uuid,campaign_uuid,platform_account_uuid,objective,account_timezone) values (?,?,?,'OUTCOME_SALES','Asia/Taipei')",campaign,plan,account);return new Fixture(account,campaign,Instant.parse("2026-08-16T00:00:00Z"),Instant.parse("2026-08-17T00:00:00Z"));}
 private record Fixture(UUID account,UUID campaign,Instant start,Instant end){}
}
