package com.filipmikolajzeglen.cqrs.persistence

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.EntityTransaction
import jakarta.persistence.Persistence
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

@Testcontainers
class DBSpecification extends Specification {

   @Shared
   PostgreSQLContainer container = new PostgreSQLContainer("postgres:16-alpine")
         .withDatabaseName("testdb")
         .withUsername("test")
         .withPassword("test")

   EntityManagerFactory entityManagerFactory
   EntityManager entityManager
   EntityTransaction entityTransaction

   final def setupSpec() {
      container.start()
      runSqlInit()
   }

   final def cleanupSpec() {
      container.stop()
   }

   final def setup() {
      Map<String, Object> props = [
            "jakarta.persistence.jdbc.url"     : container.jdbcUrl,
            "jakarta.persistence.jdbc.user"    : container.username,
            "jakarta.persistence.jdbc.password": container.password,
            "jakarta.persistence.jdbc.driver"  : "org.postgresql.Driver",
            "hibernate.hbm2ddl.auto"           : "none",
            "hibernate.dialect"                : "org.hibernate.dialect.PostgreSQLDialect"
      ]
      entityManagerFactory = Persistence.createEntityManagerFactory("test-persistence-unit", props)
      entityManager = entityManagerFactory.createEntityManager()
      entityTransaction = entityManager.transaction
      entityTransaction.begin()
   }

   final def cleanup() {
      if (entityTransaction?.isActive()) {
         entityTransaction.rollback()
      }
      entityManager?.close()
      entityManagerFactory?.close()
   }

   final def runSqlInit() {
      Connection conn = DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
      Statement stmt = conn.createStatement()
      stmt.execute("""
            CREATE SCHEMA IF NOT EXISTS fmzcqrspersistence;
            CREATE SEQUENCE IF NOT EXISTS fmzcqrspersistence.dummy_database_entity_seq START WITH 1 INCREMENT BY 1;
            CREATE TABLE IF NOT EXISTS fmzcqrspersistence.dummy_database_entity (
               id     BIGINT PRIMARY KEY DEFAULT NEXTVAL('fmzcqrspersistence.dummy_database_entity_seq'),
               name   VARCHAR(255) NOT NULL,
               flag   BOOLEAN      NOT NULL,
               number BIGINT
            );
        """)

      def insertSql = sqlInitData()
      if (insertSql?.trim()) {
         stmt.execute(insertSql)
      }

      stmt.close()
      conn.close()
   }

   /**
    * Override in child class to provide INSERTs or any test-specific SQL data.
    */
   protected String sqlInitData() {
      return ""
   }
}
