package com.filipmikolajzeglen.cqrs.persistence.database;

import com.filipmikolajzeglen.cqrs.core.CommandHandler;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Handles execution of {@link DatabaseCommand} using an {@link EntityManager}.
 *
 * @param <ENTITY> the entity type
 */
@RequiredArgsConstructor
public class DatabaseCommandHandler<ENTITY> implements CommandHandler<DatabaseCommand<ENTITY>, ENTITY>
{
   private final EntityManager entityManager;

   /**
    * Handles the given database command.
    *
    * @param command the command to handle
    * @return the result of the command execution
    */
   @Override
   public ENTITY handle(DatabaseCommand<ENTITY> command)
   {
      return command.execute(entityManager);
   }
}