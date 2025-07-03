package com.filipmikolajzeglen.cqrs.persistence.database

import com.filipmikolajzeglen.cqrs.core.Pagination
import com.filipmikolajzeglen.cqrs.core.PaginationType
import com.filipmikolajzeglen.cqrs.persistence.DBSpecification

class DatabaseQuerySpec extends DBSpecification {

   private static final String SQL_INIT_DATA = '/com/filipmikolajzeglen/cqrs/persistence/database/DatabaseQuerySpec.sql'

   @Override
   protected String sqlInitData() {
      return getClass().getResource(SQL_INIT_DATA).text
   }

   def 'should fetch entities using #testCase propertyAccessor and methods: equalTo(), in(), isNotNull()'(
         String testCase, DatabaseQuery query) {
      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager).handle(query, Pagination.all())

      then:
      result.size() == 3
      with(result[0]) {
         it.id == 1L
         it.name == 'John'
         it.flag
         it.number == 1000L
      }
      with(result[1]) {
         it.id == 3L
         it.name == 'John'
         !it.flag
         it.number == 2500L
      }
      with(result[2]) {
         it.id == 5L
         it.name == 'John'
         !it.flag
         it.number == 2000L
      }

      where:
      testCase   | query
      'Function' | DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).equalTo('John')
            .property(DummyDatabaseEntity::getId).in([1L, 2L, 3L, 5L, 6L])
            .property(DummyDatabaseEntity::getNumber).isNotNull()
            .build()
      'String'   | DatabaseQuery.builder(DummyDatabaseEntity)
            .property('name').equalTo('John')
            .property('id').in([1L, 2L, 3L, 5L, 6L])
            .property('number').isNotNull()
            .build()
   }

   def 'should fetch entities with not() and isNull() methods'() {
      when:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).not().equalTo('John')
            .property(DummyDatabaseEntity::getId).in([1L, 2L, 3L, 5L, 6L, 7L, 9L])
            .property(DummyDatabaseEntity::getNumber).isNull()
            .build()
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager).handle(query, Pagination.all())

      then:
      result.size() == 2
      with(result[0]) {
         it.id == 7L
         it.name == 'Joanna'
         !it.flag
         it.number == null
      }
      with(result[1]) {
         it.id == 9L
         it.name == 'Jane'
         !it.flag
         it.number == null
      }
   }

   def 'should handle in() with collection containing null'() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).in([null, 'John'])
            .build()

      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, Pagination.all())

      then:
      result.any { it.name == null } || result.any { it.name == 'John' }
   }

   def 'should fetch single entity using Pagination.single()'() {
      given:
      def entityId = 1L
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getId).equalTo(entityId)
            .build()

      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager).handle(query, Pagination.single())

      then:
      with(result) {
         it.id == entityId
         it.name == 'John'
         it.flag
         it.number == 1000L
      }
   }

   def 'should fetch single entity using Pagination.optional()'() {
      given:
      def entityId = 1L
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getId).equalTo(entityId)
            .build()

      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager).handle(query, Pagination.optional())

      then:
      with(result.get()) {
         it.id == entityId
         it.name == 'John'
         it.flag
         it.number == 1000L
      }
   }

   def 'should fetch paged result using Pagination.paged() for page=#page, size=#size, totalCount=#totalCount'() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).equalTo('John')
            .property(DummyDatabaseEntity::getId).in([1L, 3L, 5L])
            .build()

      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, Pagination.paged(page, size, totalCount))

      then:
      with(result) {
         it.content*.id == expectedIds
         it.content.size() == expectedSize
         it.page == page
         it.size == size
         it.totalElements == totalCount
         it.totalPages == expectedTotalPages
      }

      where:
      page | size | totalCount || expectedIds  | expectedSize | expectedTotalPages
      0    | 2    | 3          || [1L, 3L]     | 2            | 2
      1    | 2    | 3          || [5L]         | 1            | 2
      0    | 3    | 3          || [1L, 3L, 5L] | 3            | 1
      1    | 3    | 3          || []           | 0            | 1
      0    | 1    | 3          || [1L]         | 1            | 3
      2    | 1    | 3          || [5L]         | 1            | 3
      3    | 1    | 3          || []           | 0            | 3
   }

   def 'should fetch sliced result using Pagination.sliced() for offset=#offset, limit=#limit'() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).equalTo('John')
            .property(DummyDatabaseEntity::getId).in([1L, 3L, 5L])
            .build()

      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, Pagination.sliced(offset, limit))

      then:
      with(result) {
         it.content*.id == expectedIds
         it.content.size() == expectedSize
         it.offset == offset
         it.limit == limit
         it.hasNext == expectedHasNext
      }

      where:
      offset | limit || expectedIds  | expectedSize | expectedHasNext
      0      | 2     || [1L, 3L]     | 2            | true
      2      | 2     || [5L]         | 1            | false
      0      | 3     || [1L, 3L, 5L] | 3            | false
      1      | 1     || [3L]         | 1            | true
      2      | 1     || [5L]         | 1            | false
      3      | 1     || []           | 0            | false
   }

   def "should check existence using Pagination.exist()"() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).equalTo('John')
            .build()

      when:
      def exists = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, Pagination.exist())

      then:
      exists == true

      when: 'no matching entity'
      def notExists = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(DatabaseQuery.builder(DummyDatabaseEntity)
                  .property(DummyDatabaseEntity::getName).equalTo('NotExistingName')
                  .build(), Pagination.exist())

      then:
      notExists == false
   }

   def "should count entities using Pagination.count()"() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).equalTo('John')
            .build()

      when:
      def count = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, Pagination.count())

      then:
      count == 4L

      when: 'no matching entity'
      def zeroCount = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(DatabaseQuery.builder(DummyDatabaseEntity)
                  .property(DummyDatabaseEntity::getName).equalTo('NotExistingName')
                  .build(), Pagination.count())

      then:
      zeroCount == 0L
   }

   def 'should fetch first entity using Pagination.first()'() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).equalTo('John')
            .build()

      when:
      def first = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, Pagination.first())

      then:
      first.isPresent()
      first.get().name == 'John'

      when: 'no matching entity'
      def none = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(DatabaseQuery.builder(DummyDatabaseEntity)
                  .property(DummyDatabaseEntity::getName).equalTo('NotExistingName')
                  .build(), Pagination.first())

      then:
      !none.isPresent()
   }

   def 'should build query with equalTo and in restriction'() {
      when:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).equalTo('John')
            .property(DummyDatabaseEntity::getId).in([1L, 2L, 3L])
            .build()

      then:
      query.entityType == DummyDatabaseEntity
      query.restrictions.size() == 2
   }

   def 'should apply not modifier'() {
      when:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).not().equalTo('John')
            .build()

      then:
      query.restrictions.size() == 1
   }

   def "should use custom pagination and set offset and limit on query"() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).equalTo('John')
            .build()
      def pagination = new CustomPagination(2, 2)

      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, pagination)

      then:
      result == null
   }

   def "should not add restriction for null or empty values with optionally()"() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).optionally().equalTo(null)
            .property(DummyDatabaseEntity::getId).optionally().in([])
            .build()

      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, Pagination.all())

      then: "No restriction is added, so all entities are returned"
      result.size() == 17
   }

   def "should not add restrictions if values are not Optional but optionally() method is on"() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).optionally().equalTo('John')
            .property(DummyDatabaseEntity::getId).optionally().in([1L, 3L, 5L])
            .build()

      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, Pagination.all())

      then: "No restriction is added, so all entities are returned"
      result.size() == 17
   }

   def "should work with Optional in equalTo and in"() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).equalTo(Optional.of('John'))
            .property(DummyDatabaseEntity::getId).in(Optional.of([1L, 3L, 5L]))
            .build()

      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, Pagination.all())

      then:
      result*.id.sort() == [1L, 3L, 5L]
   }

   def "should skip restriction if Optional is empty in equalTo and in"() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getName).equalTo(Optional.empty())
            .property(DummyDatabaseEntity::getId).in(Optional.empty())
            .build()

      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, Pagination.all())

      then: "No restriction is added, so all entities are returned"
      result.size() == 17
   }

   def "should not add restriction for empty collection in in()"() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getId).in([])
            .build()

      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, Pagination.all())

      then: "No restriction is added, so all entities are returned"
      result.size() == 17
   }

   def "should not add restriction for Optional.empty() in in()"() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getId).in(Optional.empty())
            .build()

      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, Pagination.all())

      then: "No restriction is added, so all entities are returned"
      result.size() == 17
   }

   def "should not add restriction for present Optional with empty collection in in()"() {
      given:
      def query = DatabaseQuery.builder(DummyDatabaseEntity)
            .property(DummyDatabaseEntity::getId).in(Optional.of([]))
            .build()

      when:
      def result = new DatabaseQueryHandler<DummyDatabaseEntity>(entityManager)
            .handle(query, Pagination.all())

      then: "No restriction is added, so all entities are returned"
      result.size() == 17
   }

   private static class CustomPagination implements Pagination<DummyDatabaseEntity, List<DummyDatabaseEntity>> {
      private final int offset
      private final int limit

      CustomPagination(int offset, int limit) {
         this.offset = offset
         this.limit = limit
      }

      @Override
      List<DummyDatabaseEntity> expand(List<DummyDatabaseEntity> elements) {
         return null
      }

      @Override
      List<DummyDatabaseEntity> expandSingle(DummyDatabaseEntity element) {
         return null
      }

      @Override
      List<DummyDatabaseEntity> reduceEmpty() {
         return null
      }

      @Override
      PaginationType getType() {
         return PaginationType.LIST
      }

      @Override
      int getOffset() {
         return offset
      }

      @Override
      int getLimit() {
         return limit
      }
   }
}
