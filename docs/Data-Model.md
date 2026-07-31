# Product Knowledge Center Data Model

## 建議 Google Spreadsheet Tabs

### 1. Product Master
- product_uuid
- product_id
- sku
- product_name
- brand
- category
- subcategory
- status
- short_description
- cost
- sale_price
- currency
- stock
- product_url
- drive_folder_id
- created_at
- updated_at

### 2. Product Knowledge
- knowledge_id
- product_uuid
- knowledge_type
- title
- content
- source
- status
- version
- updated_at

### 3. Creative Plan
- creative_plan_id
- product_uuid
- campaign_id（可空）
- version
- primary_audience
- secondary_audience
- pain_point
- core_benefit
- creative_angle
- emotional_direction
- brand_tone
- visual_style
- main_color
- character_setting
- cta
- status
- approved_by
- approved_at

### 4. Campaign Plan
- campaign_id
- campaign_name
- activity_type
- start_date
- end_date
- objective
- platform
- budget_daily
- budget_total
- promotion
- landing_page
- status

### 5. Campaign Product
- campaign_id
- product_uuid
- role
- priority
- budget_weight

### 6. Asset Management
- asset_id
- product_uuid
- creative_plan_id
- campaign_id
- asset_type
- provider
- purpose
- file_url
- drive_file_id
- version
- status
- review_result

### 7. Workflow Status
- workflow_id
- product_uuid
- stage
- status
- assigned_agent
- error_message
- retry_count
- updated_at

### 8. Quality Score
- product_uuid
- system_score
- ai_suggested_score
- manual_adjustment
- final_score
- blocking_reason
- approved_by

## 關聯原則

所有資料以 `product_uuid` 作為永久識別。SKU 與商品名稱可變更，不可作為底層主鍵。
