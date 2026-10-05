-- Logos for two brands the catalogue already holds in another spelling than the USDA rules (Quaker, FAGE).
update food_brands set domain = 'quakeroats.com', logo_url = 'https://logo.clearbit.com/quakeroats.com' where name = 'Quaker' and domain is null;
update food_brands set domain = 'fage.com', logo_url = 'https://logo.clearbit.com/fage.com' where name = 'FAGE' and domain is null;
