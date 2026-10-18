-- Existing facilities stay unranked until a successful feed refresh establishes availability.
alter table hospitals add column open_now boolean;
alter table hospitals add column wait_available boolean not null default false;
alter table hospitals add column last_seen_at timestamptz;
