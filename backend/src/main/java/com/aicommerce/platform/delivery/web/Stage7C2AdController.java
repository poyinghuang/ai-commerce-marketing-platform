package com.aicommerce.platform.delivery.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.aicommerce.platform.common.web.ResourceEtag;
import com.aicommerce.platform.delivery.application.*;
import com.aicommerce.platform.delivery.domain.*;
import com.aicommerce.platform.web.RequestIdFilter;
import com.aicommerce.platform.web.error.ApiError;
import com.aicommerce.platform.web.error.FieldErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@Profile("(local | test) & !production")
@ConditionalOnExpression("'${platform.adapter:}' == 'fake' && '${platform.web.enabled:false}' == 'true' && '${platform.stage4b.enabled:false}' == 'true' && '${platform.stage4c.enabled:false}' == 'true' && '${platform.stage7.google.web.enabled:false}' == 'true'")
@RequestMapping("/api")
public class Stage7C2AdController {
    private final Stage4CService service;
    public Stage7C2AdController(Stage4CService service){this.service=service;}

    @PostMapping(path="/platforms/google/ad-sets/{adSetUuid}/ads/preview",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Stage4CViews.AdPreview previewCreate(@PathVariable UUID adSetUuid,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false)String ifMatch,@RequestBody Stage4CViews.AdCreateRequest body){
        createBody(body);return service.previewCreate(adSetUuid,body.clientRequestUuid(),body.productUuid(),body.assetUuid(),body.generationOutputUuid(),body.reviewDecisionUuid(),version(ifMatch));
    }
    @PostMapping(path="/platforms/google/ad-sets/{adSetUuid}/ads",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Stage4BViews.Operation> confirmCreate(@PathVariable UUID adSetUuid,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false)String ifMatch,@RequestBody Stage4CViews.AdCreateRequest body,HttpServletRequest request){
        createBody(body);return response(service.confirmCreate(adSetUuid,body.clientRequestUuid(),body.productUuid(),body.assetUuid(),body.generationOutputUuid(),body.reviewDecisionUuid(),version(ifMatch),requestId(request)));
    }
    @GetMapping("/platforms/google/ads/{adUuid}")
    public ResponseEntity<Stage4CViews.Ad> ad(@PathVariable UUID adUuid){
        var value=service.ad(adUuid);return ResponseEntity.ok().eTag(ResourceEtag.format(value.version())).body(value);
    }
    @PostMapping(path="/platforms/google/ads/{adUuid}/state/preview",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Stage4CViews.StatePreview previewState(@PathVariable UUID adUuid,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false)String ifMatch,@RequestBody Stage4CViews.AdStateRequest body){
        state(body);return service.previewState(adUuid,body.clientRequestUuid(),body.targetDesiredState(),version(ifMatch));
    }
    @PostMapping(path="/platforms/google/ads/{adUuid}/{action:pause|resume}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Stage4BViews.Operation> adState(@PathVariable UUID adUuid,@PathVariable String action,@RequestHeader(value=HttpHeaders.IF_MATCH,required=false)String ifMatch,@RequestBody Stage4CViews.AdStateRequest body,HttpServletRequest request){
        stateRoute(action,body);return response(service.confirmState(adUuid,body.clientRequestUuid(),body.targetDesiredState(),version(ifMatch),requestId(request)));
    }

    @ExceptionHandler(Stage4BException.class) ResponseEntity<ApiError> stageError(Stage4BException ex,HttpServletRequest request){return ResponseEntity.status(ex.status()).body(error(ex.code(),request,ex.field()));}
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<ApiError> malformedRequest(Exception ex,HttpServletRequest request){String field="body";if(ex instanceof HttpMessageNotReadableException unreadable&&unreadable.getCause() instanceof tools.jackson.databind.DatabindException binding&&!binding.getPath().isEmpty())field=binding.getPath().getFirst().getPropertyName();return ResponseEntity.badRequest().body(error("PLATFORM_REQUEST_INVALID",request,field));}
    @ExceptionHandler(PlatformOperationException.class) ResponseEntity<ApiError> operationError(PlatformOperationException ex,HttpServletRequest request){
        String uri=request.getRequestURI();boolean createOrResume=uri.contains("/ads")&&!uri.endsWith("/pause");
        String code=switch(ex.code()){
            case PLATFORM_OPERATION_NOT_FOUND->"PLATFORM_RESOURCE_NOT_FOUND";
            case PLATFORM_STALE_VERSION->uri.startsWith("/api/platforms/google/operations/")?"PLATFORM_OPERATION_STALE":"PLATFORM_ENTITY_STALE";
            case PLATFORM_ACCOUNT_INACTIVE,PLATFORM_ACCOUNT_ENVIRONMENT_MISMATCH,PLATFORM_PROVIDER_UNSUPPORTED->"PLATFORM_ACCOUNT_CONFIGURATION_INVALID";
            case PLATFORM_EVIDENCE_INVALID->createOrResume?"PLATFORM_AD_EVIDENCE_INVALID":"PLATFORM_EVIDENCE_INVALID";
            case PLATFORM_PARENT_STATE_INVALID->"PLATFORM_PARENT_STATE_INVALID";
            default->ex.code().name();
        };
        HttpStatus status=switch(code){
            case "PLATFORM_CONTRACT_INVALID","PLATFORM_REQUEST_INVALID"->HttpStatus.BAD_REQUEST;
            case "PLATFORM_RESOURCE_NOT_FOUND","PLATFORM_AD_NOT_FOUND"->HttpStatus.NOT_FOUND;
            case "PLATFORM_ENTITY_STALE","PLATFORM_OPERATION_STALE"->HttpStatus.PRECONDITION_FAILED;
            case "PLATFORM_IF_MATCH_REQUIRED"->HttpStatus.PRECONDITION_REQUIRED;
            case "PLATFORM_ADAPTER_UNAVAILABLE","PLATFORM_ACCOUNT_CONFIGURATION_INVALID"->HttpStatus.SERVICE_UNAVAILABLE;
            case "PLATFORM_PROVIDER_RETRYABLE"->HttpStatus.TOO_MANY_REQUESTS;
            default->HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(error(code,request));
    }
    @ExceptionHandler(DataAccessException.class) ResponseEntity<ApiError> databaseError(DataAccessException ex,HttpServletRequest request){
        Throwable cause=ex.getMostSpecificCause();String state=cause instanceof java.sql.SQLException sql?sql.getSQLState():null;
        String message=cause.getMessage()==null?"":cause.getMessage();
        if("40001".equals(state)||"40P01".equals(state))return ResponseEntity.status(HttpStatus.CONFLICT).body(error("PLATFORM_LEDGER_CONCURRENCY_CONFLICT",request));
        if("23514".equals(state)&&message.contains("ct_platform_ad_submit_claim_stale")){
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(error("PLATFORM_ENTITY_STALE",request));
        }
        if("23514".equals(state)&&message.contains("ct_platform_ad_submit_claim_parent_state")){
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error("PLATFORM_PARENT_STATE_INVALID",request));
        }
        if("23514".equals(state)&&message.contains("ct_platform_ad_submit_claim_evidence")){
            boolean createOrResume=request.getRequestURI().contains("/ads")&&!request.getRequestURI().endsWith("/pause");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error(createOrResume?"PLATFORM_AD_EVIDENCE_INVALID":"PLATFORM_EVIDENCE_INVALID",request));
        }
        throw ex;
    }

    private ResponseEntity<Stage4BViews.Operation> response(Stage4BViews.Confirmation c){
        var v=c.operation();HttpStatus status=c.replay()?HttpStatus.OK:HttpStatus.ACCEPTED;
        if(v.normalizedErrorCode().filter(code->code==PlatformStableErrorCode.PLATFORM_RATE_LIMITED||code==PlatformStableErrorCode.PLATFORM_TEMPORARILY_UNAVAILABLE).isPresent())status=HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status).eTag(ResourceEtag.format(v.version())).location(URI.create("/api/platforms/google/operations/"+v.operationUuid())).body(v);
    }
    private static void createBody(Stage4CViews.AdCreateRequest body){required(body,new String[]{"clientRequestUuid","productUuid","assetUuid","generationOutputUuid","reviewDecisionUuid"},body==null?null:body.clientRequestUuid(),body==null?null:body.productUuid(),body==null?null:body.assetUuid(),body==null?null:body.generationOutputUuid(),body==null?null:body.reviewDecisionUuid());}
    private static void state(Stage4CViews.AdStateRequest b){required(b,new String[]{"clientRequestUuid","targetDesiredState"},b==null?null:b.clientRequestUuid(),b==null?null:b.targetDesiredState());if(b.targetDesiredState()!=PlatformDesiredState.PAUSED&&b.targetDesiredState()!=PlatformDesiredState.ACTIVE)invalid("targetDesiredState");}
    private static void stateRoute(String action,Stage4CViews.AdStateRequest b){state(b);if(("pause".equals(action)&&b.targetDesiredState()!=PlatformDesiredState.PAUSED)||("resume".equals(action)&&b.targetDesiredState()!=PlatformDesiredState.ACTIVE))invalid("targetDesiredState");}
    private static long version(String value){if(value==null)throw new Stage4BException("PLATFORM_IF_MATCH_REQUIRED",HttpStatus.PRECONDITION_REQUIRED,"If-Match");try{return ResourceEtag.parse(value);}catch(Exception e){throw new Stage4BException("PLATFORM_REQUEST_INVALID",HttpStatus.BAD_REQUEST,"If-Match");}}
    private static void required(Object body,String[] fields,Object...values){if(body==null)invalid("body");for(int index=0;index<values.length;index++)if(values[index]==null)invalid(fields[index]);}
    private static void invalid(String field){throw new Stage4BException("PLATFORM_REQUEST_INVALID",HttpStatus.BAD_REQUEST,field);}
    private static String requestId(HttpServletRequest r){return (String)r.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);}
    private static ApiError error(String code,HttpServletRequest r){return error(code,r,null);}
    private static ApiError error(String code,HttpServletRequest r,String field){
        List<FieldErrorDetail> fields=field==null?List.of():List.of(new FieldErrorDetail(field,
                "If-Match".equals(field)?"Invalid If-Match":"body".equals(field)?"Invalid request body"
                        :"query".equals(field)?"Query parameters are not allowed":"path".equals(field)?"Invalid path":"Invalid value"));
        return new ApiError(code,message(code),requestId(r),Instant.now(),r.getRequestURI(),fields);
    }
    private static String message(String code){return switch(code){
        case "PLATFORM_REQUEST_INVALID"->"Platform request is invalid";
        case "PLATFORM_CONTRACT_INVALID"->"Platform contract is invalid";
        case "PLATFORM_RESOURCE_NOT_FOUND"->"Platform resource was not found";
        case "PLATFORM_AD_NOT_FOUND"->"Platform Ad was not found";
        case "PLATFORM_IF_MATCH_REQUIRED"->"If-Match is required";
        case "PLATFORM_ENTITY_STALE"->"The platform entity changed; reload and preview again";
        case "PLATFORM_OPERATION_STALE"->"The platform operation changed; reload and retry";
        case "PLATFORM_AD_EVIDENCE_INVALID"->"The approved Ad evidence is no longer eligible";
        case "PLATFORM_PARENT_STATE_INVALID"->"The parent platform state does not allow this action";
        case "PLATFORM_EVIDENCE_INVALID"->"Platform evidence is inconsistent";
        case "PLATFORM_POLICY_REJECTED"->"Platform policy rejected the request";
        case "PLATFORM_LEGACY_OPERATION_INERT"->"The legacy operation is read-only";
        case "PLATFORM_ACCOUNT_CONFIGURATION_INVALID"->"The local platform account is unavailable";
        case "PLATFORM_ADAPTER_UNAVAILABLE"->"The fake platform adapter is unavailable";
        case "PLATFORM_RETRY_NOT_DUE"->"The operation is not yet eligible for retry";
        case "PLATFORM_MAX_ATTEMPTS_EXCEEDED"->"The operation has no retry attempts remaining";
        case "PLATFORM_MAX_RECONCILIATIONS_EXCEEDED"->"The operation has no reconciliation attempts remaining";
        case "PLATFORM_IDEMPOTENCY_CONFLICT"->"The request conflicts with an existing operation";
        case "PLATFORM_PROVIDER_RETRYABLE"->"The fake provider result may be retried later";
        case "PLATFORM_LEDGER_CONCURRENCY_CONFLICT"->"The budget authorization changed concurrently";
        default->"The operation is not eligible for this action";
    };}
}
