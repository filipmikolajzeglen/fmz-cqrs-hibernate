package com.filipmikolajzeglen.cqrs.persistence.database;

import java.util.ArrayList;
import java.util.List;

import com.filipmikolajzeglen.cqrs.core.OrderedResultStrategy;
import com.filipmikolajzeglen.cqrs.core.PagedResult;
import com.filipmikolajzeglen.cqrs.core.QueryHandler;
import com.filipmikolajzeglen.cqrs.core.ResultStrategy;
import com.filipmikolajzeglen.cqrs.core.ResultStrategyVisitor;
import com.filipmikolajzeglen.cqrs.core.SliceResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

/**
 * Handles execution of {@link DatabaseQuery} using an {@link EntityManager} and supports various result strategies,
 * including sorting.
 * <p>
 * If the provided result strategy implements {@link com.filipmikolajzeglen.cqrs.core.OrderedResultStrategy} and
 * contains sort orders, those will be used to sort the results. Otherwise, results are sorted by the "id" property in
 * ascending order by default.
 * </p>
 *
 * @param <ENTITY> the entity type
 */
@RequiredArgsConstructor
public class DatabaseQueryHandler<ENTITY> implements QueryHandler<DatabaseQuery<ENTITY>, ENTITY>
{
   private final EntityManager entityManager;

   /**
    * Handles the given database query with the specified result strategy and sorting.
    * <p>
    * If the result strategy supports sorting and sort orders are provided, results will be sorted accordingly. Otherwise,
    * results are sorted by the "id" property in ascending order.
    * </p>
    *
    * @param query          the database query
    * @param resultStrategy the result strategy (may support sorting)
    * @param <RESULT>       the result type
    * @return the result (possibly sorted)
    */
   @Override
   public <RESULT> RESULT handle(DatabaseQuery<ENTITY> query, ResultStrategy<ENTITY, RESULT> resultStrategy)
   {
      CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
      return resultStrategy.accept(new JpaResultStrategyVisitor<>(this, query, criteriaBuilder), null);
   }

   private Boolean handleExist(DatabaseQuery<ENTITY> query, CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<Long> countQuery = buildCountQuery(query, criteriaBuilder);
      Long count = entityManager.createQuery(countQuery).setMaxResults(1).getSingleResult();
      return count > 0;
   }

   private Long handleCount(DatabaseQuery<ENTITY> query, CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<Long> countQuery = buildCountQuery(query, criteriaBuilder);
      return entityManager.createQuery(countQuery).getSingleResult();
   }

   private CriteriaQuery<Long> buildCountQuery(DatabaseQuery<ENTITY> query, CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
      Root<ENTITY> root = countQuery.from(query.getEntityType());
      Predicate[] predicates = query.toRestrictions(criteriaBuilder, root);
      countQuery.select(criteriaBuilder.count(root)).where(predicates);
      return countQuery;
   }

   private <RESULT> RESULT handleFirst(DatabaseQuery<ENTITY> query, ResultStrategy<ENTITY, RESULT> resultStrategy,
         CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<ENTITY> criteriaQuery = buildCriteriaQuery(query, criteriaBuilder, resultStrategy);
      TypedQuery<ENTITY> typedQuery = entityManager.createQuery(criteriaQuery);
      typedQuery.setFirstResult(0);
      typedQuery.setMaxResults(1);
      List<ENTITY> results = typedQuery.getResultList();
      return resultStrategy.expand(results);
   }

   private PagedResult<ENTITY> handlePaged(DatabaseQuery<ENTITY> query,
         ResultStrategy<ENTITY, PagedResult<ENTITY>> resultStrategy, CriteriaBuilder cb)
   {
      CriteriaQuery<ENTITY> criteriaQuery = buildCriteriaQuery(query, cb, resultStrategy);
      TypedQuery<ENTITY> typedQuery = entityManager.createQuery(criteriaQuery);
      int result = resultStrategy.getPage();
      int size = resultStrategy.getSize();
      typedQuery.setFirstResult(result * size);
      typedQuery.setMaxResults(size);
      List<ENTITY> results = typedQuery.getResultList();
      long totalCount = countTotal(query, cb);
      int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalCount / size);
      return new PagedResult<>(results, result, size, (int) totalCount, totalPages);
   }

   private long countTotal(DatabaseQuery<ENTITY> query, CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<Long> countQuery = buildCountQuery(query, criteriaBuilder);
      return entityManager.createQuery(countQuery).getSingleResult();
   }

   private SliceResult<ENTITY> handleSlice(DatabaseQuery<ENTITY> query,
         ResultStrategy<ENTITY, SliceResult<ENTITY>> resultStrategy, CriteriaBuilder cb)
   {
      CriteriaQuery<ENTITY> criteriaQuery = buildCriteriaQuery(query, cb, resultStrategy);
      TypedQuery<ENTITY> typedQuery = entityManager.createQuery(criteriaQuery);
      int offset = resultStrategy.getOffset();
      int limit = resultStrategy.getLimit();
      typedQuery.setFirstResult(offset);
      typedQuery.setMaxResults(limit + 1);
      List<ENTITY> results = typedQuery.getResultList();
      boolean hasNext = results.size() > limit;
      List<ENTITY> content = hasNext ? results.subList(0, limit) : results;
      return new SliceResult<>(content, offset, limit, hasNext);
   }

   private <RESULT> RESULT handleDefault(DatabaseQuery<ENTITY> query, ResultStrategy<ENTITY, RESULT> resultStrategy,
         CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<ENTITY> criteriaQuery = buildCriteriaQuery(query, criteriaBuilder, resultStrategy);
      TypedQuery<ENTITY> typedQuery = entityManager.createQuery(criteriaQuery);

      try
      {
         int offset = resultStrategy.getOffset();
         int limit = resultStrategy.getLimit();
         typedQuery.setFirstResult(offset);
         typedQuery.setMaxResults(limit);
      }
      catch (UnsupportedOperationException ignored)
      {
         // If resultStrategy does not support offset and limit, we do not set them.
      }

      List<ENTITY> results = typedQuery.getResultList();
      return resultStrategy.expand(results);
   }

   /**
    * Builds a JPA CriteriaQuery for the given query, criteria builder and result strategy.
    * <p>
    * If the result strategy supports sorting and sort orders are provided, those will be used. Otherwise, results are
    * sorted by the "id" property in ascending order.
    * </p>
    *
    * @param query           the database query
    * @param criteriaBuilder the JPA criteria builder
    * @param resultStrategy  the result strategy (may support sorting)
    * @return the criteria query with applied restrictions and sorting
    */
   private CriteriaQuery<ENTITY> buildCriteriaQuery(DatabaseQuery<ENTITY> query, CriteriaBuilder criteriaBuilder,
         ResultStrategy<ENTITY, ?> resultStrategy)
   {
      CriteriaQuery<ENTITY> criteriaQuery = criteriaBuilder.createQuery(query.getEntityType());
      Root<ENTITY> root = criteriaQuery.from(query.getEntityType());
      Predicate[] predicates = query.toRestrictions(criteriaBuilder, root);
      criteriaQuery.select(root).where(predicates);

      OrderStrategy<ENTITY> orderStrategy =
            (resultStrategy instanceof OrderedResultStrategy<?, ?> sortable && !sortable.getOrders().isEmpty())
                  ? new OrderStrategy.ProvidedSortOrderStrategy<>(sortable.getOrders())
                  : new OrderStrategy.DefaultOrderStrategy<>();

      criteriaQuery.orderBy(orderStrategy.buildOrders(root, criteriaBuilder));
      return criteriaQuery;
   }

   private static class JpaResultStrategyVisitor<ENTITY, RESULT>
         implements ResultStrategyVisitor<ENTITY, RESULT, RESULT>
   {
      private final DatabaseQueryHandler<ENTITY> handler;
      private final DatabaseQuery<ENTITY> query;
      private final CriteriaBuilder criteriaBuilder;

      JpaResultStrategyVisitor(DatabaseQueryHandler<ENTITY> handler, DatabaseQuery<ENTITY> query,
            CriteriaBuilder criteriaBuilder)
      {
         this.handler = handler;
         this.query = query;
         this.criteriaBuilder = criteriaBuilder;
      }

      @Override
      public RESULT visitSingle(ResultStrategy<ENTITY, RESULT> resultStrategy, RESULT result)
      {
         return handler.handleDefault(query, resultStrategy, criteriaBuilder);
      }

      @Override
      public RESULT visitOptional(ResultStrategy<ENTITY, RESULT> resultStrategy, RESULT result)
      {
         return handler.handleDefault(query, resultStrategy, criteriaBuilder);
      }

      @Override
      public RESULT visitList(ResultStrategy<ENTITY, RESULT> resultStrategy, RESULT result)
      {
         return handler.handleDefault(query, resultStrategy, criteriaBuilder);
      }

      @Override
      public RESULT visitExist(ResultStrategy<ENTITY, RESULT> resultStrategy, RESULT result)
      {
         //noinspection unchecked
         return (RESULT) handler.handleExist(query, criteriaBuilder);
      }

      @Override
      public RESULT visitCount(ResultStrategy<ENTITY, RESULT> resultStrategy, RESULT result)
      {
         //noinspection unchecked
         return (RESULT) handler.handleCount(query, criteriaBuilder);
      }

      @Override
      public RESULT visitFirst(ResultStrategy<ENTITY, RESULT> resultStrategy, RESULT result)
      {
         return handler.handleFirst(query, resultStrategy, criteriaBuilder);
      }

      @Override
      public RESULT visitPaged(ResultStrategy<ENTITY, RESULT> resultStrategy, RESULT result)
      {
         //noinspection unchecked
         return (RESULT) handler.handlePaged(query, (ResultStrategy<ENTITY, PagedResult<ENTITY>>) resultStrategy,
               criteriaBuilder);
      }

      @Override
      public RESULT visitSliced(ResultStrategy<ENTITY, RESULT> resultStrategy, RESULT result)
      {
         //noinspection unchecked
         return (RESULT) handler.handleSlice(query, (ResultStrategy<ENTITY, SliceResult<ENTITY>>) resultStrategy,
               criteriaBuilder);
      }
   }

   private sealed interface OrderStrategy<ENTITY>
   {
      List<Order> buildOrders(Root<ENTITY> root, CriteriaBuilder cb);

      final class ProvidedSortOrderStrategy<ENTITY> implements OrderStrategy<ENTITY>
      {
         private final List<com.filipmikolajzeglen.cqrs.core.Order> orders;

         ProvidedSortOrderStrategy(List<com.filipmikolajzeglen.cqrs.core.Order> orders)
         {
            this.orders = orders;
         }

         @Override
         public List<Order> buildOrders(Root<ENTITY> root, CriteriaBuilder cb)
         {
            List<Order> orders = new ArrayList<>();
            for (com.filipmikolajzeglen.cqrs.core.Order order : this.orders)
            {
               orders.add(order.getDirection() == com.filipmikolajzeglen.cqrs.core.Order.Direction.ASC
                     ? cb.asc(root.get(order.getProperty()))
                     : cb.desc(root.get(order.getProperty())));
            }
            return orders;
         }
      }

      final class DefaultOrderStrategy<ENTITY> implements OrderStrategy<ENTITY>
      {
         @Override
         public List<Order> buildOrders(Root<ENTITY> root, CriteriaBuilder cb)
         {
            return List.of(cb.asc(root.get("id")));
         }
      }
   }
}
