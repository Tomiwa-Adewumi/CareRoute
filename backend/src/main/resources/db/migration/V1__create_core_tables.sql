create table if not exists hospitals (
    id varchar(160) primary key,
    name varchar(255) not null,
    city varchar(120),
    province varchar(32) not null default 'ON',
    address varchar(255),
    latitude double precision,
    longitude double precision,
    emergency_department boolean not null default true,
    pediatric_emergency boolean not null default false,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists wait_time_observations (
    id uuid primary key,
    hospital_id varchar(160) not null references hospitals(id) on delete cascade,
    wait_minutes integer not null check (wait_minutes >= 0),
    observed_at timestamptz not null,
    collected_at timestamptz not null default now(),
    source varchar(80) not null,
    source_url text
);

create index if not exists idx_wait_observations_hospital_observed
    on wait_time_observations(hospital_id, observed_at desc);

create index if not exists idx_hospitals_active_province
    on hospitals(active, province);

alter table hospitals enable row level security;
alter table wait_time_observations enable row level security;
