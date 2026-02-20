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
