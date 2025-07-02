package com.filipmikolajzeglen.cqrs.persistence.database

import com.filipmikolajzeglen.cqrs.persistence.DBSpecification

class DatabaseCommandSpec extends DBSpecification {

   private static final String SQL_INIT_DATA ='/com/filipmikolajzeglen/cqrs/persistence/database/DatabaseCommandSpec.sql'

   @Override
   protected String sqlInitData() {
      return getClass().getResource(SQL_INIT_DATA).text
   }

   def "should persist entity using DatabaseCommand.create"() {
      given:
      def entity = new DummyDatabaseEntity(name: "New", flag: true, number: 123L)
      def handler = new DatabaseCommandHandler<DummyDatabaseEntity>(entityManager)

      when:
      def result = handler.handle(DatabaseCommand.create(entity))
      entityManager.flush()
      entityManager.clear()
      def persisted = entityManager.find(DummyDatabaseEntity, result.id)

      then:
      persisted != null
      persisted.name == "New"
      persisted.flag
      persisted.number == 123L
   }

   def "should update entity using DatabaseCommand.update"() {
      given:
      def handler = new DatabaseCommandHandler<DummyDatabaseEntity>(entityManager)
      def entity = entityManager
            .createQuery("SELECT e FROM DummyDatabaseEntity e WHERE name = 'To modify'", DummyDatabaseEntity)
            .singleResult

      expect:
      entity != null
      with(entity) {
         it.name == "To modify"
         it.flag
         it.number == 1000L
      }

      when:
      entity.name = "Modified"
      entity.flag = false
      entity.number = 2000L

      def updated = handler.handle(DatabaseCommand.update(entity))
      entityManager.flush()
      entityManager.clear()
      def found = entityManager.find(DummyDatabaseEntity, updated.id)

      then:
      with(found) {
         it.name == "Modified"
         !it.flag
         it.number == 2000L
      }
   }

   def "should remove entity using DatabaseCommand.remove"() {
      given:
      def handler = new DatabaseCommandHandler<DummyDatabaseEntity>(entityManager)
      def entity = entityManager
            .createQuery("SELECT e FROM DummyDatabaseEntity e WHERE name = 'To remove'", DummyDatabaseEntity)
            .singleResult
      assert entity != null

      when:
      handler.handle(DatabaseCommand.remove(entity))
      entityManager.flush()
      entityManager.clear()

      then:
      entityManager.find(DummyDatabaseEntity, -2L) == null
   }

   def "should flush using DatabaseCommand.flush"() {
      given:
      def entity = new DummyDatabaseEntity(name: "FlushTest", flag: false, number: 99L)
      def handler = new DatabaseCommandHandler<DummyDatabaseEntity>(entityManager)
      handler.handle(DatabaseCommand.create(entity))

      when:
      handler.handle(DatabaseCommand.flush())
      entityManager.clear()
      def persisted = entityManager.find(DummyDatabaseEntity, entity.id)

      then:
      persisted != null
      persisted.name == "FlushTest"
   }
}
