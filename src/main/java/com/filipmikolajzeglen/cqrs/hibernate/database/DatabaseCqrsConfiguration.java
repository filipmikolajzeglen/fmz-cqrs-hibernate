package com.filipmikolajzeglen.cqrs.hibernate.database;

import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseCqrsConfiguration
{

   @Bean
   public DatabaseQueryHandler<?> databaseQueryHandler(EntityManager entityManager)
   {
      return new DatabaseQueryHandler<>(entityManager);
   }

   @Bean
   public DatabaseCommandHandler<?> databaseCommandHandler(EntityManager entityManager)
   {
      return new DatabaseCommandHandler<>(entityManager);
   }
}