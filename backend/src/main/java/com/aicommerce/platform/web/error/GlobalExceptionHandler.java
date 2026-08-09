package com.aicommerce.platform.web.error;

import com.aicommerce.platform.asset.application.*;
import com.aicommerce.platform.campaign.application.*;
import com.aicommerce.platform.connector.sheets.application.SheetImportNotFoundException;
import com.aicommerce.platform.connector.sheets.application.SheetImportPreconditionFailedException;
import com.aicommerce.platform.connector.sheets.application.SheetImportStateConflictException;
import com.aicommerce.platform.connector.sheets.application.SheetImportValidationException;
import com.aicommerce.platform.connector.sheets.application.SheetProviderException;
import com.aicommerce.platform.creativeplan.application.CreativePlanArchivedException;
import com.aicommerce.platform.creativeplan.application.CreativePlanNotFoundException;
import com.aicommerce.platform.creativeplan.application.CreativePlanPreconditionFailedException;
import com.aicommerce.platform.creativeplan.application.CreativePlanValidationException;
import com.aicommerce.platform.knowledge.application.KnowledgeArchivedException;
import com.aicommerce.platform.knowledge.application.KnowledgeNotFoundException;
import com.aicommerce.platform.knowledge.application.KnowledgePreconditionFailedException;
import com.aicommerce.platform.knowledge.application.KnowledgeValidationException;
import com.aicommerce.platform.product.application.AuditActorUnavailableException;
import com.aicommerce.platform.product.application.ProductArchivedException;
import com.aicommerce.platform.product.application.ProductNotFoundException;
import com.aicommerce.platform.product.application.ProductPreconditionFailedException;
import com.aicommerce.platform.product.application.ProductValidationException;
import com.aicommerce.platform.product.web.InvalidIfMatchException;
import com.aicommerce.platform.product.web.InvalidMergePatchException;
import com.aicommerce.platform.product.web.PreconditionRequiredException;
import com.aicommerce.platform.quality.application.QualityNotFoundException;
import com.aicommerce.platform.quality.application.QualityPreconditionFailedException;
import com.aicommerce.platform.quality.application.QualityValidationException;
import com.aicommerce.platform.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(SheetImportValidationException.class)
  ResponseEntity<ApiError> handleSheetImportValidation(SheetImportValidationException exception,
      HttpServletRequest request) {
    return ResponseEntity.badRequest().body(error(exception.getCode(), "Request validation failed", request,
        List.of(new FieldErrorDetail(exception.getField(), exception.getMessage()))));
  }

  @ExceptionHandler(SheetProviderException.class)
  ResponseEntity<ApiError> handleSheetProvider(SheetProviderException exception, HttpServletRequest request) {
    HttpStatus status = switch (exception.getCode()) {
      case "GOOGLE_PERMISSION_DENIED" -> HttpStatus.FORBIDDEN;
      case "GOOGLE_RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
      default -> HttpStatus.SERVICE_UNAVAILABLE;
    };
    return ResponseEntity.status(status).body(error(exception.getCode(), exception.getMessage(), request, null));
  }

  @ExceptionHandler(SheetImportNotFoundException.class)
  ResponseEntity<ApiError> handleSheetImportNotFound(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(error("IMPORT_JOB_NOT_FOUND", "Sheet import job not found", request, null));
  }

  @ExceptionHandler(SheetImportStateConflictException.class)
  ResponseEntity<ApiError> handleSheetImportStateConflict(SheetImportStateConflictException exception,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(error("IMPORT_JOB_STATE_CONFLICT", exception.getMessage(), request, null));
  }

  @ExceptionHandler(SheetImportPreconditionFailedException.class)
  ResponseEntity<ApiError> handleSheetImportPrecondition(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
        .body(error("IMPORT_JOB_STALE", "Sheet import job version does not match If-Match", request, null));
  }

  @ExceptionHandler(QualityValidationException.class)
  ResponseEntity<ApiError> handleQualityValidation(QualityValidationException exception,
      HttpServletRequest request) {
    return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", "Request validation failed", request,
        List.of(new FieldErrorDetail(exception.getField(), exception.getMessage()))));
  }

  @ExceptionHandler(QualityNotFoundException.class)
  ResponseEntity<ApiError> handleQualityNotFound(HttpServletRequest request) {
    return ResponseEntity.status(404).body(error("QUALITY_NOT_FOUND", "Quality projection not found", request, null));
  }

  @ExceptionHandler(QualityPreconditionFailedException.class)
  ResponseEntity<ApiError> handleQualityPrecondition(HttpServletRequest request) {
    return ResponseEntity.status(412).body(error("PRECONDITION_FAILED",
        "Quality version does not match If-Match", request, null));
  }

  @ExceptionHandler(AssetValidationException.class)
  ResponseEntity<ApiError> handleAssetValidation(AssetValidationException e, HttpServletRequest request) {
    return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", "Request validation failed", request,
        List.of(new FieldErrorDetail(e.getField(), e.getMessage()))));
  }

  @ExceptionHandler(AssetNotFoundException.class)
  ResponseEntity<ApiError> handleAssetNotFound(HttpServletRequest request) {
    return ResponseEntity.status(404).body(error("ASSET_NOT_FOUND", "Asset not found", request, null));
  }

  @ExceptionHandler(AssetArchivedException.class)
  ResponseEntity<ApiError> handleAssetArchived(HttpServletRequest request) {
    return ResponseEntity.status(409).body(error("RESOURCE_ARCHIVED", "Archived asset cannot be modified", request, null));
  }

  @ExceptionHandler(AssetPreconditionFailedException.class)
  ResponseEntity<ApiError> handleAssetPrecondition(HttpServletRequest request) {
    return ResponseEntity.status(412).body(error("PRECONDITION_FAILED", "Asset version does not match If-Match", request, null));
  }

  @ExceptionHandler(AssetRelationshipConflictException.class)
  ResponseEntity<ApiError> handleAssetRelationshipConflict(HttpServletRequest request) {
    return ResponseEntity.status(409).body(error("RELATIONSHIP_CONFLICT", "Asset relationship is not active or valid", request, null));
  }

  @ExceptionHandler(CampaignValidationException.class)
  ResponseEntity<ApiError> handleCampaignValidation(
      CampaignValidationException e, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            error(
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                List.of(new FieldErrorDetail(e.getField(), e.getMessage()))));
  }

  @ExceptionHandler(CampaignNotFoundException.class)
  ResponseEntity<ApiError> handleCampaignNotFound(HttpServletRequest request) {
    return ResponseEntity.status(404)
        .body(error("CAMPAIGN_NOT_FOUND", "Campaign not found", request, null));
  }

  @ExceptionHandler(CampaignProductNotFoundException.class)
  ResponseEntity<ApiError> handleCampaignProductNotFound(HttpServletRequest request) {
    return ResponseEntity.status(404)
        .body(error("CAMPAIGN_PRODUCT_NOT_FOUND", "Campaign product not found", request, null));
  }

  @ExceptionHandler({CampaignArchivedException.class, CampaignProductArchivedException.class})
  ResponseEntity<ApiError> handleCampaignArchived(HttpServletRequest request) {
    return ResponseEntity.status(409)
        .body(error("RESOURCE_ARCHIVED", "Archived resource cannot be modified", request, null));
  }

  @ExceptionHandler(CampaignPreconditionFailedException.class)
  ResponseEntity<ApiError> handleCampaignPrecondition(HttpServletRequest request) {
    return ResponseEntity.status(412)
        .body(
            error(
                "PRECONDITION_FAILED",
                "Campaign resource version does not match If-Match",
                request,
                null));
  }

  @ExceptionHandler(RelationshipConflictException.class)
  ResponseEntity<ApiError> handleRelationshipConflict(HttpServletRequest request) {
    return ResponseEntity.status(409)
        .body(
            error(
                "RELATIONSHIP_CONFLICT",
                "Campaign product relationship already exists",
                request,
                null));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<FieldErrorDetail> fieldErrors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
            .toList();
    ApiError error = error("VALIDATION_ERROR", "Request validation failed", request, fieldErrors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(ProductValidationException.class)
  ResponseEntity<ApiError> handleProductValidation(
      ProductValidationException exception, HttpServletRequest request) {
    ApiError error =
        error(
            "VALIDATION_ERROR",
            "Request validation failed",
            request,
            List.of(new FieldErrorDetail(exception.getField(), exception.getMessage())));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
  }

  @ExceptionHandler(CreativePlanValidationException.class)
  ResponseEntity<ApiError> handleCreativePlanValidation(
      CreativePlanValidationException exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            error(
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                List.of(new FieldErrorDetail(exception.getField(), exception.getMessage()))));
  }

  @ExceptionHandler(KnowledgeValidationException.class)
  ResponseEntity<ApiError> handleKnowledgeValidation(
      KnowledgeValidationException exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            error(
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                List.of(new FieldErrorDetail(exception.getField(), exception.getMessage()))));
  }

  @ExceptionHandler(CreativePlanNotFoundException.class)
  ResponseEntity<ApiError> handleCreativePlanNotFound(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(error("CREATIVE_PLAN_NOT_FOUND", "Creative plan not found", request, null));
  }

  @ExceptionHandler(CreativePlanArchivedException.class)
  ResponseEntity<ApiError> handleCreativePlanArchived(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            error("RESOURCE_ARCHIVED", "Archived creative plan cannot be modified", request, null));
  }

  @ExceptionHandler(CreativePlanPreconditionFailedException.class)
  ResponseEntity<ApiError> handleCreativePlanPreconditionFailed(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
        .body(
            error(
                "PRECONDITION_FAILED",
                "Creative plan version does not match If-Match",
                request,
                null));
  }

  @ExceptionHandler(KnowledgeNotFoundException.class)
  ResponseEntity<ApiError> handleKnowledgeNotFound(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(error("KNOWLEDGE_NOT_FOUND", "Knowledge not found", request, null));
  }

  @ExceptionHandler(KnowledgeArchivedException.class)
  ResponseEntity<ApiError> handleKnowledgeArchived(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(error("KNOWLEDGE_ARCHIVED", "Archived knowledge cannot be modified", request, null));
  }

  @ExceptionHandler(KnowledgePreconditionFailedException.class)
  ResponseEntity<ApiError> handleKnowledgePreconditionFailed(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
        .body(
            error(
                "PRECONDITION_FAILED", "Knowledge version does not match If-Match", request, null));
  }

  @ExceptionHandler(InvalidMergePatchException.class)
  ResponseEntity<ApiError> handleInvalidMergePatch(
      InvalidMergePatchException exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(error("INVALID_MERGE_PATCH", exception.getMessage(), request, null));
  }

  @ExceptionHandler(PreconditionRequiredException.class)
  ResponseEntity<ApiError> handlePreconditionRequired(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
        .body(error("PRECONDITION_REQUIRED", "If-Match is required", request, null));
  }

  @ExceptionHandler(InvalidIfMatchException.class)
  ResponseEntity<ApiError> handleInvalidIfMatch(HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            error(
                "INVALID_IF_MATCH", "If-Match must use the format W/\"<version>\"", request, null));
  }

  @ExceptionHandler(ProductPreconditionFailedException.class)
  ResponseEntity<ApiError> handlePreconditionFailed(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
        .body(
            error("PRECONDITION_FAILED", "Product version does not match If-Match", request, null));
  }

  @ExceptionHandler(ProductNotFoundException.class)
  ResponseEntity<ApiError> handleProductNotFound(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(error("PRODUCT_NOT_FOUND", "Product not found", request, null));
  }

  @ExceptionHandler(ProductArchivedException.class)
  ResponseEntity<ApiError> handleProductArchived(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(error("PRODUCT_ARCHIVED", "Archived product cannot be modified", request, null));
  }

  @ExceptionHandler(AuditActorUnavailableException.class)
  ResponseEntity<ApiError> handleAuditActorUnavailable(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            error(
                "AUDIT_ACTOR_UNAVAILABLE", "A trusted audit actor is unavailable", request, null));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiError> handleMalformedJson(HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(error("MALFORMED_JSON", "Request body is not valid JSON", request, null));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ApiError> handleUnsupportedMediaType(HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(error("UNSUPPORTED_MEDIA_TYPE", "Content-Type is not supported", request, null));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiError> handleTypeMismatch(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            error(
                "VALIDATION_ERROR",
                "Request validation failed",
                request,
                List.of(new FieldErrorDetail(exception.getName(), "has an invalid format"))));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
    log.error("Unhandled request failure", exception);
    ApiError error = error("INTERNAL_ERROR", "An unexpected error occurred", request, null);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> handleNotFound(HttpServletRequest request) {
    ApiError error = error("NOT_FOUND", "Resource not found", request, null);
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  private ApiError error(
      String code, String message, HttpServletRequest request, List<FieldErrorDetail> fieldErrors) {
    return new ApiError(
        code,
        message,
        (String) request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE),
        Instant.now(),
        request.getRequestURI(),
        fieldErrors);
  }
}
