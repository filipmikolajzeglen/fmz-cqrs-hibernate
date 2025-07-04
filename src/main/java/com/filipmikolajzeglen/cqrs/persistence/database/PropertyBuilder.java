package com.filipmikolajzeglen.cqrs.persistence.database;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.perfectable.introspection.FunctionalReference;

/**
 * Builder for adding property-based restrictions to a {@link DatabaseQuery}.
 *
 * @param <ENTITY>   the entity type
 * @param <PROPERTY> the property type
 */
class PropertyBuilder<ENTITY, PROPERTY>
{
   /**
    * The parent query builder.
    */
   private final DatabaseQuery.Builder<ENTITY> parent;

   /**
    * Strategy for resolving the initial property path.
    */
   private final AccessorStrategy<ENTITY> accessorStrategy;

   /**
    * Strategy for negating restrictions.
    */
   private final NegationStrategy negationStrategy;

   /**
    * Strategy for handling optional restrictions.
    */
   private final OptionalityStrategy optionality;

   /**
    * List of strategies for resolving nested property paths (for then() chaining).
    */
   private final List<PathStepStrategy> pathStepStrategies;

   /**
    * Creates a property builder for a given property accessor.
    *
    * @param parent   the parent query builder
    * @param accessor the property accessor
    */
   public PropertyBuilder(DatabaseQuery.Builder<ENTITY> parent, Getter<ENTITY, PROPERTY> accessor)
   {
      this(parent, AccessorStrategy.byGetter(accessor), NegationStrategy.INITIAL, OptionalityStrategy.ALWAYS,
            List.of(PathStepStrategy.getter(accessor)));
   }

   /**
    * Creates a property builder for a given property name.
    *
    * @param parent      the parent query builder
    * @param rawAccessor the property name
    */
   public PropertyBuilder(DatabaseQuery.Builder<ENTITY> parent, String rawAccessor)
   {
      this(parent, AccessorStrategy.byName(rawAccessor), NegationStrategy.INITIAL, OptionalityStrategy.ALWAYS,
            List.of(PathStepStrategy.name(rawAccessor)));
   }

   /**
    * Internal constructor for advanced usage.
    */
   private PropertyBuilder(DatabaseQuery.Builder<ENTITY> parent,
         AccessorStrategy<ENTITY> accessorStrategy, NegationStrategy negationStrategy, OptionalityStrategy optionality)
   {
      this(parent, accessorStrategy, negationStrategy, optionality, new ArrayList<>());
   }

   /**
    * Internal constructor for advanced usage with path steps.
    */
   private PropertyBuilder(DatabaseQuery.Builder<ENTITY> parent,
         AccessorStrategy<ENTITY> accessorStrategy, NegationStrategy negationStrategy, OptionalityStrategy optionality,
         List<PathStepStrategy> pathStepStrategies)
   {
      this.parent = parent;
      this.accessorStrategy = accessorStrategy;
      this.negationStrategy = negationStrategy;
      this.optionality = optionality;
      this.pathStepStrategies = pathStepStrategies;
   }

   /**
    * Negates the next restriction.
    *
    * @return a new property builder with negation applied
    */
   public PropertyBuilder<ENTITY, PROPERTY> not()
   {
      return new PropertyBuilder<>(parent, accessorStrategy, negationStrategy.negate(), optionality);
   }

   /**
    * Enables optional restriction: restriction will be added only if the value is not null.
    *
    * @return a new property builder with an optionally flag set
    */
   public PropertyBuilder<ENTITY, PROPERTY> optionally()
   {
      return new PropertyBuilder<>(parent, accessorStrategy, negationStrategy, OptionalityStrategy.OPTIONAL);
   }

   /**
    * Adds an equality restriction for the property.
    *
    * @param property the value to compare
    * @return the parent builder
    */
   public DatabaseQuery.Builder<ENTITY> equalTo(PROPERTY property)
   {
      if (!optionality.shouldApply(property))
      {
         return parent;
      }
      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = resolvePath(root);
         return criteriaBuilder.equal(path, property);
      });
   }

   /**
    * Adds an equality restriction for the property using Optional.
    *
    * @param propertyOpt the optional value to compare
    * @return the parent builder
    */
   @SuppressWarnings({ "OptionalUsedAsFieldOrParameterType" })
   public DatabaseQuery.Builder<ENTITY> equalTo(Optional<PROPERTY> propertyOpt)
   {
      if (!optionality.shouldApply(propertyOpt) || propertyOpt.isEmpty())
      {
         return parent;
      }
      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = resolvePath(root);
         return criteriaBuilder.equal(path, propertyOpt.get());
      });
   }

   /**
    * Adds a restriction that the property is null.
    *
    * @return the parent builder
    */
   public DatabaseQuery.Builder<ENTITY> isNull()
   {
      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = resolvePath(root);
         return criteriaBuilder.isNull(path);
      });
   }

   /**
    * Adds a restriction that the property is not null.
    *
    * @return the parent builder
    */
   public DatabaseQuery.Builder<ENTITY> isNotNull()
   {
      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = resolvePath(root);
         return criteriaBuilder.isNotNull(path);
      });
   }

   /**
    * Adds an "in" restriction for the property.
    *
    * @param properties the collection of values
    * @return the parent builder
    */
   public DatabaseQuery.Builder<ENTITY> in(Collection<PROPERTY> properties)
   {
      if (!optionality.shouldApply(properties) || properties.isEmpty())
      {
         return parent;
      }
      return getEntityBuilder(properties);
   }

   /**
    * Adds an "in" restriction for the property using Optional. Restriction will be added only if the Optional is
    * present and the collection is not empty.
    *
    * @param propertiesOpt the optional collection of values
    * @return the parent builder
    */
   @SuppressWarnings({ "OptionalUsedAsFieldOrParameterType" })
   public DatabaseQuery.Builder<ENTITY> in(Optional<Collection<PROPERTY>> propertiesOpt)
   {
      if (!optionality.shouldApply(propertiesOpt) || propertiesOpt.isEmpty())
      {
         return parent;
      }
      Collection<PROPERTY> properties = propertiesOpt.get();
      if (properties.isEmpty())
      {
         return parent;
      }
      return getEntityBuilder(properties);
   }

   /**
    * Adds a restriction using a custom predicate for the given collection of properties.
    *
    * @param properties the collection of property values
    * @return the parent builder
    */
   private DatabaseQuery.Builder<ENTITY> getEntityBuilder(Collection<PROPERTY> properties)
   {
      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = resolvePath(root);
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

   /**
    * Adds a restriction using the provided predicate function.
    *
    * @param predicateFn the function to create a predicate
    * @return the parent builder
    */
   private DatabaseQuery.Builder<ENTITY> addRestriction(
         BiFunction<CriteriaBuilder, Root<ENTITY>, Predicate> predicateFn)
   {
      parent.withRestriction((criteriaBuilder, root) -> {
         Predicate rawPredicate = predicateFn.apply(criteriaBuilder, root);
         return negationStrategy.apply(criteriaBuilder, rawPredicate);
      });

      return parent;
   }

   /**
    * Functional interface for property getter references.
    *
    * @param <E> the entity type
    * @param <V> the property type
    */
   @FunctionalInterface
   public interface Getter<E, V> extends FunctionalReference
   {
      V get(E entity);
   }

   /**
    * Functional interface for property setter references.
    *
    * @param <E> the entity type
    * @param <V> the property type
    */
   @FunctionalInterface
   public interface Setter<E, V> extends FunctionalReference
   {
      void set(E entity, V value);
   }

   /**
    * Functional interface for restriction with value.
    *
    * @param <ENTITY> the entity type
    * @param <T>      the value type
    */
   @FunctionalInterface
   private interface RestrictionFunction<ENTITY, T>
   {
      Predicate apply(CriteriaBuilder cb, Root<ENTITY> root, T value);
   }

   /**
    * Strategy for resolving property access.
    */
   sealed interface AccessorStrategy<ENTITY>
   {
      /**
       * Resolves the property path from the root entity.
       *
       * @param root the root entity
       * @return the property path
       */
      Path<?> resolve(Root<ENTITY> root);

      /**
       * Creates an accessor strategy using a property getter.
       *
       * @param getter the property getter
       * @return the accessor strategy
       */
      static <ENTITY, PROPERTY> AccessorStrategy<ENTITY> byGetter(PropertyBuilder.Getter<ENTITY, PROPERTY> getter)
      {
         return new GetterAccessor<>(getter);
      }

      /**
       * Creates an accessor strategy using a property name.
       *
       * @param propertyName the property name
       * @return the accessor strategy
       */
      static <ENTITY> AccessorStrategy<ENTITY> byName(String propertyName)
      {
         return new RawAccessor<>(propertyName);
      }

      /**
       * Accessor strategy using a property getter.
       */
      final class GetterAccessor<ENTITY, PROPERTY> implements AccessorStrategy<ENTITY>
      {
         private final PropertyBuilder.Getter<ENTITY, PROPERTY> getter;

         public GetterAccessor(PropertyBuilder.Getter<ENTITY, PROPERTY> getter)
         {
            this.getter = getter;
         }

         @Override
         public Path<?> resolve(Root<ENTITY> root)
         {
            String propertyName = propertyNameFrom(getter);
            return root.get(propertyName);
         }

         private String propertyNameFrom(PropertyBuilder.Getter<ENTITY, PROPERTY> getter)
         {
            FunctionalReference.Introspection introspection = getter.introspect();
            String methodName = introspection.referencedMethod().getName();
            return Introspector.decapitalize(methodName.replaceFirst("^(get|is)", ""));
         }
      }

      /**
       * Accessor strategy using a property name.
       */
      final class RawAccessor<ENTITY> implements AccessorStrategy<ENTITY>
      {
         private final String path;

         public RawAccessor(String path)
         {
            this.path = path;
         }

         @Override
         public Path<?> resolve(Root<ENTITY> root)
         {
            return root.get(path);
         }
      }
   }

   /**
    * Strategy for negating restrictions.
    */
   sealed interface NegationStrategy
   {
      /**
       * The initial (non-negated) strategy.
       */
      NegationStrategy INITIAL = ForwardStrategy.INSTANCE;

      /**
       * Applies negation to the given predicate if needed.
       *
       * @param cb        the criteria builder
       * @param predicate the predicate to possibly negate
       * @return the (possibly negated) predicate
       */
      Predicate apply(CriteriaBuilder cb, Predicate predicate);

      /**
       * Returns the opposite negation strategy.
       *
       * @return the negated or non-negated strategy
       */
      NegationStrategy negate();

      /**
       * Negated strategy implementation.
       */
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

      /**
       * Forward (non-negated) strategy implementation.
       */
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

   /**
    * Strategy for handling optional restrictions.
    */
   sealed interface OptionalityStrategy
   {
      /**
       * Determines if the restriction should be applied for the given value.
       *
       * @param value the value to check (maybe Optional or direct value)
       * @return true if restriction should be applied, false otherwise
       */
      @SuppressWarnings("BooleanMethodIsAlwaysInverted")
      boolean shouldApply(Object value);

      OptionalityStrategy ALWAYS = new AlwaysStrategy();

      OptionalityStrategy OPTIONAL = new OptionalStrategy();

      final class AlwaysStrategy implements OptionalityStrategy
      {
         @Override
         public boolean shouldApply(Object value)
         {
            return true;
         }
      }

      final class OptionalStrategy implements OptionalityStrategy
      {
         @Override
         public boolean shouldApply(Object value)
         {
            return value instanceof Optional<?> opt && opt.isPresent();
         }
      }
   }

   /**
    * Adds a nested property step using a getter reference.
    *
    * @param getter the getter for the next property in the path
    * @return a new property builder for the nested property
    */
   public <NEXT> PropertyBuilder<ENTITY, NEXT> then(Getter<PROPERTY, NEXT> getter)
   {
      List<PathStepStrategy> newSteps = new ArrayList<>(this.pathStepStrategies);
      newSteps.add(PathStepStrategy.getter(getter));
      return new PropertyBuilder<>(parent, accessorStrategy, negationStrategy, optionality, newSteps);
   }

   /**
    * Adds a nested property step using a property name.
    *
    * @param propertyName the name of the next property in the path
    * @return a new property builder for the nested property
    */
   public PropertyBuilder<ENTITY, Object> then(String propertyName)
   {
      List<PathStepStrategy> newSteps = new ArrayList<>(this.pathStepStrategies);
      newSteps.add(PathStepStrategy.name(propertyName));
      return new PropertyBuilder<>(parent, accessorStrategy, negationStrategy, optionality, newSteps);
   }

   /**
    * Resolves the full property path, including all nested steps.
    *
    * @param root the root entity
    * @return the resolved property path
    */
   private Path<?> resolvePath(Root<ENTITY> root)
   {
      Path<?> path = accessorStrategy.resolve(root);
      if (!pathStepStrategies.isEmpty())
      {
         for (int i = 1; i < pathStepStrategies.size(); i++)
         {
            path = pathStepStrategies.get(i).apply(path);
         }
      }
      return path;
   }

   /**
    * Strategy for resolving a step in a nested property path.
    */
   private sealed interface PathStepStrategy
   {
      /**
       * Applies this step to the given path.
       *
       * @param from the current path
       * @return the next path
       */
      Path<?> apply(Path<?> from);

      /**
       * Creates a step using a getter reference.
       *
       * @param getter the getter
       * @return the path step strategy
       */
      static PathStepStrategy getter(Getter<?, ?> getter) {
         return new GetterPathStepStrategy(getter);
      }

      /**
       * Creates a step using a property name.
       *
       * @param propertyName the property name
       * @return the path step strategy
       */
      static PathStepStrategy name(String propertyName) {
         return new NamePathStepStrategy(propertyName);
      }

      final class GetterPathStepStrategy implements PathStepStrategy
      {
         private final Getter<?, ?> getter;

         GetterPathStepStrategy(Getter<?, ?> getter)
         {
            this.getter = getter;
         }

         @Override
         public Path<?> apply(Path<?> from)
         {
            String propertyName = propertyNameFrom(getter);
            return from.get(propertyName);
         }

         private String propertyNameFrom(Getter<?, ?> getter)
         {
            FunctionalReference.Introspection introspection = getter.introspect();
            String methodName = introspection.referencedMethod().getName();
            return Introspector.decapitalize(methodName.replaceFirst("^(get|is)", ""));
         }
      }

      final class NamePathStepStrategy implements PathStepStrategy
      {
         private final String propertyName;

         NamePathStepStrategy(String propertyName)
         {
            this.propertyName = propertyName;
         }

         @Override
         public Path<?> apply(Path<?> from)
         {
            return from.get(propertyName);
         }
      }
   }
}
