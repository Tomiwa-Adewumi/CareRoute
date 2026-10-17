delete from wait_time_observations a
using wait_time_observations b
where a.id > b.id
  and a.hospital_id = b.hospital_id
  and a.observed_at = b.observed_at
  and a.wait_minutes = b.wait_minutes;

create unique index if not exists uq_wait_observation_identity
    on wait_time_observations(hospital_id, observed_at, wait_minutes);
