package com.filipmikolajzeglen.cqrs.hibernate.database;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;

class NoNegationStrategy implements NegationStrategy
{
   @Override
   public Predicate apply(CriteriaBuilder cb, Predicate predicate)
   {
      return predicate;
   }
}