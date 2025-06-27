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

   @SuppressWarnings("unchecked")
   @Override
   public <PAGE> PAGE handle(DatabaseQuery<ENTITY> query, Pagination<ENTITY, PAGE> pagination)
   {
      CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

      if (isExistPagination(pagination))
      {
         return (PAGE) handleExist(query, criteriaBuilder);
      }

      if (isCountPagination(pagination))
      {
         return (PAGE) handleCount(query, criteriaBuilder);
      }

      if (isFirstPagination(pagination))
      {
         return handleFirst(query, pagination, criteriaBuilder);
      }

      return handleDefault(query, pagination, criteriaBuilder);
   }

   private boolean isExistPagination(Pagination<?, ?> pagination)
   {
      return pagination.getClass().getSimpleName().equals("ExistPagination");
   }

   private Boolean handleExist(DatabaseQuery<ENTITY> query, CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<Long> countQuery = buildCountQuery(query, criteriaBuilder);
      Long count = entityManager.createQuery(countQuery).setMaxResults(1).getSingleResult();
      return count > 0;
   }

   private boolean isCountPagination(Pagination<?, ?> pagination)
   {
      return pagination.getClass().getSimpleName().equals("CountPagination");
   }

   private Long handleCount(DatabaseQuery<ENTITY> query, CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<Long> countQuery = buildCountQuery(query, criteriaBuilder);
      return entityManager.createQuery(countQuery).getSingleResult();
   }

   private boolean isFirstPagination(Pagination<?, ?> pagination)
   {
      return pagination.getClass().getSimpleName().equals("FirstPagination");
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

   private CriteriaQuery<Long> buildCountQuery(DatabaseQuery<ENTITY> query, CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
      Root<ENTITY> root = countQuery.from(query.getEntityType());
      List<Predicate> predicates = query.getRestrictions().stream()
            .map(r -> r.toPredicate(criteriaBuilder, root))
            .toList();
      countQuery.select(criteriaBuilder.count(root)).where(predicates.toArray(new Predicate[0]));
      return countQuery;
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
      List<Predicate> predicates = query.getRestrictions().stream()
            .map(r -> r.toPredicate(criteriaBuilder, root))
            .toList();
      criteriaQuery.select(root).where(predicates.toArray(new Predicate[0]));
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
      if (pagination instanceof PagedResultPagination<?> paged)
      {
         Pagination<ENTITY, PAGE> newPagination =
               (Pagination<ENTITY, PAGE>) new PagedResultPagination<>(
                     paged.getPage(), paged.getSize(), (int) totalCount);
         return newPagination.expand(results);
      }
      return pagination.expand(results);
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
}