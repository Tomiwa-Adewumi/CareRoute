package com.careroute.ingestion;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PostgresMigrationTest {
    @Test void v2RemovesExistingDuplicatesBeforeCreatingConstraint() throws Exception {
        String url = System.getenv("TEST_DATABASE_URL");
        String user = System.getenv().getOrDefault("TEST_DATABASE_USERNAME", "postgres");
        String password = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "postgres");
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            try {
                Flyway.configure().dataSource(url, user, password).schemas(schema).target("1").load().migrate();
                sql.execute("set search_path to " + schema);
                sql.execute("insert into hospitals (id, name) values ('fixture', 'Fixture Hospital')");
                for (int i = 0; i < 2; i++) sql.execute("insert into wait_time_observations (id,hospital_id,wait_minutes,observed_at,source) values ('" + UUID.randomUUID() + "','fixture',40,'2026-01-01T00:00:00Z','TEST')");
                Flyway.configure().dataSource(url, user, password).schemas(schema).load().migrate();
                try (var result = sql.executeQuery("select count(*) from wait_time_observations")) {
                    result.next(); assertEquals(1, result.getInt(1));
                }
            } finally { sql.execute("drop schema if exists " + schema + " cascade"); }
        }
    }
}
