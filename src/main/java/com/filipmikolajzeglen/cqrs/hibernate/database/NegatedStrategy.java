package com.filipmikolajzeglen.cqrs.hibernate.database;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;

class NegatedStrategy implements NegationStrategy
{
   @Override
   public Predicate apply(CriteriaBuilder cb, Predicate predicate)
   {
      return cb.not(predicate);
   }
}