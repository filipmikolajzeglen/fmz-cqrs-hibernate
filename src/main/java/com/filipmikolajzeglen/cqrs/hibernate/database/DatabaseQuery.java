package com.filipmikolajzeglen.cqrs.hibernate.database;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import com.filipmikolajzeglen.cqrs.common.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class DatabaseQuery<ENTITY> extends Query<ENTITY>
{
   private final Class<ENTITY> entityType;

   private final List<Restriction<ENTITY>> restrictions;

   public static <ENTITY> Builder<ENTITY> builder(Class<ENTITY> entityType)
   {
      return new Builder<>(entityType);
   }

   public static final class Builder<ENTITY>
   {
      private final Class<ENTITY> entityType;
      private final List<Restriction<ENTITY>> restrictions = new ArrayList<>();

      public Builder(Class<ENTITY> entityType)
      {
         this.entityType = entityType;
      }

      public <PROPERTY> PropertyBuilder<ENTITY, PROPERTY> property(
            PropertyBuilder.Getter<ENTITY, PROPERTY> propertyAccessor)
      {
         return new PropertyBuilder<>(this, propertyAccessor);
      }

      public <PROPERTY> PropertyBuilder<ENTITY, PROPERTY> property(String propertyAccessor)
      {
         return new PropertyBuilder<>(this, propertyAccessor);
      }

      public Builder<ENTITY> withRestriction(BiFunction<CriteriaBuilder, Root<ENTITY>, Predicate> restrictionFn)
      {
         restrictions.add(restrictionFn::apply);
         return this;
      }

      public DatabaseQuery<ENTITY> build()
      {
         return new DatabaseQuery<>(entityType, restrictions);
      }
   }
}