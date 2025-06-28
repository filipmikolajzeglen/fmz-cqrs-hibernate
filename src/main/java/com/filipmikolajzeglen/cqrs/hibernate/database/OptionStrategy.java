package com.filipmikolajzeglen.cqrs.hibernate.database;

import java.util.function.BiFunction;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

class OptionStrategy implements OptionalStrategy
{
   @Override
   public Predicate applySafely(BiFunction<CriteriaBuilder, Root<?>, Predicate> fn, CriteriaBuilder cb, Root<?> root)
   {
      try
      {
         return fn.apply(cb, root);
      }
      catch (Exception e)
      {
         return cb.conjunction();
      }
   }
}