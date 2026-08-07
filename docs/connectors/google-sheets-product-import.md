# Google Sheets Product Import Mapping

Stage 02 uses Google Sheets as an explicit Import Connector. PostgreSQL remains the only System of Record. A preview is immutable and must be reviewed before execution; there is no polling, automatic write-back, or bidirectional synchronization.

## Canonical columns

The header row uses these exact lowercase snake-case names, in this order:

`product_uuid`, `product_id`, `sku`, `product_name`, `brand`, `category`, `subcategory`, `short_description`, `cost`, `sale_price`, `currency`, `stock`, `product_url`

- `product_uuid`, `product_id`, and `product_name` headers are required.
- New Products leave `product_uuid` and `product_id` blank; the system generates both identifiers.
- Updates match an existing Product by UUID first, then immutable Product ID. SKU and Product name are never identity keys.
- A supplied malformed or unknown UUID is an error and never falls back to Product ID.
- Optional blank cells explicitly clear those Product fields during an update. Product name cannot be blank.
- Money values use at most four decimal places, stock is a non-negative integer, and currency is a three-letter uppercase ISO code.
- A preview contains at most 1,000 data rows and 13 columns. Errors are reported per row.

Use [the canonical CSV template](templates/google-sheets-product-import-template.csv) to create a Sheet with the required header ordering.
