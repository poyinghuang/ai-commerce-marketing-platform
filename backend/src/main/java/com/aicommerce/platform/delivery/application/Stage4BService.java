package com.aicommerce.platform.delivery.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

import com.aicommerce.platform.delivery.domain.*;
import com.aicommerce.platform.delivery.infrastructure.persistence.PlatformOperationJpaRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service @Profile("(local | test) & !production")
public class Stage4BService {
    private final Stage4BTransactions tx;private final PlatformOperationService operationService;
    private final PlatformOperationJpaRepository operations;private final JdbcTemplate jdbc;private final Clock clock;
    private final Stage4CSupport stage4c;
    public Stage4BService(Stage4BTransactions tx,PlatformOperationService operationService,PlatformOperationJpaRepository operations,JdbcTemplate jdbc,Clock clock,Stage4CSupport stage4c){this.tx=tx;this.operationService=operationService;this.operations=operations;this.jdbc=jdbc;this.clock=clock;this.stage4c=stage4c;}
    public Stage4BViews.Preview previewCampaign(UUID request,UUID campaign){return tx.previewCampaign(request,campaign);}
    public Stage4BViews.Confirmation confirmCampaign(UUID request,UUID campaign,long planVersion,String requestId){return dispatch(tx.confirmCampaign(request,campaign,planVersion,requestId));}
    public Stage4BViews.Preview previewAdSet(UUID parent,UUID request,PlatformBudgetType type,String amount){return tx.previewAdSet(parent,request,type,money(amount));}
    public Stage4BViews.Confirmation confirmAdSet(UUID parent,UUID request,PlatformBudgetType type,String amount,long planVersion,long parentVersion,String requestId){return dispatch(tx.confirmAdSet(parent,request,type,money(amount),planVersion,parentVersion,requestId));}
    public Stage4BViews.Preview previewState(PlatformEntityType type,UUID id,UUID request,PlatformDesiredState target){return tx.previewState(type,id,request,target);}
    public Stage4BViews.Confirmation confirmState(PlatformEntityType type,UUID id,UUID request,PlatformDesiredState target,long version,String requestId){return dispatch(tx.confirmState(type,id,request,target,version,requestId));}
    public Stage4BViews.Preview previewBudget(UUID id,UUID request,String amount){return tx.previewBudget(id,request,money(amount));}
    public Stage4BViews.Confirmation confirmBudget(UUID id,UUID request,String amount,long version,String requestId){return dispatch(tx.confirmBudget(id,request,money(amount),version,requestId));}
    public Stage4BViews.Operation operation(UUID id){UUID account=tx.account();PlatformOperation value=operations.findById(id).filter(o->account.equals(o.getPlatformAccountUuid())).orElseThrow(()->new Stage4BException("PLATFORM_RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND));return view(value);}
    public Stage4BViews.Confirmation retry(UUID id,long version){scopedOperation(id);return new Stage4BViews.Confirmation(from(operationService.retry(id,version,java.time.Instant.now(clock))),false);}
    public Stage4BViews.Confirmation reconcile(UUID id,long version){scopedOperation(id);return new Stage4BViews.Confirmation(from(operationService.reconcile(id,version)),false);}
    public Stage4BViews.Campaign campaign(UUID id){List<Stage4BViews.Campaign> rows=jdbc.query("""
      SELECT p.platform_campaign_uuid,p.campaign_uuid,c.version,p.objective,p.account_timezone,p.desired_state,p.observed_state,
             p.schedule_start,p.schedule_end,p.external_id,p.version
      FROM platform_campaigns p JOIN campaign_plans c ON c.campaign_uuid=p.campaign_uuid WHERE p.platform_campaign_uuid=? AND p.platform_account_uuid=?
      """,(rs,n)->new Stage4BViews.Campaign(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getLong(3),rs.getString(4),rs.getString(5),PlatformDesiredState.valueOf(rs.getString(6)),Optional.ofNullable(rs.getString(7)).map(PlatformObservedState::valueOf),Optional.ofNullable(rs.getTimestamp(8)).map(v->v.toInstant()),Optional.ofNullable(rs.getTimestamp(9)).map(v->v.toInstant()),Optional.ofNullable(rs.getString(10)).map(Stage4BService::hash),rs.getLong(11)),id,tx.account());if(rows.size()!=1)throw new Stage4BException("PLATFORM_RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND);return rows.getFirst();}
    public Stage4BViews.AdSet adSet(UUID id){List<Stage4BViews.AdSet> rows=jdbc.query("""
      SELECT platform_ad_set_uuid,platform_campaign_uuid,desired_state,observed_state,budget_type,budget_amount,currency,
             account_timezone,optimization_goal,targeting_profile_key,placement_profile_key,schedule_start,schedule_end,external_id,version
      FROM platform_ad_sets WHERE platform_ad_set_uuid=? AND platform_account_uuid=?
      """,(rs,n)->new Stage4BViews.AdSet(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),PlatformDesiredState.valueOf(rs.getString(3)),Optional.ofNullable(rs.getString(4)).map(PlatformObservedState::valueOf),PlatformBudgetType.valueOf(rs.getString(5)),rs.getBigDecimal(6).stripTrailingZeros().toPlainString(),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),Optional.ofNullable(rs.getTimestamp(12)).map(v->v.toInstant()),Optional.ofNullable(rs.getTimestamp(13)).map(v->v.toInstant()),Optional.ofNullable(rs.getString(14)).map(Stage4BService::hash),rs.getLong(15)),id,tx.account());if(rows.size()!=1)throw new Stage4BException("PLATFORM_RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND);return rows.getFirst();}
    private Stage4BViews.Confirmation dispatch(Stage4BTransactions.Created created){if(created.replay())return new Stage4BViews.Confirmation(view(created.operation()),true);return new Stage4BViews.Confirmation(from(operationService.submit(created.operation().getOperationUuid(),created.operation().getVersion(),null)),false);}
    private PlatformOperation scopedOperation(UUID id){UUID account=tx.account();PlatformOperation operation=operations.findById(id).filter(o->account.equals(o.getPlatformAccountUuid())).orElseThrow(()->new Stage4BException("PLATFORM_RESOURCE_NOT_FOUND",HttpStatus.NOT_FOUND));Integer count=jdbc.queryForObject("SELECT count(*) FROM platform_operation_batches WHERE operation_uuid=? AND platform_account_uuid=?",Integer.class,id,account);if(count!=null&&count==1)return operation;if(stage4c.applicationOwned(operation,account))return operation;throw new Stage4BException("PLATFORM_LEGACY_OPERATION_INERT",HttpStatus.CONFLICT);}
    private static java.math.BigDecimal money(String value){if(value==null||!value.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]*[1-9])?"))throw new Stage4BException("PLATFORM_REQUEST_INVALID",HttpStatus.BAD_REQUEST);try{return Stage4BTransactions.money(new java.math.BigDecimal(value));}catch(NumberFormatException e){throw new Stage4BException("PLATFORM_REQUEST_INVALID",HttpStatus.BAD_REQUEST);}}
    private static Stage4BViews.Operation from(PlatformOperationView o){return new Stage4BViews.Operation(o.operationUuid(),o.operationType(),o.entityType(),o.entityUuid(),o.status(),o.attemptCount(),o.reconciliationCount(),o.maxAttempts(),o.normalizedErrorCode(),o.nextAttemptAt(),o.completedAt(),o.createdAt(),o.updatedAt(),o.version());}
    private static Stage4BViews.Operation view(PlatformOperation o){return new Stage4BViews.Operation(o.getOperationUuid(),o.getOperationType(),o.getEntityType(),o.getEntityUuid(),o.getStatus(),o.getAttemptCount(),o.getReconciliationCount(),o.getMaxAttempts(),Optional.ofNullable(o.getNormalizedErrorCode()).map(PlatformStableErrorCode::valueOf),Optional.ofNullable(o.getNextAttemptAt()),Optional.ofNullable(o.getCompletedAt()),o.getCreatedAt(),o.getUpdatedAt(),o.getVersion());}
    private static String hash(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
