package com.careroute.data;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:17:///careroute",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "careroute.ingestion.initial-delay-ms=3600000"
})
class PostgresMigrationIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayCreatesAndValidatesProductionSchema() {
        Integer migrations = jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class);
        Integer coreTables = jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name in ('hospitals','wait_time_observations')", Integer.class);
        assertThat(migrations).isEqualTo(2);
        assertThat(coreTables).isEqualTo(2);
    }
}
