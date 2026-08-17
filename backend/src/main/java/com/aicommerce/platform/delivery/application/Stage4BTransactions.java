package com.aicommerce.platform.delivery.application;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import com.aicommerce.platform.audit.domain.*;
import com.aicommerce.platform.delivery.domain.*;
import com.aicommerce.platform.delivery.infrastructure.persistence.PlatformOperationJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("(local | test) & !production")
public class Stage4BTransactions {
    private static final ZoneId TAIPEI=ZoneId.of("Asia/Taipei");
    private final JdbcTemplate jdbc; private final PlatformOperationService operations;
    private final PlatformOperationJpaRepository operationRepository; private final ObjectMapper mapper;
    private final Environment environment;
    public Stage4BTransactions(JdbcTemplate jdbc,PlatformOperationService operations,
            PlatformOperationJpaRepository operationRepository,ObjectMapper mapper,Environment environment){
        this.jdbc=jdbc;this.operations=operations;this.operationRepository=operationRepository;this.mapper=mapper;this.environment=environment;
    }

    @Transactional(readOnly=true)
    public Stage4BViews.Preview previewCampaign(UUID request,UUID campaignUuid){
        var plan=plan(campaignUuid,false); validatePlan(plan,businessDate());
        return preview(request,PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN,null,null,plan.version,
                PlatformDesiredState.PAUSED,null,null,scheduleStart(plan),scheduleEnd(plan),"NONE",null,null,BigDecimal.ZERO);
    }

    @Transactional
    public Created confirmCampaign(UUID request,UUID campaignUuid,long expectedPlanVersion,String requestId){
        UUID account=account(); var replay=replay(account,request);
        if(replay.isPresent())return replay(replay.get(),PlatformOperationType.CREATE_CAMPAIGN,Map.of("campaignUuid",campaignUuid.toString()));
        var plan=plan(campaignUuid,true);
        if(plan.version!=expectedPlanVersion)throw error("PLATFORM_CAMPAIGN_PLAN_STALE",HttpStatus.PRECONDITION_FAILED);
        if(jdbc.queryForObject("SELECT count(*) FROM platform_campaigns WHERE campaign_uuid=?",Integer.class,campaignUuid)>0)throw error("PLATFORM_CAMPAIGN_ALREADY_MAPPED",HttpStatus.CONFLICT);
        UUID entity=UUID.randomUUID(),operation=UUID.randomUUID();
        var payload=base(PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN,entity);
        payload.put("platformCampaignUuid",entity);payload.put("campaignUuid",campaignUuid);
        payload.put("objective","OUTCOME_SALES");payload.put("desiredState","PAUSED");payload.put("accountTimezone","Asia/Taipei");
        payload.put("scheduleStart",scheduleStart(plan).toString());payload.put("scheduleEnd",scheduleEnd(plan).toString());
        insertBatch(operation,account,request,null,BigDecimal.ZERO);
        LocalDate anchored=batchDate(operation);validatePlan(plan,anchored);
        PlatformOperation stored=create(operation,account,request,entity,PlatformOperationType.CREATE_CAMPAIGN,PlatformEntityType.CAMPAIGN,payload,requestId);
        return new Created(stored,false);
    }

    @Transactional(readOnly=true)
    public Stage4BViews.Preview previewAdSet(UUID parent,UUID request,PlatformBudgetType type,BigDecimal amount){
        var campaign=campaign(parent,false);var plan=plan(campaign.campaignUuid,false);validatePlan(plan,businessDate());validateBudget(type,amount,plan);
        return preview(request,PlatformOperationType.CREATE_AD_SET,PlatformEntityType.AD_SET,null,campaign.version,plan.version,
                PlatformDesiredState.PAUSED,type,amount,campaign.scheduleStart,campaign.scheduleEnd,"INITIAL",null,amount,amount);
    }

    @Transactional
    public Created confirmAdSet(UUID parent,UUID request,PlatformBudgetType type,BigDecimal amount,long expectedPlanVersion,long parentVersion,String requestId){
        UUID account=account();var existing=replay(account,request);if(existing.isPresent())return replay(existing.get(),PlatformOperationType.CREATE_AD_SET,Map.of("platformCampaignUuid",parent.toString(),"budgetType",type.name(),"budgetAmount",plain(amount)),parentVersion);
        var campaign=campaign(parent,true);if(campaign.version!=parentVersion)throw error("PLATFORM_ENTITY_STALE",HttpStatus.PRECONDITION_FAILED);
        var plan=plan(campaign.campaignUuid,true);if(plan.version!=expectedPlanVersion)throw error("PLATFORM_CAMPAIGN_PLAN_STALE",HttpStatus.PRECONDITION_FAILED);
        validateBudget(type,amount,plan);UUID entity=UUID.randomUUID(),operation=UUID.randomUUID();
        var payload=base(PlatformOperationType.CREATE_AD_SET,PlatformEntityType.AD_SET,entity);
        payload.put("platformAdSetUuid",entity);payload.put("platformCampaignUuid",parent);payload.put("budgetType",type.name());payload.put("budgetAmount",amount);
        payload.put("currency","TWD");payload.put("accountTimezone","Asia/Taipei");payload.put("optimizationGoal","OFFSITE_CONVERSIONS");
        payload.put("targetingProfileKey","TW_BROAD_FEEDS_V1");payload.put("placementProfileKey","TW_BROAD_FEEDS_V1");payload.put("desiredState","PAUSED");
        payload.put("scheduleStart",campaign.scheduleStart.toString());payload.put("scheduleEnd",campaign.scheduleEnd.toString());
        UUID batch=insertBatch(operation,account,request,parentVersion,amount);LocalDate date=batchDate(operation);validatePlan(plan,date);
        Day day=reserveDay(account,date,amount);insertReservation(batch,operation,account,day.uuid,entity,"INITIAL",null,amount,amount);applyDay(account,date,amount);
        PlatformOperation stored=create(operation,account,request,entity,PlatformOperationType.CREATE_AD_SET,PlatformEntityType.AD_SET,payload,requestId);
        return new Created(stored,false);
    }

    @Transactional(readOnly=true)
    public Stage4BViews.Preview previewState(PlatformEntityType type,UUID entity,UUID request,PlatformDesiredState target){
        var current=entity(type,entity,false);validateState(current,target);
        PlatformOperationType op=target==PlatformDesiredState.PAUSED?PlatformOperationType.PAUSE:PlatformOperationType.RESUME;
        return preview(request,op,type,entity,current.version,null,target,null,null,current.scheduleStart,current.scheduleEnd,"NONE",null,null,BigDecimal.ZERO);
    }

    @Transactional
    public Created confirmState(PlatformEntityType type,UUID entity,UUID request,PlatformDesiredState target,long expectedVersion,String requestId){
        UUID account=account();var existing=replay(account,request);if(existing.isPresent())return replay(existing.get(),target==PlatformDesiredState.PAUSED?PlatformOperationType.PAUSE:PlatformOperationType.RESUME,Map.of("expectedEntityVersion",Long.toString(expectedVersion),"targetDesiredState",target.name()));
        var current=entity(type,entity,true);if(current.version!=expectedVersion)throw error("PLATFORM_ENTITY_STALE",HttpStatus.PRECONDITION_FAILED);validateState(current,target);
        PlatformOperationType op=target==PlatformDesiredState.PAUSED?PlatformOperationType.PAUSE:PlatformOperationType.RESUME;UUID operation=UUID.randomUUID();
        var payload=base(op,type,entity);payload.put("expectedEntityVersion",expectedVersion);payload.put("targetDesiredState",target.name());
        insertBatch(operation,account,request,expectedVersion,BigDecimal.ZERO);
        return new Created(create(operation,account,request,entity,op,type,payload,requestId),false);
    }

    @Transactional(readOnly=true)
    public Stage4BViews.Preview previewBudget(UUID entity,UUID request,BigDecimal next){
        var current=adSet(entity,false);validateBudget(current.budgetType,next,plan(current.campaignUuid,false));
        if(current.budget.compareTo(next)==0)throw error("PLATFORM_REQUEST_INVALID",HttpStatus.BAD_REQUEST);
        BigDecimal delta=next.subtract(current.budget).max(BigDecimal.ZERO);
        return preview(request,PlatformOperationType.UPDATE_BUDGET,PlatformEntityType.AD_SET,entity,current.version,null,current.desiredState,current.budgetType,next,
                current.scheduleStart,current.scheduleEnd,next.compareTo(current.budget)>0?"INCREASE":"DECREASE_NO_RELEASE",current.budget,next,delta);
    }

    @Transactional
    public Created confirmBudget(UUID entity,UUID request,BigDecimal next,long expectedVersion,String requestId){
        UUID account=account();var existing=replay(account,request);if(existing.isPresent())return replay(existing.get(),PlatformOperationType.UPDATE_BUDGET,Map.of("expectedEntityVersion",Long.toString(expectedVersion),"newBudgetAmount",plain(next)));
        var current=adSet(entity,true);if(current.version!=expectedVersion)throw error("PLATFORM_ENTITY_STALE",HttpStatus.PRECONDITION_FAILED);
        validateBudget(current.budgetType,next,plan(current.campaignUuid,true));if(current.budget.compareTo(next)==0)throw error("PLATFORM_REQUEST_INVALID",HttpStatus.BAD_REQUEST);
        BigDecimal delta=next.subtract(current.budget).max(BigDecimal.ZERO);String kind=delta.signum()>0?"INCREASE":"DECREASE_NO_RELEASE";UUID operation=UUID.randomUUID();
        var payload=base(PlatformOperationType.UPDATE_BUDGET,PlatformEntityType.AD_SET,entity);payload.put("platformAdSetUuid",entity);payload.put("expectedEntityVersion",expectedVersion);
        payload.put("budgetType",current.budgetType.name());payload.put("currency","TWD");payload.put("previousBudgetAmount",current.budget);payload.put("newBudgetAmount",next);
        UUID batch=insertBatch(operation,account,request,expectedVersion,delta);LocalDate date=batchDate(operation);Day day=reserveDay(account,date,delta);
        insertReservation(batch,operation,account,day.uuid,entity,kind,current.budget,next,delta);applyDay(account,date,delta);
        PlatformOperation stored=create(operation,account,request,entity,PlatformOperationType.UPDATE_BUDGET,PlatformEntityType.AD_SET,payload,requestId);return new Created(stored,false);
    }

    private PlatformOperation create(UUID operation,UUID account,UUID request,UUID entity,PlatformOperationType type,PlatformEntityType entityType,Map<String,Object> payload,String requestId){
        try{return operations.create(new CreatePlatformOperationCommand(operation,account,type,entityType,entity,request,mapper.writeValueAsString(payload),3),
                new AuditOperationContext(operation,safeRequest(requestId),AuditActor.localAdmin(),AuditSource.API));}
        catch(Stage4BException e){throw e;}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException(e);}
    }
    private Optional<PlatformOperation> replay(UUID account,UUID request){return operationRepository.findByPlatformAccountUuidAndRequestedActorTypeAndRequestedActorIdAndClientRequestUuid(account,"LOCAL_ADMIN","local-admin",request);}
    private Created replay(PlatformOperation operation,PlatformOperationType expected,Map<String,String> fields){return replay(operation,expected,fields,null);}
    private Created replay(PlatformOperation operation,PlatformOperationType expected,Map<String,String> fields,Long expectedEntityVersion){
        try{
            var payload=mapper.readTree(operation.getRequestPayload());
            if(operation.getOperationType()!=expected)throw error("PLATFORM_IDEMPOTENCY_CONFLICT",HttpStatus.CONFLICT);
            for(var field:fields.entrySet())if(!field.getValue().equals(payload.path(field.getKey()).asText()))throw error("PLATFORM_IDEMPOTENCY_CONFLICT",HttpStatus.CONFLICT);
            if(jdbc.queryForObject("SELECT count(*) FROM platform_operation_batches WHERE operation_uuid=?",Integer.class,operation.getOperationUuid())!=1)throw error("PLATFORM_LEGACY_OPERATION_INERT",HttpStatus.CONFLICT);
            Long persistedVersion=jdbc.queryForObject("SELECT expected_entity_version FROM platform_operation_batches WHERE operation_uuid=?",Long.class,operation.getOperationUuid());
            if(expectedEntityVersion!=null&&!expectedEntityVersion.equals(persistedVersion))throw error("PLATFORM_IDEMPOTENCY_CONFLICT",HttpStatus.CONFLICT);
            return new Created(operation,true);
        }catch(Stage4BException e){throw e;}catch(Exception e){throw error("PLATFORM_IDEMPOTENCY_CONFLICT",HttpStatus.CONFLICT);}
    }
    private UUID insertBatch(UUID operation,UUID account,UUID request,Long expectedVersion,BigDecimal amount){UUID id=UUID.randomUUID();jdbc.update("""
      INSERT INTO platform_operation_batches(operation_batch_uuid,operation_uuid,platform_account_uuid,client_request_uuid,requested_actor_type,requested_actor_id,expected_entity_version,currency,business_date,reserved_amount,created_at,version)
      VALUES (?,?,?,?, 'LOCAL_ADMIN','local-admin',?,'TWD',CURRENT_DATE,?,CURRENT_TIMESTAMP,0)
      """,id,operation,account,request,expectedVersion,amount);return id;}
    private LocalDate batchDate(UUID operation){return jdbc.queryForObject("SELECT business_date FROM platform_operation_batches WHERE operation_uuid=?",LocalDate.class,operation);}
    private Day reserveDay(UUID account,LocalDate date,BigDecimal delta){UUID candidate=UUID.randomUUID();jdbc.update("""
      INSERT INTO platform_account_budget_days(platform_account_uuid,business_date,currency,account_budget_day_uuid,reserved_amount,ceiling_amount,created_at,updated_at,version)
      VALUES (?,?,'TWD',?,0,1000.000000,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0) ON CONFLICT DO NOTHING
      """,account,date,candidate);Day day=jdbc.queryForObject("SELECT account_budget_day_uuid,reserved_amount,version FROM platform_account_budget_days WHERE platform_account_uuid=? AND business_date=? AND currency='TWD' FOR UPDATE",(rs,n)->new Day(rs.getObject(1,UUID.class),rs.getBigDecimal(2),rs.getLong(3)),account,date);
        if(day.amount.add(delta).compareTo(new BigDecimal("1000.000000"))>0)throw error("PLATFORM_BUDGET_CAP_EXCEEDED",HttpStatus.CONFLICT);
        return day;}
    private void applyDay(UUID account,LocalDate date,BigDecimal delta){if(delta.signum()>0)jdbc.update("UPDATE platform_account_budget_days SET reserved_amount=reserved_amount+?,updated_at=statement_timestamp(),version=version+1 WHERE platform_account_uuid=? AND business_date=? AND currency='TWD'",delta,account,date);}
    private void insertReservation(UUID batch,UUID operation,UUID account,UUID day,UUID entity,String kind,BigDecimal previous,BigDecimal next,BigDecimal reserved){jdbc.update("""
      INSERT INTO platform_budget_reservations(budget_reservation_uuid,operation_batch_uuid,operation_uuid,platform_account_uuid,account_budget_day_uuid,platform_ad_set_uuid,reservation_kind,previous_budget_amount,new_budget_amount,reserved_amount,currency,business_date,created_at)
      VALUES (?,?,?,?,?,?,?,?,?,?,'TWD',CURRENT_DATE,CURRENT_TIMESTAMP)
      """,UUID.randomUUID(),batch,operation,account,day,entity,kind,previous,next,reserved);}

    UUID account(){boolean test=Arrays.asList(environment.getActiveProfiles()).contains("test");UUID id=test?Stage4BAccountInitializer.TEST_UUID:Stage4BAccountInitializer.LOCAL_UUID;String reference=test?"stage4b-test":"stage4b-local";String expectedEnvironment=test?"TEST":"LOCAL";String fingerprint=test?"9276789d487fcd7791df964134173a1b815a4f9fc1d507457ee6dbcca187c8c2":"4f1eee978e5efed2d42ac62995484b642870cda74dea26cd2d2f63653d51cf36";List<UUID> candidates=jdbc.query("SELECT platform_account_uuid FROM platform_accounts WHERE provider_key='FAKE' AND account_reference=?",(rs,n)->rs.getObject(1,UUID.class),reference);if(candidates.size()!=1||!id.equals(candidates.getFirst()))throw error("PLATFORM_ACCOUNT_CONFIGURATION_INVALID",HttpStatus.SERVICE_UNAVAILABLE);Integer exact=jdbc.queryForObject("SELECT count(*) FROM platform_accounts WHERE platform_account_uuid=? AND provider_key='FAKE' AND environment=? AND account_reference=? AND external_account_fingerprint=? AND lifecycle_status='ACTIVE' AND archived_at IS NULL AND currency='TWD' AND timezone='Asia/Taipei'",Integer.class,id,expectedEnvironment,reference,fingerprint);if(exact==null||exact!=1)throw error("PLATFORM_ACCOUNT_CONFIGURATION_INVALID",HttpStatus.SERVICE_UNAVAILABLE);return id;}
    private Plan plan(UUID id,boolean lock){List<Plan> rows=jdbc.query("SELECT campaign_uuid,start_date,end_date,objective,platform,budget_daily,budget_total,currency,lifecycle_status,archived_at,version FROM campaign_plans WHERE campaign_uuid=?"+(lock?" FOR UPDATE":""),(rs,n)->new Plan(rs.getObject(1,UUID.class),rs.getObject(2,LocalDate.class),rs.getObject(3,LocalDate.class),rs.getString(4),rs.getString(5),rs.getBigDecimal(6),rs.getBigDecimal(7),rs.getString(8),rs.getString(9),rs.getObject(10),rs.getLong(11)),id);if(rows.size()!=1)throw error("PLATFORM_RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND);return rows.getFirst();}
    private void validatePlan(Plan p,LocalDate date){if(!"ACTIVE".equals(p.lifecycle)||p.archived!=null||!"META".equals(p.platform)||!"OUTCOME_SALES".equals(p.objective)||!"TWD".equals(p.currency)||p.start==null||p.end==null||p.start.isBefore(date)||p.end.isBefore(p.start)||(nonPositive(p.daily)&&nonPositive(p.total)))throw error("PLATFORM_CAMPAIGN_PLAN_INELIGIBLE",HttpStatus.CONFLICT);}
    private void validateBudget(PlatformBudgetType type,BigDecimal amount,Plan p){amount=money(amount);BigDecimal max=type==PlatformBudgetType.DAILY?new BigDecimal("100"):new BigDecimal("300");BigDecimal planMax=type==PlatformBudgetType.DAILY?p.daily:p.total;if(planMax==null||amount.signum()<=0||amount.compareTo(max)>0||amount.compareTo(planMax)>0)throw error("PLATFORM_POLICY_REJECTED",HttpStatus.CONFLICT);}
    private CampaignRow campaign(UUID id,boolean lock){UUID account=account();List<CampaignRow> rows=jdbc.query("SELECT platform_campaign_uuid,campaign_uuid,platform_account_uuid,desired_state,observed_state,schedule_start,schedule_end,external_id,version FROM platform_campaigns WHERE platform_campaign_uuid=? AND platform_account_uuid=?"+(lock?" FOR UPDATE":""),(rs,n)->new CampaignRow(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),PlatformDesiredState.valueOf(rs.getString(4)),rs.getString(5),rs.getTimestamp(6).toInstant(),rs.getTimestamp(7).toInstant(),rs.getString(8),rs.getLong(9)),id,account);if(rows.size()!=1)throw error("PLATFORM_RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND);return rows.getFirst();}
    private EntityRow entity(PlatformEntityType type,UUID id,boolean lock){if(type==PlatformEntityType.CAMPAIGN){var c=campaign(id,lock);return new EntityRow(c.id,c.desired,c.externalId,c.version,c.scheduleStart,c.scheduleEnd);}var a=adSet(id,lock);return new EntityRow(a.id,a.desiredState,a.externalId,a.version,a.scheduleStart,a.scheduleEnd);}
    private AdSetRow adSet(UUID id,boolean lock){UUID account=account();List<AdSetRow> rows=jdbc.query("SELECT s.platform_ad_set_uuid,s.platform_campaign_uuid,s.platform_account_uuid,s.budget_type,s.budget_amount,s.desired_state,s.external_id,s.version,s.schedule_start,s.schedule_end,c.campaign_uuid FROM platform_ad_sets s JOIN platform_campaigns c ON c.platform_campaign_uuid=s.platform_campaign_uuid AND c.platform_account_uuid=s.platform_account_uuid WHERE s.platform_ad_set_uuid=? AND s.platform_account_uuid=?"+(lock?" FOR UPDATE OF s":""),(rs,n)->new AdSetRow(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),PlatformBudgetType.valueOf(rs.getString(4)),rs.getBigDecimal(5),PlatformDesiredState.valueOf(rs.getString(6)),rs.getString(7),rs.getLong(8),rs.getTimestamp(9).toInstant(),rs.getTimestamp(10).toInstant(),rs.getObject(11,UUID.class)),id,account);if(rows.size()!=1)throw error("PLATFORM_RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND);return rows.getFirst();}
    private void validateState(EntityRow row,PlatformDesiredState target){if(row.externalId==null||row.desired==target||(target!=PlatformDesiredState.PAUSED&&target!=PlatformDesiredState.ACTIVE))throw error("PLATFORM_INVALID_OPERATION_STATE",HttpStatus.CONFLICT);}
    private Stage4BViews.Preview preview(UUID req,PlatformOperationType op,PlatformEntityType type,UUID entity,Long version,Long planVersion,PlatformDesiredState state,PlatformBudgetType budgetType,BigDecimal amount,Instant start,Instant end,String kind,BigDecimal previous,BigDecimal next,BigDecimal delta){LocalDate date=businessDate();BigDecimal before=accountDay(date),after=before.add(delta);List<String> warnings=new java.util.ArrayList<>();if("DECREASE_NO_RELEASE".equals(kind))warnings.add("CAPACITY_NOT_RELEASED");warnings.add("CONFIRMATION_REVALIDATES");warnings.add("FAKE_ONLY_NO_REAL_DELIVERY");return new Stage4BViews.Preview(req,op,type,Optional.ofNullable(entity),Optional.ofNullable(version),Optional.ofNullable(planVersion),state,Optional.ofNullable(budgetType),Optional.ofNullable(amount).map(Stage4BTransactions::plain),Optional.ofNullable(start),Optional.ofNullable(end),policy(budgetType),new Stage4BViews.Reservation(kind,Optional.ofNullable(previous).map(Stage4BTransactions::plain),Optional.ofNullable(next).map(Stage4BTransactions::plain),plain(delta),date,plain(delta),plain(before),plain(after),plain(new BigDecimal("1000").subtract(after))),List.copyOf(warnings),after.compareTo(new BigDecimal("1000"))<=0);}
    private Stage4BViews.Policy policy(PlatformBudgetType type){return new Stage4BViews.Policy("TWD","Asia/Taipei","OUTCOME_SALES","OFFSITE_CONVERSIONS","TW_BROAD_FEEDS_V1","TW_BROAD_FEEDS_V1",Optional.ofNullable(type).map(t->t==PlatformBudgetType.DAILY?"100":"300"),"300","1000");}
    private LocalDate businessDate(){return jdbc.queryForObject("SELECT platform_taipei_business_date(statement_timestamp())",LocalDate.class);}
    private BigDecimal accountDay(LocalDate date){UUID account=account();BigDecimal value=jdbc.queryForObject("SELECT COALESCE(MAX(reserved_amount),0) FROM platform_account_budget_days WHERE platform_account_uuid=? AND business_date=? AND currency='TWD'",BigDecimal.class,account,date);return value==null?BigDecimal.ZERO:value;}
    private static LinkedHashMap<String,Object> base(PlatformOperationType op,PlatformEntityType type,UUID entity){var p=new LinkedHashMap<String,Object>();p.put("schemaVersion",1);p.put("operationType",op.name());p.put("entityType",type.name());p.put("entityUuid",entity);return p;}
    private static Instant scheduleStart(Plan p){return p.start.atStartOfDay(TAIPEI).toInstant();}private static Instant scheduleEnd(Plan p){return p.end.plusDays(1).atStartOfDay(TAIPEI).toInstant();}
    public static BigDecimal money(BigDecimal v){if(v==null||v.signum()<=0||v.scale()>6)throw error("PLATFORM_REQUEST_INVALID",HttpStatus.BAD_REQUEST);return v.stripTrailingZeros().scale()<0?v.setScale(0):v.stripTrailingZeros();}
    private static boolean nonPositive(BigDecimal v){return v==null||v.signum()<=0;}private static String plain(BigDecimal v){return v.stripTrailingZeros().toPlainString();}
    private static String safeRequest(String value){return value!=null&&value.matches("[A-Za-z0-9._:-]{1,128}")?value:UUID.randomUUID().toString();}
    private static Stage4BException error(String code,HttpStatus status){return new Stage4BException(code,status);}
    public record Created(PlatformOperation operation,boolean replay){}
    private record Plan(UUID id,LocalDate start,LocalDate end,String objective,String platform,BigDecimal daily,BigDecimal total,String currency,String lifecycle,Object archived,long version){}
    private record CampaignRow(UUID id,UUID campaignUuid,UUID account,PlatformDesiredState desired,String observed,Instant scheduleStart,Instant scheduleEnd,String externalId,long version){}
    private record EntityRow(UUID id,PlatformDesiredState desired,String externalId,long version,Instant scheduleStart,Instant scheduleEnd){}
    private record AdSetRow(UUID id,UUID platformCampaignUuid,UUID account,PlatformBudgetType budgetType,BigDecimal budget,PlatformDesiredState desiredState,String externalId,long version,Instant scheduleStart,Instant scheduleEnd,UUID campaignUuid){}
    private record Day(UUID uuid,BigDecimal amount,long version){}
}
