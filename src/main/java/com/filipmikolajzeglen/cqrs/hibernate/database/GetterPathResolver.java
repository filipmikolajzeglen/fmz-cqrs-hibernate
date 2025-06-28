package com.filipmikolajzeglen.cqrs.hibernate.database;

import java.beans.Introspector;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.perfectable.introspection.FunctionalReference;

class GetterPathResolver<ENTITY, PROPERTY> implements PathResolverStrategy<ENTITY>
{
   private final PropertyBuilder.Getter<ENTITY, PROPERTY> getter;

   public GetterPathResolver(PropertyBuilder.Getter<ENTITY, PROPERTY> getter)
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