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
      return command.execute(entityManager);
   }
}