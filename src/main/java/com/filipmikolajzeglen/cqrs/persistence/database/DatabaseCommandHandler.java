package com.filipmikolajzeglen.cqrs.persistence.database;

import com.filipmikolajzeglen.cqrs.core.CommandHandler;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DatabaseCommandHandler<ENTITY> implements CommandHandler<DatabaseCommand<ENTITY>, ENTITY>
{
   private final EntityManager entityManager;

   @Override
   public ENTITY handle(DatabaseCommand<ENTITY> command)
   {
      return command.execute(entityManager);
   }
}