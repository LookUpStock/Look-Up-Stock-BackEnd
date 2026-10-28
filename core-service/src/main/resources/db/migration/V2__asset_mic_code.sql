--ergänzung der mic-Spalte in der asset-Tabelle

ALTER TABLE asset ADD COLUMN mic_code VARCHAR(10);
ALTER TABLE asset ADD CONSTRAINT uq_asset_symbol_mic UNIQUE (symbol, mic_code);