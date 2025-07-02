package com.filipmikolajzeglen.cqrs.persistence.database;

import java.util.List;

import com.filipmikolajzeglen.cqrs.core.PagedPagination;
import com.filipmikolajzeglen.cqrs.core.Pagination;
import com.filipmikolajzeglen.cqrs.core.QueryHandler;
import com.filipmikolajzeglen.cqrs.core.SliceResult;
import com.filipmikolajzeglen.cqrs.core.SlicePagination;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

/**
 * Handles execution of {@link DatabaseQuery} using an {@link EntityManager} and supports various pagination strategies.
 *
 * @param <ENTITY> the entity type
 */
@RequiredArgsConstructor
public class DatabaseQueryHandler<ENTITY> implements QueryHandler<DatabaseQuery<ENTITY>, ENTITY>
{
   private final EntityManager entityManager;

   /**
    * Handles the given database query with the specified pagination.
    *
    * @param query      the database query
    * @param pagination the pagination strategy
    * @param <PAGE>     the result page type
    * @return the paginated result
    */
   @Override
   public <PAGE> PAGE handle(DatabaseQuery<ENTITY> query, Pagination<ENTITY, PAGE> pagination)
   {
      CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
      return resolveStrategy(pagination).handle(this, query, pagination, criteriaBuilder);
   }

   private static <ENTITY, PAGE> PaginationStrategy<ENTITY, PAGE> resolveStrategy(Pagination<ENTITY, PAGE> pagination)
   {
      return switch (pagination.getType())
      {
         case EXIST -> PaginationStrategy.exist();
         case COUNT -> PaginationStrategy.count();
         case FIRST -> PaginationStrategy.first();
         case PAGED -> PaginationStrategy.paged();
         case SLICED -> PaginationStrategy.sliced();
         default -> PaginationStrategy.defaultStrategy();
      };
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

   private <PAGE> PAGE handleFirst(DatabaseQuery<ENTITY> query, Pagination<ENTITY, PAGE> pagination,
         CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<ENTITY> criteriaQuery = buildCriteriaQuery(query, criteriaBuilder);
      TypedQuery<ENTITY> typedQuery = entityManager.createQuery(criteriaQuery);
      typedQuery.setFirstResult(0);
      typedQuery.setMaxResults(1);
      List<ENTITY> results = typedQuery.getResultList();
      return pagination.expand(results);
   }

   @SuppressWarnings("unchecked")
   private <PAGE> PAGE handlePaged(DatabaseQuery<ENTITY> query, Pagination<ENTITY, PAGE> pagination, CriteriaBuilder cb)
   {
      CriteriaQuery<ENTITY> criteriaQuery = buildCriteriaQuery(query, cb);
      TypedQuery<ENTITY> typedQuery = entityManager.createQuery(criteriaQuery);
      List<ENTITY> results = typedQuery.getResultList();
      long totalCount = countTotal(query, cb);
      PagedPagination<?> paged = (PagedPagination<?>) pagination;
      Pagination<ENTITY, PAGE> newPagination = (Pagination<ENTITY, PAGE>) new PagedPagination<>(
            paged.getPage(), paged.getSize(), (int) totalCount);
      return newPagination.expand(results);
   }

   private long countTotal(DatabaseQuery<ENTITY> query, CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<Long> countQuery = buildCountQuery(query, criteriaBuilder);
      return entityManager.createQuery(countQuery).getSingleResult();
   }

   @SuppressWarnings("unchecked")
   private <PAGE> PAGE handleSlice(DatabaseQuery<ENTITY> query, Pagination<ENTITY, PAGE> pagination, CriteriaBuilder cb)
   {
      CriteriaQuery<ENTITY> criteriaQuery = buildCriteriaQuery(query, cb);
      TypedQuery<ENTITY> typedQuery = entityManager.createQuery(criteriaQuery);
      int offset = pagination.getOffset();
      int limit = pagination.getLimit();
      typedQuery.setFirstResult(offset);
      typedQuery.setMaxResults(limit + 1);
      List<ENTITY> results = typedQuery.getResultList();
      SlicePagination<?> slice = (SlicePagination<?>) pagination;
      int realLimit = slice.getLimit();
      boolean hasNext = results.size() > realLimit;
      List<ENTITY> content = hasNext ? results.subList(0, realLimit) : results;
      return (PAGE) new SliceResult<>(content, slice.getOffset(), realLimit, hasNext);
   }

   private <PAGE> PAGE handleDefault(DatabaseQuery<ENTITY> query, Pagination<ENTITY, PAGE> pagination,
         CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<ENTITY> criteriaQuery = buildCriteriaQuery(query, criteriaBuilder);
      TypedQuery<ENTITY> typedQuery = entityManager.createQuery(criteriaQuery);

      try
      {
         int offset = pagination.getOffset();
         int limit = pagination.getLimit();
         typedQuery.setFirstResult(offset);
         typedQuery.setMaxResults(limit + 1);
      }
      catch (UnsupportedOperationException ignored)
      {
         // If pagination does not support offset and limit, we do not set them.
         // This is useful for cases where pagination is not based on offsets, like slicing or counting.
      }

      List<ENTITY> results = typedQuery.getResultList();
      return pagination.expand(results);
   }

   private CriteriaQuery<ENTITY> buildCriteriaQuery(DatabaseQuery<ENTITY> query, CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<ENTITY> criteriaQuery = criteriaBuilder.createQuery(query.getEntityType());
      Root<ENTITY> root = criteriaQuery.from(query.getEntityType());
      Predicate[] predicates = query.toRestrictions(criteriaBuilder, root);
      criteriaQuery.select(root).where(predicates);
      return criteriaQuery;
   }

   private sealed interface PaginationStrategy<ENTITY, PAGE>
   {
      PAGE handle(DatabaseQueryHandler<ENTITY> handler, DatabaseQuery<ENTITY> query,
            Pagination<ENTITY, PAGE> pagination, CriteriaBuilder cb);

      static <ENTITY, PAGE> PaginationStrategy<ENTITY, PAGE> exist()
      {
         return ExistStrategy.instance();
      }

      static <ENTITY, PAGE> PaginationStrategy<ENTITY, PAGE> count()
      {
         return CountStrategy.instance();
      }

      static <ENTITY, PAGE> PaginationStrategy<ENTITY, PAGE> first()
      {
         return FirstStrategy.instance();
      }

      static <ENTITY, PAGE> PaginationStrategy<ENTITY, PAGE> paged()
      {
         return PagedStrategy.instance();
      }

      static <ENTITY, PAGE> PaginationStrategy<ENTITY, PAGE> sliced()
      {
         return SlicedStrategy.instance();
      }

      static <ENTITY, PAGE> PaginationStrategy<ENTITY, PAGE> defaultStrategy()
      {
         return DefaultStrategy.instance();
      }

      @SuppressWarnings("unchecked")
      final class ExistStrategy<ENTITY, PAGE> implements PaginationStrategy<ENTITY, PAGE>
      {
         private static final ExistStrategy<?, ?> INSTANCE = new ExistStrategy<>();

         static <E, P> ExistStrategy<E, P> instance()
         {
            return (ExistStrategy<E, P>) INSTANCE;
         }

         @Override
         public PAGE handle(DatabaseQueryHandler<ENTITY> handler, DatabaseQuery<ENTITY> query,
               Pagination<ENTITY, PAGE> pagination, CriteriaBuilder cb)
         {
            return (PAGE) handler.handleExist(query, cb);
         }
      }

      @SuppressWarnings("unchecked")
      final class CountStrategy<ENTITY, PAGE> implements PaginationStrategy<ENTITY, PAGE>
      {
         private static final CountStrategy<?, ?> INSTANCE = new CountStrategy<>();

         static <E, P> CountStrategy<E, P> instance()
         {
            return (CountStrategy<E, P>) INSTANCE;
         }

         @Override
         public PAGE handle(DatabaseQueryHandler<ENTITY> handler, DatabaseQuery<ENTITY> query,
               Pagination<ENTITY, PAGE> pagination, CriteriaBuilder cb)
         {
            return (PAGE) handler.handleCount(query, cb);
         }
      }

      final class FirstStrategy<ENTITY, PAGE> implements PaginationStrategy<ENTITY, PAGE>
      {
         private static final FirstStrategy<?, ?> INSTANCE = new FirstStrategy<>();

         @SuppressWarnings("unchecked")
         static <E, P> FirstStrategy<E, P> instance()
         {
            return (FirstStrategy<E, P>) INSTANCE;
         }

         @Override
         public PAGE handle(DatabaseQueryHandler<ENTITY> handler, DatabaseQuery<ENTITY> query,
               Pagination<ENTITY, PAGE> pagination, CriteriaBuilder cb)
         {
            return handler.handleFirst(query, pagination, cb);
         }
      }

      final class PagedStrategy<ENTITY, PAGE> implements PaginationStrategy<ENTITY, PAGE>
      {
         private static final PagedStrategy<?, ?> INSTANCE = new PagedStrategy<>();

         @SuppressWarnings("unchecked")
         static <E, P> PagedStrategy<E, P> instance()
         {
            return (PagedStrategy<E, P>) INSTANCE;
         }

         @Override
         public PAGE handle(DatabaseQueryHandler<ENTITY> handler, DatabaseQuery<ENTITY> query,
               Pagination<ENTITY, PAGE> pagination, CriteriaBuilder cb)
         {
            return handler.handlePaged(query, pagination, cb);
         }
      }

      final class SlicedStrategy<ENTITY, PAGE> implements PaginationStrategy<ENTITY, PAGE>
      {
         private static final SlicedStrategy<?, ?> INSTANCE = new SlicedStrategy<>();

         @SuppressWarnings("unchecked")
         static <E, P> SlicedStrategy<E, P> instance()
         {
            return (SlicedStrategy<E, P>) INSTANCE;
         }

         @Override
         public PAGE handle(DatabaseQueryHandler<ENTITY> handler, DatabaseQuery<ENTITY> query,
               Pagination<ENTITY, PAGE> pagination, CriteriaBuilder cb)
         {
            return handler.handleSlice(query, pagination, cb);
         }
      }

      final class DefaultStrategy<ENTITY, PAGE> implements PaginationStrategy<ENTITY, PAGE>
      {
         private static final DefaultStrategy<?, ?> INSTANCE = new DefaultStrategy<>();

         @SuppressWarnings("unchecked")
         static <E, P> DefaultStrategy<E, P> instance()
         {
            return (DefaultStrategy<E, P>) INSTANCE;
         }

         @Override
         public PAGE handle(DatabaseQueryHandler<ENTITY> handler, DatabaseQuery<ENTITY> query,
               Pagination<ENTITY, PAGE> pagination, CriteriaBuilder cb)
         {
            return handler.handleDefault(query, pagination, cb);
         }
      }
   }
}