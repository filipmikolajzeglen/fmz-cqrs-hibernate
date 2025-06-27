package com.filipmikolajzeglen.cqrs.hibernate.database;

import com.filipmikolajzeglen.cqrs.common.CommandHandler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DatabaseCommandHandler<ENTITY> implements CommandHandler<DatabaseCommand<ENTITY>, ENTITY>
{
   @PersistenceContext
   private final EntityManager entityManager;

   @Override
   public ENTITY handle(DatabaseCommand<ENTITY> command)
   {
      switch (command)
      {
         case DatabaseCommand.Create<ENTITY> create ->
         {
            entityManager.persist(create.getEntity());
            return create.getEntity();
         }
         case DatabaseCommand.Update<ENTITY> update ->
         {
            return entityManager.merge(update.getEntity());
         }
         case DatabaseCommand.Remove<ENTITY> remove ->
         {
            ENTITY managed = entityManager.merge(remove.getEntity());
            entityManager.remove(managed);
            return managed;
         }
         case DatabaseCommand.Flush<ENTITY> entityFlush ->
         {
            entityManager.flush();
            return null;
         }
         default -> throw new IllegalArgumentException("Unknown DatabaseCommand: " + command.getClass());
      }
   }
}