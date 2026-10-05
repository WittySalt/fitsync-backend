-- A new database: V906 and V907 set the domains after V28 has run, so the logo addresses are rebuilt again here.
update food_brands set logo_url = 'https://www.google.com/s2/favicons?domain=' || domain || '&sz=128' where domain is not null;
