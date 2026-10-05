-- Clearbit's logo endpoint no longer answers (every logo failed to load), so the logo address is rebuilt from the
-- stored domain against Google's public favicon service, which returns the brand's icon at up to 128 px.
update food_brands set logo_url = 'https://www.google.com/s2/favicons?domain=' || domain || '&sz=128' where domain is not null;
