package com.filipmikolajzeglen.cqrs.persistence.database;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Represents a query that can be constrained by restrictions and converted to JPA predicates.
 *
 * @param <ENTITY> the entity type
 */
public interface ConstrainingQuery<ENTITY>
{
   /**
    * Converts the query constraints to an array of JPA {@link Predicate} objects.
    *
    * @param cb   the {@link CriteriaBuilder} used to construct predicates
    * @param root the root type in the form clause
    * @return an array of predicates representing the query constraints
    */
   Predicate[] toRestrictions(CriteriaBuilder cb, Root<ENTITY> root);
}