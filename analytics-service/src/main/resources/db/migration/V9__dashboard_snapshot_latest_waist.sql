alter table analytics_user_dashboard_snapshot
    add column if not exists latest_waist_cm numeric(6, 2);

alter table analytics_user_dashboard_snapshot
    add column if not exists waist_measured_on date;
