alter table app_users
    add column auth_provider varchar(30) not null default 'LOCAL';

alter table app_users
    add column external_subject varchar(120);

alter table app_users
    add constraint uk_app_users_external_subject unique (external_subject);
