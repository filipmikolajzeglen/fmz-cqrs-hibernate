package com.filipmikolajzeglen.cqrs.hibernate.database;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;

import com.filipmikolajzeglen.cqrs.common.Command;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.perfectable.introspection.FunctionalReference;

public abstract class DatabaseSuperCommand<E> extends Command<Integer> {

   public abstract Integer execute(EntityManager entityManager);

   public static <E> UpdateBuilder<E> update(Class<E> entityClass) {
      return new UpdateBuilder<>(entityClass);
   }

   public static class UpdateBuilder<E> {
      private final Class<E> entityClass;
      private final List<Setter<E, ?>> setters = new ArrayList<>();
      private DatabaseQuery<E> query;

      public UpdateBuilder(Class<E> entityClass) {
         this.entityClass = entityClass;
      }

      public <V> UpdateBuilder<E> set(PropertyBuilder.Setter<E, V> setter, V value) {
         setters.add(new Setter<>(setter, value));
         return this;
      }

      public UpdateBuilder<E> set(String property, Object value) {
         setters.add(new Setter<>(property, value));
         return this;
      }

      public DatabaseSuperCommand<E> where(DatabaseQuery<E> query) {
         this.query = query;
         return new UpdateCommand<>(entityClass, setters, query);
      }
   }

   private static class Setter<E, V> {
      final PropertyBuilder.Setter<E, V> setter;
      final String property;
      final Object value;

      Setter(PropertyBuilder.Setter<E, V> setter, V value) {
         this.setter = setter;
         this.property = null;
         this.value = value;
      }

      Setter(String property, Object value) {
         this.setter = null;
         this.property = property;
         this.value = value;
      }
   }

   private static class UpdateCommand<E> extends DatabaseSuperCommand<E> {
      private final Class<E> entityClass;
      private final List<Setter<E, ?>> setters;
      private final DatabaseQuery<E> query;

      UpdateCommand(Class<E> entityClass, List<Setter<E, ?>> setters, DatabaseQuery<E> query) {
         this.entityClass = entityClass;
         this.setters = setters;
         this.query = query;
      }

      @Override
      public Integer execute(EntityManager entityManager) {
         CriteriaBuilder cb = entityManager.getCriteriaBuilder();
         CriteriaUpdate<E> update = cb.createCriteriaUpdate(entityClass);
         Root<E> root = update.from(entityClass);

         for (Setter<E, ?> setter : setters) {
            if (setter.property != null) {
               update.set(setter.property, setter.value);
            } else if (setter.setter != null) {
               String property = propertyNameFromSetter(setter.setter);
               update.set(property, setter.value);
            }
         }

         List<Predicate> predicates = query.getRestrictions().stream()
               .map(r -> r.toPredicate(cb, root))
               .toList();
         update.where(predicates.toArray(new Predicate[0]));

         return entityManager.createQuery(update).executeUpdate();
      }

      private <ENTITY, PROPERTY> String propertyNameFromSetter(PropertyBuilder.Setter<ENTITY, PROPERTY> setter) {
         FunctionalReference.Introspection introspection = setter.introspect();
         String methodName = introspection.referencedMethod().getName();
         return Introspector.decapitalize(methodName.replaceFirst("^set", ""));
      }
   }
}