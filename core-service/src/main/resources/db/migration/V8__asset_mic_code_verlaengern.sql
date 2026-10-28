-- B6 (BE #88): Suche nach „ETHA“ lieferte 500, weil Twelve Data für einen Treffer
-- einen MIC-Code mit mehr als 10 Zeichen liefert („value too long for type character varying(10)“).
-- V2 bleibt unverändert, da Flyway sie bereits ausgeführt hat.
-- Der Unique-Constraint uq_asset_symbol_mic bleibt beim Verlängern bestehen.

ALTER TABLE asset ALTER COLUMN mic_code TYPE VARCHAR(50);