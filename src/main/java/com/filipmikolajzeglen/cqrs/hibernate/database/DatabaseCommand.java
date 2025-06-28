package com.filipmikolajzeglen.cqrs.hibernate.database;

import com.filipmikolajzeglen.cqrs.common.Command;
import jakarta.persistence.EntityManager;

public abstract class DatabaseCommand<ENTITY> extends Command<ENTITY>
{
   public abstract ENTITY execute(EntityManager entityManager);

   public static <ENTITY> DatabaseCommand<ENTITY> create(ENTITY entity)
   {
      return new Create<>(entity);
   }

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

   public static <ENTITY> DatabaseCommand<ENTITY> update(ENTITY entity)
   {
      return new Update<>(entity);
   }

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

   public static <ENTITY> DatabaseCommand<ENTITY> remove(ENTITY entity)
   {
      return new Remove<>(entity);
   }

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

   public static <ENTITY> DatabaseCommand<ENTITY> flush()
   {
      return new Flush<>();
   }

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