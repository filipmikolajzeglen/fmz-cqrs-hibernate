package com.filipmikolajzeglen.cqrs.persistence.database;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public interface ConstrainingQuery<ENTITY>
{
   Predicate[] toRestrictions(CriteriaBuilder cb, Root<ENTITY> root);
}