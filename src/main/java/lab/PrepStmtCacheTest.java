package lab;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class PrepStmtCacheTest {
	public static void main(String[] args) throws Exception {
		String properties = "?useServerPrepStmts=true&cachePrepStmts=true";
		String url = "jdbc:mysql://localhost:3306/testdb" + properties;
		Connection conn = DriverManager.getConnection(url, "root", "1234");
		
		PreparedStatement stmt1 = conn.prepareStatement("SELECT * FROM new_table WHERE idnew_table = ?");
		stmt1.close(); 

		PreparedStatement stmt2 = conn.prepareStatement("SELECT * FROM new_table WHERE idnew_table = ?");
		stmt2.close();
		
		System.out.println("동일 객체인가? " + (stmt1 == stmt2));
		System.out.println(stmt1.getClass());
		System.out.println(stmt2.getClass());
		
		conn.close();
	}
}