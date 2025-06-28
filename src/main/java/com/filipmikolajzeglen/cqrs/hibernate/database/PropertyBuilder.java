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
   private final PathResolverStrategy<ENTITY> pathResolverStrategy;
   private final OptionalStrategy optionalStrategy;
   private final NegationStrategy negationStrategy;

   public PropertyBuilder(DatabaseQuery.Builder<ENTITY> parent, Getter<ENTITY, PROPERTY> accessor)
   {
      this(parent, new GetterPathResolver<>(accessor), new RequiredStrategy(), new NoNegationStrategy());
   }

   public PropertyBuilder(DatabaseQuery.Builder<ENTITY> parent, String rawAccessor)
   {
      this(parent, new RawPathResolver<>(rawAccessor), new RequiredStrategy(), new NoNegationStrategy());
   }

   private PropertyBuilder(DatabaseQuery.Builder<ENTITY> parent,
         PathResolverStrategy<ENTITY> pathResolverStrategy,
         OptionalStrategy optionalStrategy,
         NegationStrategy negationStrategy)
   {
      this.parent = parent;
      this.pathResolverStrategy = pathResolverStrategy;
      this.optionalStrategy = optionalStrategy;
      this.negationStrategy = negationStrategy;
   }

   public PropertyBuilder<ENTITY, PROPERTY> optionally()
   {
      return new PropertyBuilder<>(parent, pathResolverStrategy, new OptionStrategy(), negationStrategy);
   }

   public PropertyBuilder<ENTITY, PROPERTY> not()
   {
      return new PropertyBuilder<>(parent, pathResolverStrategy, optionalStrategy, new NegatedStrategy());
   }

   public DatabaseQuery.Builder<ENTITY> equalTo(PROPERTY property)
   {
      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = pathResolverStrategy.resolve(root);
         return criteriaBuilder.equal(path, property);
      });
   }

   public DatabaseQuery.Builder<ENTITY> isNull()
   {
      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = pathResolverStrategy.resolve(root);
         return criteriaBuilder.isNull(path);
      });
   }

   public DatabaseQuery.Builder<ENTITY> isNotNull()
   {
      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = pathResolverStrategy.resolve(root);
         return criteriaBuilder.isNotNull(path);
      });
   }

   public DatabaseQuery.Builder<ENTITY> in(Collection<PROPERTY> properties)
   {
      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = pathResolverStrategy.resolve(root);
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
         Predicate negated = negationStrategy.apply(criteriaBuilder, rawPredicate);
         return optionalStrategy.applySafely((cb, r) -> negated, criteriaBuilder, root);
      });

      return parent;
   }

   @FunctionalInterface
   public interface Getter<E, V> extends FunctionalReference
   {
      V get(E entity);
   }
}