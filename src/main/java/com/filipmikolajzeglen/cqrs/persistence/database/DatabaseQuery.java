package com.filipmikolajzeglen.cqrs.persistence.database;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import com.filipmikolajzeglen.cqrs.core.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a database query with restrictions for a specific entity type.
 *
 * @param <ENTITY> the entity type
 */
@Getter
@RequiredArgsConstructor
public class DatabaseQuery<ENTITY> extends Query<ENTITY> implements ConstrainingQuery<ENTITY>
{
   private final Class<ENTITY> entityType;

   private final List<Restriction<ENTITY>> restrictions;

   /**
    * Converts the query restrictions to an array of JPA predicates.
    *
    * @param cb   the criteria builder
    * @param root the root entity
    * @return an array of predicates
    */
   @Override
   public Predicate[] toRestrictions(CriteriaBuilder cb, Root<ENTITY> root)
   {
      return restrictions.stream()
            .map(r -> r.toPredicate(cb, root))
            .toArray(Predicate[]::new);
   }

   /**
    * Creates a new builder for the given entity type.
    *
    * @param entityType the entity class
    * @param <ENTITY>   the entity type
    * @return a new builder
    */
   public static <ENTITY> Builder<ENTITY> builder(Class<ENTITY> entityType)
   {
      return new Builder<>(entityType);
   }

   /**
    * Builder for {@link DatabaseQuery}.
    *
    * @param <ENTITY> the entity type
    */
   public static final class Builder<ENTITY>
   {
      private final Class<ENTITY> entityType;
      private final List<Restriction<ENTITY>> restrictions = new ArrayList<>();

      public Builder(Class<ENTITY> entityType)
      {
         this.entityType = entityType;
      }

      /**
       * Starts building a restriction for a property using a property accessor.
       *
       * @param propertyAccessor the property accessor
       * @param <PROPERTY>       the property type
       * @return a property builder
       */
      public <PROPERTY> PropertyBuilder<ENTITY, PROPERTY> property(
            PropertyBuilder.Getter<ENTITY, PROPERTY> propertyAccessor)
      {
         return new PropertyBuilder<>(this, propertyAccessor);
      }

      /**
       * Starts building a restriction for a property using a property name.
       *
       * @param propertyAccessor the property name
       * @param <PROPERTY>       the property type
       * @return a property builder
       */
      public <PROPERTY> PropertyBuilder<ENTITY, PROPERTY> property(String propertyAccessor)
      {
         return new PropertyBuilder<>(this, propertyAccessor);
      }

      /**
       * Adds a custom restriction to the query.
       *
       * @param restrictionFn the restriction function
       * @return this builder
       */
      public Builder<ENTITY> withRestriction(BiFunction<CriteriaBuilder, Root<ENTITY>, Predicate> restrictionFn)
      {
         restrictions.add(restrictionFn::apply);
         return this;
      }

      /**
       * Builds the {@link DatabaseQuery} instance.
       *
       * @return the database query
       */
      public DatabaseQuery<ENTITY> build()
      {
         return new DatabaseQuery<>(entityType, restrictions);
      }
   }
}