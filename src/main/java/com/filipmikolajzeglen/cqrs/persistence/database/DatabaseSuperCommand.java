package com.filipmikolajzeglen.cqrs.persistence.database;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.List;

import com.filipmikolajzeglen.cqrs.core.Command;
import com.filipmikolajzeglen.cqrs.core.Query;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.perfectable.introspection.FunctionalReference;

public abstract class DatabaseSuperCommand<ENTITY> extends Command<Integer>
{
   public abstract Integer execute(EntityManager entityManager);

   public static <ENTITY> UpdateBuilder<ENTITY> update(Class<ENTITY> entityClass)
   {
      return new UpdateBuilder<>(entityClass);
   }

   public static class UpdateBuilder<ENTITY>
   {
      private final Class<ENTITY> entityClass;
      private final List<Setter<ENTITY>> setters = new ArrayList<>();
      private Query<ENTITY> query;

      public UpdateBuilder(Class<ENTITY> entityClass)
      {
         this.entityClass = entityClass;
      }

      public <VALUE> UpdateBuilder<ENTITY> set(PropertyBuilder.Setter<ENTITY, VALUE> setter, VALUE value)
      {
         setters.add(Setter.from(setter, value));
         return this;
      }

      public UpdateBuilder<ENTITY> set(String property, Object value)
      {
         setters.add(Setter.from(property, value));
         return this;
      }

      public DatabaseSuperCommand<ENTITY> where(Query<ENTITY> query)
      {
         this.query = query;
         return new UpdateCommand<>(entityClass, setters, query);
      }
   }

   public interface Setter<ENTITY>
   {
      void applyToCriteria(CriteriaUpdate<ENTITY> update, Root<ENTITY> root);

      void applyToEntity(ENTITY entity);

      static <ENTITY, VALUE> Setter<ENTITY> from(PropertyBuilder.Setter<ENTITY, VALUE> setter, VALUE value)
      {
         return new Setter<>()
         {
            @Override
            public void applyToCriteria(CriteriaUpdate<ENTITY> update, Root<ENTITY> root)
            {
               String property = propertyNameFromSetter(setter);
               update.set(property, value);
            }

            @Override
            public void applyToEntity(ENTITY entity)
            {
               setter.set(entity, value);
            }

            private String propertyNameFromSetter(PropertyBuilder.Setter<ENTITY, VALUE> setter)
            {
               FunctionalReference.Introspection introspection = setter.introspect();
               String methodName = introspection.referencedMethod().getName();
               return Introspector.decapitalize(methodName.replaceFirst("^set", ""));
            }
         };
      }

      static <ENTITY> Setter<ENTITY> from(String property, Object value)
      {
         return new Setter<>()
         {
            @Override
            public void applyToCriteria(CriteriaUpdate<ENTITY> update, Root<ENTITY> root)
            {
               update.set(property, value);
            }

            @Override
            public void applyToEntity(ENTITY entity)
            {
               throw new UnsupportedOperationException("Not supported yet.");
            }
         };
      }
   }

   private static class UpdateCommand<ENTITY> extends DatabaseSuperCommand<ENTITY>
   {
      private final Class<ENTITY> entityClass;
      private final List<Setter<ENTITY>> setters;
      private final Query<ENTITY> query;

      UpdateCommand(Class<ENTITY> entityClass, List<Setter<ENTITY>> setters, Query<ENTITY> query)
      {
         this.entityClass = entityClass;
         this.setters = setters;
         this.query = query;
      }

      @Override
      public Integer execute(EntityManager entityManager)
      {
         CriteriaBuilder cb = entityManager.getCriteriaBuilder();
         CriteriaUpdate<ENTITY> update = cb.createCriteriaUpdate(entityClass);
         Root<ENTITY> root = update.from(entityClass);

         setters.forEach(setter -> setter.applyToCriteria(update, root));

         if (query instanceof ConstrainingQuery<?> constraining)
         {
            @SuppressWarnings("unchecked")
            Predicate[] predicates = ((ConstrainingQuery<ENTITY>) constraining).toRestrictions(cb, root);
            update.where(predicates);
            return entityManager.createQuery(update).executeUpdate();
         }
         else
         {
            List<ENTITY> allEntities = entityManager.createQuery(
                  "SELECT e FROM " + entityClass.getSimpleName() + " e", entityClass
            ).getResultList();

            for (ENTITY entity : allEntities)
            {
               setters.forEach(setter -> setter.applyToEntity(entity));
               entityManager.merge(entity);
            }
            return allEntities.size();
         }
      }
   }
}