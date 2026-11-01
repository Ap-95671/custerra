package com.nci.backend.operations;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_DB_UPGRADE_URL", matches="jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/custerra_upgrade_[a-zA-Z0-9_]+")
class MigrationUpgradeTests {
    @Test void v4LegacyRecordsSurviveOwnershipAndNotificationMigrations() throws Exception {
        String url=System.getenv("TEST_DB_UPGRADE_URL"),user=System.getenv("TEST_DB_USERNAME"),password=System.getenv("TEST_DB_PASSWORD");
        try(var connection=java.sql.DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
            try(var result=statement.executeQuery("select count(*) from information_schema.tables where table_schema='public'")) {
                result.next(); assertThat(result.getInt(1)).as("Upgrade test requires a NEW empty isolated database").isZero();
            }
            Flyway.configure().dataSource(url,user,password).target("4").cleanDisabled(true).load().migrate();
            statement.executeUpdate("insert into monitored_journey(id,name,status,route,created_at) values ('11111111-1111-4111-8111-111111111111','Legacy fixture','ACTIVE','{}',now())");
            statement.executeUpdate("insert into history_snapshot(id,fetched_at,payload) values ('22222222-2222-4222-8222-222222222222',now(),'{}')");
            var flyway=Flyway.configure().dataSource(url,user,password).cleanDisabled(true).load();
            flyway.migrate(); flyway.validate();
            try(var result=statement.executeQuery("select status,owner_id,route from monitored_journey")) {
                assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("PAUSED");
                assertThat(result.getObject(2)).isNull();assertThat(result.getString(3)).isEqualTo("{}");
            }
            try(var result=statement.executeQuery("select payload,owner_id from history_snapshot")) {
                assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("{}");assertThat(result.getObject(2)).isNull();
            }
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            var invalid=java.nio.file.Files.createTempDirectory("custerra-invalid-migration-");
            var script=invalid.resolve("V999__deliberate_failure.sql");
            try {
                java.nio.file.Files.writeString(script,"CREATE TABLE rollback_fixture(id int); THIS IS NOT SQL;");
                var broken=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration","filesystem:"+invalid).cleanDisabled(true).load();
                assertThatThrownBy(broken::migrate).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
                try(var result=statement.executeQuery("select to_regclass('public.rollback_fixture')")){result.next();assertThat(result.getString(1)).isNull();}
                flyway.validate();assertThat(flyway.migrate().migrationsExecuted).isZero();
            } finally {java.nio.file.Files.deleteIfExists(script);java.nio.file.Files.deleteIfExists(invalid);}
        }
    }
}
