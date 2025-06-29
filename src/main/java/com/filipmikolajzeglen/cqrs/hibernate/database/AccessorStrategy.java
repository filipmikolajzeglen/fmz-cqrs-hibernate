package com.filipmikolajzeglen.cqrs.hibernate.database;

import java.beans.Introspector;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.perfectable.introspection.FunctionalReference;

sealed interface AccessorStrategy<ENTITY> permits AccessorStrategy.GetterAccessor, AccessorStrategy.RawAccessor
{
   Path<?> resolve(Root<ENTITY> root);

   static <ENTITY, PROPERTY> AccessorStrategy<ENTITY> byGetter(PropertyBuilder.Getter<ENTITY, PROPERTY> getter)
   {
      return new GetterAccessor<>(getter);
   }

   static <ENTITY> AccessorStrategy<ENTITY> byName(String propertyName)
   {
      return new RawAccessor<>(propertyName);
   }

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