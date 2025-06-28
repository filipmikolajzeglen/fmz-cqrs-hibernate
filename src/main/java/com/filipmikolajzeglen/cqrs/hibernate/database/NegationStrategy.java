package com.filipmikolajzeglen.cqrs.hibernate.database;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;

interface NegationStrategy
{
   Predicate apply(CriteriaBuilder cb, Predicate predicate);
}