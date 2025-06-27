package com.filipmikolajzeglen.cqrs.hibernate.database;

import com.filipmikolajzeglen.cqrs.common.Command;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public abstract class DatabaseCommand<ENTITY> extends Command<ENTITY>
{
   public static <ENTITY> Create<ENTITY> create(ENTITY entity)
   {
      return new Create<>(entity);
   }

   public static <ENTITY> Update<ENTITY> update(ENTITY entity)
   {
      return new Update<>(entity);
   }

   public static <ENTITY> Remove<ENTITY> remove(ENTITY entity)
   {
      return new Remove<>(entity);
   }

   public static <ENTITY> Flush<ENTITY> flush()
   {
      return new Flush<>();
   }

   @Getter
   @RequiredArgsConstructor
   public static class Create<ENTITY> extends DatabaseCommand<ENTITY>
   {
      private final ENTITY entity;
   }

   @Getter
   @RequiredArgsConstructor
   public static class Update<ENTITY> extends DatabaseCommand<ENTITY>
   {
      private final ENTITY entity;
   }

   @Getter
   @RequiredArgsConstructor
   public static class Remove<ENTITY> extends DatabaseCommand<ENTITY>
   {
      private final ENTITY entity;
   }

   public static class Flush<ENTITY> extends DatabaseCommand<ENTITY>
   {
   }
}