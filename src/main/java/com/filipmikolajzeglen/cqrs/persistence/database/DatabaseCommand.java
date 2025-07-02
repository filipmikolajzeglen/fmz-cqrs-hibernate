package com.filipmikolajzeglen.cqrs.persistence.database;

import com.filipmikolajzeglen.cqrs.core.Command;
import jakarta.persistence.EntityManager;

/**
 * Represents a database command that can be executed using an {@link EntityManager}.
 *
 * @param <ENTITY> the entity type
 */
public abstract class DatabaseCommand<ENTITY> extends Command<ENTITY>
{
   /**
    * Executes the command using the provided {@link EntityManager}.
    *
    * @param entityManager the entity manager
    * @return the result of the command
    */
   public abstract ENTITY execute(EntityManager entityManager);

   /**
    * Creates a command for persisting a new entity.
    *
    * @param entity the entity to persist
    * @return a create command
    */
   public static <ENTITY> DatabaseCommand<ENTITY> create(ENTITY entity)
   {
      return new Create<>(entity);
   }

   /**
    * Command for persisting a new entity.
    */
   public static class Create<ENTITY> extends DatabaseCommand<ENTITY>
   {
      private final ENTITY entity;

      public Create(ENTITY entity)
      {
         this.entity = entity;
      }

      @Override
      public ENTITY execute(EntityManager entityManager)
      {
         entityManager.persist(entity);
         return entity;
      }
   }

   /**
    * Creates a command for updating an entity.
    *
    * @param entity the entity to update
    * @return an update command
    */
   public static <ENTITY> DatabaseCommand<ENTITY> update(ENTITY entity)
   {
      return new Update<>(entity);
   }

   /**
    * Command for updating an entity.
    */
   public static class Update<ENTITY> extends DatabaseCommand<ENTITY>
   {
      private final ENTITY entity;

      public Update(ENTITY entity)
      {
         this.entity = entity;
      }

      @Override
      public ENTITY execute(EntityManager entityManager)
      {
         return entityManager.merge(entity);
      }
   }

   /**
    * Creates a command for removing an entity.
    *
    * @param entity the entity to remove
    * @return a remove command
    */
   public static <ENTITY> DatabaseCommand<ENTITY> remove(ENTITY entity)
   {
      return new Remove<>(entity);
   }

   /**
    * Command for removing an entity.
    */
   public static class Remove<ENTITY> extends DatabaseCommand<ENTITY>
   {
      private final ENTITY entity;

      public Remove(ENTITY entity)
      {
         this.entity = entity;
      }

      @Override
      public ENTITY execute(EntityManager entityManager)
      {
         entityManager.remove(entity);
         return entity;
      }
   }

   /**
    * Creates a command for flushing the persistence context.
    *
    * @return a flush command
    */
   public static <ENTITY> DatabaseCommand<ENTITY> flush()
   {
      return new Flush<>();
   }

   /**
    * Command for flushing the persistence context.
    */
   public static class Flush<ENTITY> extends DatabaseCommand<ENTITY>
   {
      @Override
      public ENTITY execute(EntityManager entityManager)
      {
         entityManager.flush();
         return null;
      }
   }
}