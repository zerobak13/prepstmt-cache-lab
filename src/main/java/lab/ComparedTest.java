package lab;

import java.sql.*;
import java.util.Locale;
import java.util.Properties;

public class ComparedTest {

	// ====== 환경에 맞게 수정 ======
	static final String HOST = "127.0.0.1";
	static final int PORT = 3306;
	static final String DB = "bench";
	static final String USER = "root";
	static final String PASS = "1234";
//	static final String HOST = "";
//	static final int PORT = -1;
//	static final String DB = "railway";
//	static final String USER = "root";
//	static final String PASS = "";

	// 데이터 규모 / 반복 횟수
	static final int ROWS_TO_LOAD = 200_000; // 초기 데이터 적재량
	static final int WARMUP_ITERS = 30_000; // 워밍업 쿼리 실행 횟수
	static final int MEASURE_ITERS = 200_000; // 측정 쿼리 실행 횟수
//	static final int ROWS_TO_LOAD = 50_000; // 6,000
//	static final int WARMUP_ITERS = 10_000;
//	static final int MEASURE_ITERS = 50_000;

	static final int BATCH_SIZE = 1_000;

	// 벤치 대상 쿼리: 인덱스(grp) + PK(id) 혼합 (파라미터 2개)
	static final String SELECT_SQL = "SELECT payload FROM user_bench WHERE grp = ? AND id = ?";

	// 배치 INSERT 쿼리
	static final String INSERT_SQL = "INSERT INTO user_bench (id, grp, payload) VALUES (?, ?, ?)";

	public static void main(String[] args) throws Exception {
		Locale.setDefault(Locale.US);
//		try (Connection c = connect(baseUrlWithParams("useSSL=true&requireSSL=false&verifyServerCertificate=false"))) {
//		    System.out.println("Connected! " + c.getMetaData().getURL());
//		}

		// 1) 데이터 준비 (한 번만)
		try (Connection admin = connect(baseUrlWithParams(""))) {
			admin.setAutoCommit(true);
			ensureSchema(admin);
			maybeLoadData(admin);
		}

		// 2) 두 설정 URL 준비
		// 공통 옵션: 성능/테스트 안정화에 도움되는 것들
		// - useSSL=false, serverTimezone=UTC(환경 따라 변경), useUnicode=true,
		// characterEncoding=utf8
		// - cachePrepStmts=true (클라/서버 모두에서 드라이버 캐시 켬)
		// - prepStmtCacheSize / prepStmtCacheSqlLimit: 캐시 크기/SQL 길이 제한
		String common = "useSSL=false" + "&useUnicode=true&characterEncoding=utf8" + "&serverTimezone=UTC"
				+ "&cachePrepStmts=true" + "&prepStmtCacheSize=256" + "&prepStmtCacheSqlLimit=2048"
				+ "&useLocalSessionState=true" + "&cacheServerConfiguration=true" + "&elideSetAutoCommits=true";
//		String common =
//			    "useSSL=true" +
//			    "&requireSSL=false" +
//			    "&verifyServerCertificate=false" +   // 인증서 검증으로 막히는 경우 예방
//			    "&useUnicode=true&characterEncoding=utf8" +
//			    "&serverTimezone=UTC" +
//			    "&cachePrepStmts=true" +
//			    "&prepStmtCacheSize=256" +
//			    "&prepStmtCacheSqlLimit=2048" +
//			    "&useLocalSessionState=true" +
//			    "&cacheServerConfiguration=true" +
//			    "&elideSetAutoCommits=true";

		String clientSide = common + "&useServerPrepStmts=false";
		String serverSide = common + "&useServerPrepStmts=true";

		String urlClient = baseUrlWithParams(clientSide);
		String urlServer = baseUrlWithParams(serverSide);

		// 3) 벤치 실행
		System.out.println("\n=== Benchmark: SELECT (grp=?, id=?) ===");
		BenchResult r1 = runSelectBench("CLIENT  (useServerPrepStmts=false)", urlClient);
		BenchResult r2 = runSelectBench("SERVER  (useServerPrepStmts=true )", urlServer);

		// 4) 결과 출력
		print(r1);
		System.out.println();
		print(r2);
	}

	// ====== 벤치: SELECT ======
	static BenchResult runSelectBench(String name, String jdbcUrl) throws Exception {
		try (Connection c = connect(jdbcUrl)) {
			c.setAutoCommit(true);

			// 워밍업
			long warmupChecksum = 0;
			try (PreparedStatement ps = c.prepareStatement(SELECT_SQL)) {
				for (int i = 1; i <= WARMUP_ITERS; i++) {
					int grp = i % 100; // grp는 0~99
					long id = i; // id는 1..rows
					ps.setInt(1, grp);
					ps.setLong(2, id);
					try (ResultSet rs = ps.executeQuery()) {
						if (rs.next())
							warmupChecksum += rs.getString(1).length();
					}
				}
			}

			// 측정
			long checksum = 0;
			long start = System.nanoTime();

			try (PreparedStatement ps = c.prepareStatement(SELECT_SQL)) {
				for (int i = 1; i <= MEASURE_ITERS; i++) {
					int grp = i % 100;
					long id = (i % ROWS_TO_LOAD) + 1; // 범위 내에서 반복
					ps.setInt(1, grp);
					ps.setLong(2, id);
					try (ResultSet rs = ps.executeQuery()) {
						if (rs.next())
							checksum += rs.getString(1).length();
					}
				}
			}

			long end = System.nanoTime();
			long elapsedNs = end - start;

			// 체크섬으로 "최적화로 코드가 사라지는" 걸 방지 (의미는 없음)
			checksum += warmupChecksum;

			double sec = elapsedNs / 1_000_000_000.0;
			double qps = MEASURE_ITERS / sec;
			double avgUs = (elapsedNs / 1000.0) / MEASURE_ITERS;

			return new BenchResult(name, elapsedNs, sec, qps, avgUs, checksum);
		}
	}

	// ====== 스키마/데이터 ======
	static void ensureSchema(Connection c) throws SQLException {
		try (Statement st = c.createStatement()) {
			st.execute("CREATE DATABASE IF NOT EXISTS " + DB);
			st.execute("USE " + DB);
			st.execute("DROP TABLE IF EXISTS user_bench");
			st.execute("CREATE TABLE user_bench (id BIGINT PRIMARY KEY, grp INT NOT NULL, payload VARCHAR(200) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY idx_grp (grp))");
		}
	}

	static void maybeLoadData(Connection c) throws SQLException {
		System.out.println("Loading data: " + ROWS_TO_LOAD + " rows ...");
		c.setAutoCommit(false);

		try (PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {
			for (int i = 1; i <= ROWS_TO_LOAD; i++) {
				ps.setLong(1, i);
				ps.setInt(2, i % 100); // grp 0~99
				ps.setString(3, "payload-" + i);
				ps.addBatch();

				if (i % BATCH_SIZE == 0) {
					ps.executeBatch();
					c.commit();
				}
			}
			ps.executeBatch();
			c.commit();
		} catch (SQLException e) {
			c.rollback();
			throw e;
		} finally {
			c.setAutoCommit(true);
		}
		System.out.println("Data loaded.\n");
	}

	// ====== 커넥션 유틸 ======
	static Connection connect(String url) throws SQLException {
		Properties p = new Properties();
		p.setProperty("user", USER);
		p.setProperty("password", PASS);
		// 드라이버에 따라 도움이 되는 경우가 있어 명시
		// p.setProperty("tcpKeepAlive", "true");
		return DriverManager.getConnection(url, p);
	}

	static String baseUrlWithParams(String params) {
		String base = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB;
		if (params == null || params.isBlank())
			return base;
		return base + "?" + params;
	}

	// ====== 출력 ======
	static void print(BenchResult r) {
		System.out.printf("%s%n  elapsed=%.3fs  QPS=%.0f  avg=%.2f µs/op  checksum=%d%n", r.name, r.seconds, r.qps,
				r.avgMicros, r.checksum);
	}

	static class BenchResult {
		final String name;
		final long elapsedNs;
		final double seconds;
		final double qps;
		final double avgMicros;
		final long checksum;

		BenchResult(String name, long elapsedNs, double seconds, double qps, double avgMicros, long checksum) {
			this.name = name;
			this.elapsedNs = elapsedNs;
			this.seconds = seconds;
			this.qps = qps;
			this.avgMicros = avgMicros;
			this.checksum = checksum;
		}
	}
}