package com.filipmikolajzeglen.cqrs.hibernate.database;

import java.beans.Introspector;
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
   private final Getter<ENTITY, PROPERTY> accessor;
   private final String rawAccessor;
   private boolean optional = false;
   private boolean negated = false;

   public PropertyBuilder(DatabaseQuery.Builder<ENTITY> parent, Getter<ENTITY, PROPERTY> accessor)
   {
      this.parent = parent;
      this.accessor = accessor;
      this.rawAccessor = null;
   }

   public PropertyBuilder(DatabaseQuery.Builder<ENTITY> parent, String accessor)
   {
      this.parent = parent;
      this.accessor = null;
      this.rawAccessor = accessor;
   }

   public PropertyBuilder<ENTITY, PROPERTY> optionally()
   {
      this.optional = true;
      return this;
   }

   public PropertyBuilder<ENTITY, PROPERTY> not()
   {
      this.negated = true;
      return this;
   }

   //TODO: Stworzyć typ dla pustych wartości tak żeby nie używać nulla
   @SuppressWarnings("ConstantValue")
   public DatabaseQuery.Builder<ENTITY> equalTo(PROPERTY property)
   {
      if (optional && property == null)
      {
         return parent;
      }

      return addRestriction((criteriaBuilder, root) -> {
         Path<?> path = resolvePath(root);
         if (property == null)
         {
            return criteriaBuilder.isNull(path);
         }
         return criteriaBuilder.equal(path, property);
      });
   }

   public DatabaseQuery.Builder<ENTITY> in(Collection<PROPERTY> properties)
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

   private Path<?> resolvePath(Root<ENTITY> root)
   {
      if (rawAccessor != null)
      {
         return root.get(rawAccessor);
      }
      else
      {
         assert accessor != null;
         return root.get(propertyNameFrom(accessor));
      }
   }

   private String propertyNameFrom(Getter<ENTITY, PROPERTY> getter)
   {
      FunctionalReference.Introspection introspection = getter.introspect();
      String methodName = introspection.referencedMethod().getName();
      return Introspector.decapitalize(methodName.replaceFirst("^(get|is)", ""));
   }

   @FunctionalInterface
   public interface Getter<E, V> extends FunctionalReference
   {
      V get(E entity);
   }

   private DatabaseQuery.Builder<ENTITY> addRestriction(
         BiFunction<CriteriaBuilder, Root<ENTITY>, Predicate> predicateFn)
   {
      BiFunction<CriteriaBuilder, Root<ENTITY>, Predicate> finalFn = (criteriaBuilder, root) ->
      {
         Predicate predicate = predicateFn.apply(criteriaBuilder, root);
         return negated ? criteriaBuilder.not(predicate) : predicate;
      };

      if (optional)
      {
         parent.withRestriction((criteriaBuilder, root) -> {
            try
            {
               return finalFn.apply(criteriaBuilder, root);
            }
            catch (Exception e)
            {
               return criteriaBuilder.conjunction();
            }
         });
      }
      else
      {
         parent.withRestriction(finalFn);
      }

      return parent;
   }

}
