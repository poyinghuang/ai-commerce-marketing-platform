"use client";

import { FormEvent, useState } from "react";
import type { ProductInput } from "@/lib/products";

export const emptyProductInput: ProductInput = {
  sku: "",
  productName: "",
  brand: "",
  category: "",
  subcategory: "",
  shortDescription: "",
  cost: "",
  salePrice: "",
  currency: "",
  stock: "",
  productUrl: "",
};

type Props = {
  initialValue?: ProductInput;
  submitLabel: string;
  disabled?: boolean;
  onSubmit: (value: ProductInput) => Promise<void>;
};

export function ProductForm({ initialValue = emptyProductInput, submitLabel, disabled, onSubmit }: Props) {
  const [value, setValue] = useState<ProductInput>(initialValue);

  function update(field: keyof ProductInput, nextValue: string) {
    setValue((current) => ({ ...current, [field]: nextValue }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSubmit(value);
  }

  return (
    <form className="product-form" onSubmit={submit}>
      <div className="form-grid">
        <label>
          商品名稱 <span aria-hidden="true">*</span>
          <input
            required
            maxLength={256}
            value={value.productName}
            onChange={(event) => update("productName", event.target.value)}
          />
        </label>
        <label>
          SKU
          <input maxLength={128} value={value.sku} onChange={(event) => update("sku", event.target.value)} />
        </label>
        <label>
          品牌
          <input maxLength={128} value={value.brand} onChange={(event) => update("brand", event.target.value)} />
        </label>
        <label>
          分類
          <input
            maxLength={128}
            value={value.category}
            onChange={(event) => update("category", event.target.value)}
          />
        </label>
        <label>
          子分類
          <input
            maxLength={128}
            value={value.subcategory}
            onChange={(event) => update("subcategory", event.target.value)}
          />
        </label>
        <label>
          幣別
          <input
            maxLength={3}
            pattern="[A-Z]{3}"
            placeholder="TWD"
            value={value.currency}
            onChange={(event) => update("currency", event.target.value.toUpperCase())}
          />
        </label>
        <label>
          成本
          <input
            inputMode="decimal"
            placeholder="0.0000"
            value={value.cost}
            onChange={(event) => update("cost", event.target.value)}
          />
        </label>
        <label>
          售價
          <input
            inputMode="decimal"
            placeholder="0.0000"
            value={value.salePrice}
            onChange={(event) => update("salePrice", event.target.value)}
          />
        </label>
        <label>
          庫存
          <input inputMode="numeric" value={value.stock} onChange={(event) => update("stock", event.target.value)} />
        </label>
        <label className="span-two">
          商品網址
          <input
            type="url"
            maxLength={2048}
            value={value.productUrl}
            onChange={(event) => update("productUrl", event.target.value)}
          />
        </label>
        <label className="span-two">
          簡短描述
          <textarea
            maxLength={2000}
            rows={5}
            value={value.shortDescription}
            onChange={(event) => update("shortDescription", event.target.value)}
          />
        </label>
      </div>
      <button className="primary-button" type="submit" disabled={disabled}>
        {disabled ? "處理中…" : submitLabel}
      </button>
    </form>
  );
}
