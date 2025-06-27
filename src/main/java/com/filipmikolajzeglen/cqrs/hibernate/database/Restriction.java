package com.filipmikolajzeglen.cqrs.hibernate.database;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public interface Restriction<ENTITY>
{
   Predicate toPredicate(CriteriaBuilder criteriaBuilder, Root<ENTITY> root);
}