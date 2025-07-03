package com.filipmikolajzeglen.cqrs.persistence.database;

import java.util.List;

import com.filipmikolajzeglen.cqrs.core.Pagination;
import com.filipmikolajzeglen.cqrs.core.PaginationVisitor;
import com.filipmikolajzeglen.cqrs.core.PagedResult;
import com.filipmikolajzeglen.cqrs.core.QueryHandler;
import com.filipmikolajzeglen.cqrs.core.SliceResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

/**
 * Handles execution of {@link DatabaseQuery} using an {@link EntityManager} and supports various pagination
 * strategies.
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
      return pagination.accept(new JpaPaginationVisitor<>(this, query, criteriaBuilder), null);
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

   private PagedResult<ENTITY> handlePaged(DatabaseQuery<ENTITY> query,
         Pagination<ENTITY, PagedResult<ENTITY>> pagination, CriteriaBuilder cb)
   {
      CriteriaQuery<ENTITY> criteriaQuery = buildCriteriaQuery(query, cb);
      TypedQuery<ENTITY> typedQuery = entityManager.createQuery(criteriaQuery);
      int page = pagination.getPage();
      int size = pagination.getSize();
      typedQuery.setFirstResult(page * size);
      typedQuery.setMaxResults(size);
      List<ENTITY> results = typedQuery.getResultList();
      long totalCount = countTotal(query, cb);
      int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalCount / size);
      return new PagedResult<>(results, page, size, (int) totalCount, totalPages);
   }

   private long countTotal(DatabaseQuery<ENTITY> query, CriteriaBuilder criteriaBuilder)
   {
      CriteriaQuery<Long> countQuery = buildCountQuery(query, criteriaBuilder);
      return entityManager.createQuery(countQuery).getSingleResult();
   }

   private SliceResult<ENTITY> handleSlice(DatabaseQuery<ENTITY> query,
         Pagination<ENTITY, SliceResult<ENTITY>> pagination, CriteriaBuilder cb)
   {
      CriteriaQuery<ENTITY> criteriaQuery = buildCriteriaQuery(query, cb);
      TypedQuery<ENTITY> typedQuery = entityManager.createQuery(criteriaQuery);
      int offset = pagination.getOffset();
      int limit = pagination.getLimit();
      typedQuery.setFirstResult(offset);
      typedQuery.setMaxResults(limit + 1);
      List<ENTITY> results = typedQuery.getResultList();
      boolean hasNext = results.size() > limit;
      List<ENTITY> content = hasNext ? results.subList(0, limit) : results;
      return new SliceResult<>(content, offset, limit, hasNext);
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
         typedQuery.setMaxResults(limit);
      }
      catch (UnsupportedOperationException ignored)
      {
         // If pagination does not support offset and limit, we do not set them.
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

   private static class JpaPaginationVisitor<ENTITY, PAGE> implements PaginationVisitor<ENTITY, PAGE, PAGE>
   {
      private final DatabaseQueryHandler<ENTITY> handler;
      private final DatabaseQuery<ENTITY> query;
      private final CriteriaBuilder criteriaBuilder;

      JpaPaginationVisitor(DatabaseQueryHandler<ENTITY> handler, DatabaseQuery<ENTITY> query,
            CriteriaBuilder criteriaBuilder)
      {
         this.handler = handler;
         this.query = query;
         this.criteriaBuilder = criteriaBuilder;
      }

      @Override
      public PAGE visitSingle(Pagination<ENTITY, PAGE> pagination, PAGE page)
      {
         return handler.handleDefault(query, pagination, criteriaBuilder);
      }

      @Override
      public PAGE visitOptional(Pagination<ENTITY, PAGE> pagination, PAGE page)
      {
         return handler.handleDefault(query, pagination, criteriaBuilder);
      }

      @Override
      public PAGE visitList(Pagination<ENTITY, PAGE> pagination, PAGE page)
      {
         return handler.handleDefault(query, pagination, criteriaBuilder);
      }

      @Override
      public PAGE visitExist(Pagination<ENTITY, PAGE> pagination, PAGE page)
      {
         @SuppressWarnings("unchecked")
         PAGE result = (PAGE) handler.handleExist(query, criteriaBuilder);
         return result;
      }

      @Override
      public PAGE visitCount(Pagination<ENTITY, PAGE> pagination, PAGE page)
      {
         @SuppressWarnings("unchecked")
         PAGE result = (PAGE) handler.handleCount(query, criteriaBuilder);
         return result;
      }

      @Override
      public PAGE visitFirst(Pagination<ENTITY, PAGE> pagination, PAGE page)
      {
         return handler.handleFirst(query, pagination, criteriaBuilder);
      }

      @Override
      public PAGE visitPaged(Pagination<ENTITY, PAGE> pagination, PAGE page)
      {
         @SuppressWarnings("unchecked")
         PAGE result =
               (PAGE) handler.handlePaged(query, (Pagination<ENTITY, PagedResult<ENTITY>>) pagination, criteriaBuilder);
         return result;
      }

      @Override
      public PAGE visitSliced(Pagination<ENTITY, PAGE> pagination, PAGE page)
      {
         @SuppressWarnings("unchecked")
         PAGE result =
               (PAGE) handler.handleSlice(query, (Pagination<ENTITY, SliceResult<ENTITY>>) pagination, criteriaBuilder);
         return result;
      }
   }
}