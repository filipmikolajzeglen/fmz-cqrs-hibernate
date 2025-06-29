package com.filipmikolajzeglen.cqrs.hibernate.database;

import java.util.Collection;
import java.util.Objects;
import java.util.function.BiFunction;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.perfectable.introspection.FunctionalReference;

public class PropertyBuilder<ENTITY, PROPERTY>
{
   private final DatabaseQuery.Builder<ENTITY> parent;
   private final AccessorStrategy<ENTITY> accessorStrategy;
   private final NegationStrategy negationStrategy;

   public PropertyBuilder(DatabaseQuery.Builder<ENTITY> parent, Getter<ENTITY, PROPERTY> accessor)
   {
      this(parent, AccessorStrategy.byGetter(accessor), NegationStrategy.INITIAL);
   }

   public PropertyBuilder(DatabaseQuery.Builder<ENTITY> parent, String rawAccessor)
   {
      this(parent, AccessorStrategy.byName(rawAccessor), NegationStrategy.INITIAL);
   }

   private PropertyBuilder(DatabaseQuery.Builder<ENTITY> parent,
         AccessorStrategy<ENTITY> accessorStrategy, NegationStrategy negationStrategy)
   {
      this.parent = parent;
      this.accessorStrategy = accessorStrategy;
      this.negationStrategy = negationStrategy;
   }

   public PropertyBuilder<ENTITY, PROPERTY> not()
   {
      return new PropertyBuilder<>(parent, accessorStrategy, negationStrategy.negate());
   }

   public DatabaseQuery.Builder<ENTITY> equalTo(PROPERTY property)
   {
      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = accessorStrategy.resolve(root);
         return criteriaBuilder.equal(path, property);
      });
   }

   public DatabaseQuery.Builder<ENTITY> isNull()
   {
      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = accessorStrategy.resolve(root);
         return criteriaBuilder.isNull(path);
      });
   }

   public DatabaseQuery.Builder<ENTITY> isNotNull()
   {
      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = accessorStrategy.resolve(root);
         return criteriaBuilder.isNotNull(path);
      });
   }

   public DatabaseQuery.Builder<ENTITY> in(Collection<PROPERTY> properties)
   {
      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = accessorStrategy.resolve(root);
         Collection<PROPERTY> filtered = properties.stream()
               .filter(Objects::nonNull)
               .toList();

         Predicate predicate = path.in(filtered);

         if (properties.contains(null))
         {
            predicate = criteriaBuilder.or(predicate, criteriaBuilder.isNull(path));
         }

         return predicate;
      });
   }

   private DatabaseQuery.Builder<ENTITY> addRestriction(
         BiFunction<CriteriaBuilder, Root<ENTITY>, Predicate> predicateFn)
   {
      parent.withRestriction((criteriaBuilder, root) -> {
         Predicate rawPredicate = predicateFn.apply(criteriaBuilder, root);
         return negationStrategy.apply(criteriaBuilder, rawPredicate);
      });

      return parent;
   }

   @FunctionalInterface
   public interface Getter<E, V> extends FunctionalReference
   {
      V get(E entity);
   }
}