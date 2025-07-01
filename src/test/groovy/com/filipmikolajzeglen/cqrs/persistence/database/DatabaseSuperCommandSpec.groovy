package com.filipmikolajzeglen.cqrs.persistence.database

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.jdbc.Sql
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.spock.Testcontainers
import spock.lang.Specification

@Sql
@Testcontainers
@Transactional
@SpringBootTest
class DatabaseSuperCommandSpec extends Specification {

   @PersistenceContext
   EntityManager entityManager

   def "should update multiple entities using DatabaseSuperCommand.update"() {
      when:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).equalTo("1111111")
            .build()
      def command = DatabaseSuperCommand.update(DummyDatabaseEntity)
            .set(DummyDatabaseEntity::setName, "Zmieniony")
            .set(DummyDatabaseEntity::setFlag, false)
            .where(query)
      def updatedCount = command.execute(entityManager)
      entityManager.flush()
      entityManager.clear()

      then:
      updatedCount == 1
      def changed = entityManager.createQuery(
            "select e from DummyDatabaseEntity e where e.name = :name", DummyDatabaseEntity)
            .setParameter("name", "Zmieniony")
            .resultList
      changed.size() == 1
      changed.every { !it.flag }
   }

   def "should update by property name"() {
      when:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property("name").equalTo("2222222")
            .build()
      def command = DatabaseSuperCommand.update(DummyDatabaseEntity)
            .set("number", 99L)
            .where(query)
      def updated = command.execute(entityManager)
      entityManager.flush()
      entityManager.clear()

      then:
      updated == 1
      def found = entityManager.createQuery(
            "select e from DummyDatabaseEntity e where e.name = :name", DummyDatabaseEntity)
            .setParameter("name", "2222222")
            .singleResult
      found.number == 99L
   }

   def "should not update when where does not match"() {
      when:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).equalTo("Nieistnieje")
            .build()
      def command = DatabaseSuperCommand.update(DummyDatabaseEntity)
            .set(DummyDatabaseEntity::setFlag, false)
            .where(query)
      def updated = command.execute(entityManager)
      entityManager.flush()
      entityManager.clear()

      then:
      updated == 0
   }

   def "should update all entities when no where clause is provided"() {
      given:
      def command = DatabaseSuperCommand.update(DummyDatabaseEntity)
            .set(DummyDatabaseEntity::setFlag, false).where()

      when:
      def updatedCount = command.execute(entityManager)
      entityManager.flush()
      entityManager.clear()

      then:
      updatedCount == 7
      def all = entityManager.createQuery(
            "select e from DummyDatabaseEntity e", DummyDatabaseEntity
      ).resultList
      all.size() == 7
      all.every { !it.flag }
   }

   def "should throw UnsupportedOperationException in applyToEntity for string property setter"() {
      given:
      def setter = DatabaseSuperCommand.Setter.from("someProperty", "value")

      when:
      setter.applyToEntity(new Object())

      then:
      def e = thrown(UnsupportedOperationException)
      e.message == "Not supported yet."
   }
}