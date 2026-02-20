# prepstmt-cache-lab
MySQL PreparedStatement의 캐시 동작과 Client/Server 모드 성능 차이를 분석한 벤치마크 프로젝트

## **MySQL JDBC PreparedStatement Caching 동작 원리**

MySQL JDBC 드라이버의 `serverSideStatementCache` 동작 원리를 소스 코드 레벨에서 분석하여, 특정 상황에서 캐싱이 동작하지 않는 이유를 알아보았습니다. [포스팅 참조](https://velog.io/@min1234/PreparedStatement-%EC%BA%90%EC%8B%B1-%EC%9B%90%EB%A6%AC%EC%97%90-%EB%8C%80%ED%95%B4-%EC%95%8C%EC%95%84%EB%B3%B4%EC%9E%90)

- stmt1.close() -> ConnectionImpl.class-> recachePreparedStatement
  ```java
  Object oldServerPrepStmt = this.serverSideStatementCache.put(
                              new CompoundCacheKey(pstmt.getCurrentDatabase(), ((PreparedQuery) pstmt.getQuery()).getOriginalSql()),
                              (ServerPreparedStatement) pstmt);
  ```
  PreparedStatment 객체인 stmt1를 close할 때에 해당 객체를 캐싱하기 때문에, stmt1으로 캐싱된 값을 이용하기 위해서는 stmt1을 close를 먼저 해주어야 합니다. 

## **MySQL JDBC ClientPreparedStatement 동작 원리**
  ```java
  public java.sql.PreparedStatement clientPrepareStatement(String sql) throws SQLException {
        return clientPrepareStatement(sql, DEFAULT_RESULT_SET_TYPE, DEFAULT_RESULT_SET_CONCURRENCY);
  }
  ```
ClientPreparedStatement의 경우 PreparedStatement 객체 자체를 캐시 하지 않고 ClientPreparedStatement를 재생성하는 형태로 구현하고 있습니다.

이는 변경이 없는 쿼리문 자체나 테이블 이름과 같은 메타 정보를 담은 QueryInfo 타입을 캐싱해서 파라미터를 별도로 처리하기 위함입니다.  
만약 파라미터까지 캐싱할 경우, 시점에 따라 캐싱된 쿼리 결과와 특정 시점에 실행한 쿼리 결과가 달라지는 문제가 발생할 수 있습니다.
