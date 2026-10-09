-- When the user last gave consent to the processing of their health answers (readiness
-- questionnaire, injuries). Set by user-service when data_sharing_consent turns true, cleared when it
-- turns false. Rows that already hold consent keep null: the moment it was given was not recorded.
alter table user_app_preferences add column health_data_consent_at timestamp with time zone;
