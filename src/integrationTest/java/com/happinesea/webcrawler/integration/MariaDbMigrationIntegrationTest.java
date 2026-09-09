package com.happinesea.webcrawler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MariaDbMigrationIntegrationTest {
	private final String url = System.getenv("MARIADB_URL");
	private final String username = System.getenv("MARIADB_USERNAME");
	private final String password = System.getenv("MARIADB_PASSWORD");

	@BeforeEach
	void cleanDatabase() {
		flyway().clean();
	}

	@Test
	void migratesEmptyDatabaseAndCreatesLeaseAndCanonicalConstraints() throws Exception {
		flyway().migrate();

		try (Connection connection = connection()) {
			assertThat(columnExists(connection, "site_info_process_pool", "lease_attempt")).isTrue();
			assertThat(columnExists(connection, "site_info_process_pool", "heartbeat_at")).isTrue();
			assertThat(columnExists(connection, "site_contents", "normalized_url_hash")).isTrue();
			assertThat(indexExists(connection, "site_contents", "uk_site_contents_normalized_url_hash")).isTrue();
		}
	}

	@Test
	void upgradesVersionSixSchemaAndAllowsOnlyOneAtomicClaim() throws Exception {
		Flyway.configure().dataSource(url, username, password).target("6").load().migrate();
		flyway().migrate();

		try (Connection connection = connection()) {
			connection.createStatement().executeUpdate("""
					INSERT INTO site_info(site_name, site_url) VALUES ('test', 'https://example.test')
					""");
			connection.createStatement().executeUpdate("""
					INSERT INTO site_category(site_info_id, category_name, category_url,
					list_record_select_id, title_record_select_id, contents_url_selectId,
					body_select_id, more_body_select_id)
					VALUES (1, 'test', 'https://example.test/news', 'li', 'a', 'a', 'body', 'body')
					""");
			connection.createStatement().executeUpdate(
					"INSERT INTO site_info_process_pool(site_category_id, process_status) VALUES (1, '1')");
		}

		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var first = executor.submit(() -> claim("owner-one", start));
			var second = executor.submit(() -> claim("owner-two", start));
			start.countDown();
			assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}

	private int claim(String owner, CountDownLatch start) throws Exception {
		start.await(5, TimeUnit.SECONDS);
		try (Connection connection = connection(); var statement = connection.prepareStatement("""
				UPDATE site_info_process_pool
				SET process_status='2', process_id=?, owner_instance=?, lease_attempt=lease_attempt+1,
				    claimed_at=NOW(6), heartbeat_at=NOW(6), process_time=NOW(6)
				WHERE site_info_process_id=1 AND process_status IN ('1','9','0')
				""")) {
			statement.setString(1, owner);
			statement.setString(2, owner);
			return statement.executeUpdate();
		}
	}

	private Flyway flyway() {
		return Flyway.configure().dataSource(url, username, password).cleanDisabled(false).load();
	}

	private Connection connection() throws Exception {
		return DriverManager.getConnection(url, username, password);
	}

	private boolean columnExists(Connection connection, String table, String column) throws Exception {
		try (ResultSet result = connection.getMetaData().getColumns(null, null, table, column)) {
			return result.next();
		}
	}

	private boolean indexExists(Connection connection, String table, String index) throws Exception {
		try (ResultSet result = connection.getMetaData().getIndexInfo(null, null, table, true, false)) {
			while (result.next()) {
				if (index.equalsIgnoreCase(result.getString("INDEX_NAME"))) {
					return true;
				}
			}
			return false;
		}
	}
}
