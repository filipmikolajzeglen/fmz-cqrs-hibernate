package com.filipmikolajzeglen.cqrs.hibernate.database;

import java.util.List;

import com.filipmikolajzeglen.cqrs.common.PagedResultPagination;
import com.filipmikolajzeglen.cqrs.common.Pagination;
import com.filipmikolajzeglen.cqrs.common.QueryHandler;
import com.filipmikolajzeglen.cqrs.common.SliceResult;
import com.filipmikolajzeglen.cqrs.common.SliceResultPagination;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DatabaseQueryHandler<ENTITY> implements QueryHandler<DatabaseQuery<ENTITY>, ENTITY>
{
   @PersistenceContext
   private final EntityManager entityManager;

   @Override
   public <PAGE> PAGE handle(DatabaseQuery<ENTITY> query, Pagination<ENTITY, PAGE> pagination)
   {
      CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
      return resolveStrategy(pagination).handle(this, query, pagination, criteriaBuilder);
   }

   private static <ENTITY, PAGE> PaginationStrategy<ENTITY, PAGE> resolveStrategy(Pagination<ENTITY, PAGE> pagination)
   {
      String simpleName = pagination.getClass().getSimpleName();
      return switch (simpleName)
      {
         case "ExistPagination" -> PaginationStrategy.exist();
         case "CountPagination" -> PaginationStrategy.count();
         case "FirstPagination" -> PaginationStrategy.first();
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

   @SuppressWarnings({ "unchecked", "DataFlowIssue" })
   private <PAGE> PAGE handleDefault(DatabaseQuery<ENTITY> query, Pagination<ENTITY, PAGE> pagination,
         CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<ENTITY> criteriaQuery = buildCriteriaQuery(query, criteriaBuilder);
      Pagination.Pageable pageable = pagination.asPageable().orElse(null);
      TypedQuery<ENTITY> typedQuery = prepareTypedQuery(criteriaQuery, pageable);

      List<ENTITY> results = typedQuery.getResultList();

      if (isPaged(pageable))
      {
         return handlePaged(query, pagination, criteriaBuilder, results);
      }
      if (pagination instanceof SliceResultPagination<?> slice)
      {
         return (PAGE) handleSlice(slice, results);
      }
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

   @SuppressWarnings("ConstantValue")
   private TypedQuery<ENTITY> prepareTypedQuery(CriteriaQuery<ENTITY> criteriaQuery, Pagination.Pageable pageable)
   {
      TypedQuery<ENTITY> typedQuery = entityManager.createQuery(criteriaQuery);
      if (pageable != null)
      {
         typedQuery.setFirstResult(pageable.offset());
         typedQuery.setMaxResults(pageable.requireTotalCount() ? pageable.limit() : pageable.limit() + 1);
      }
      return typedQuery;
   }

   @SuppressWarnings("ConstantValue")
   private boolean isPaged(Pagination.Pageable pageable)
   {
      return pageable != null && pageable.requireTotalCount();
   }

   @SuppressWarnings("unchecked")
   private <PAGE> PAGE handlePaged(DatabaseQuery<ENTITY> query, Pagination<ENTITY, PAGE> pagination,
         CriteriaBuilder criteriaBuilder, List<ENTITY> results)
   {
      long totalCount = countTotal(query, criteriaBuilder);
      PagedResultPagination<?> paged = (PagedResultPagination<?>) pagination;
      Pagination<ENTITY, PAGE> newPagination = (Pagination<ENTITY, PAGE>) new PagedResultPagination<>(
            paged.getPage(), paged.getSize(), (int) totalCount);
      return newPagination.expand(results);
   }

   private long countTotal(DatabaseQuery<ENTITY> query, CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<Long> countQuery = buildCountQuery(query, criteriaBuilder);
      return entityManager.createQuery(countQuery).getSingleResult();
   }

   @SuppressWarnings("TypeParameterHidesVisibleType")
   private <ENTITY> SliceResult<ENTITY> handleSlice(SliceResultPagination<?> slice, List<ENTITY> results)
   {
      int limit = slice.getLimit();
      boolean hasNext = results.size() > limit;
      List<ENTITY> content = hasNext ? results.subList(0, limit) : results;
      return new SliceResult<>(content, slice.getOffset(), limit, hasNext);
   }

   private sealed interface PaginationStrategy<ENTITY, PAGE>
         permits PaginationStrategy.ExistStrategy, PaginationStrategy.CountStrategy, PaginationStrategy.FirstStrategy,
         PaginationStrategy.DefaultStrategy
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