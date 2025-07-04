package com.filipmikolajzeglen.cqrs.persistence.database;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Represents a restriction that can be converted to a JPA {@link Predicate}.
 *
 * @param <ENTITY> the entity type
 */
interface Restriction<ENTITY>
{
   /**
    * Converts this restriction to a JPA predicate.
    *
    * @param criteriaBuilder the criteria builder
    * @param root            the root entity
    * @return the predicate representing this restriction
    */
   Predicate toPredicate(CriteriaBuilder criteriaBuilder, Root<ENTITY> root);
}