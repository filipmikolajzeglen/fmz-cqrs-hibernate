package com.filipmikolajzeglen.cqrs.persistence.database;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;

sealed interface NegationStrategy permits NegationStrategy.NegatedStrategy, NegationStrategy.ForwardStrategy
{
   NegationStrategy INITIAL = ForwardStrategy.INSTANCE;

   Predicate apply(CriteriaBuilder cb, Predicate predicate);

   NegationStrategy negate();

   final class NegatedStrategy implements NegationStrategy
   {
      static final NegationStrategy INSTANCE = new NegatedStrategy();

      @Override
      public Predicate apply(CriteriaBuilder cb, Predicate predicate)
      {
         return cb.not(predicate);
      }

      @SuppressWarnings("ClassEscapesDefinedScope")
      @Override
      public NegationStrategy negate()
      {
         return ForwardStrategy.INSTANCE;
      }
   }

   final class ForwardStrategy implements NegationStrategy
   {
      static final NegationStrategy INSTANCE = new ForwardStrategy();

      @Override
      public Predicate apply(CriteriaBuilder cb, Predicate predicate)
      {
         return predicate;
      }

      @SuppressWarnings("ClassEscapesDefinedScope")
      @Override
      public NegationStrategy negate()
      {
         return NegatedStrategy.INSTANCE;
      }
   }
}