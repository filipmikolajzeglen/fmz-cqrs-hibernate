package com.filipmikolajzeglen.cqrs.persistence;

import com.filipmikolajzeglen.cqrs.spring.SpringDispatcherConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(SpringDispatcherConfiguration.class)
public class FMZCQRSHibernateApplication
{
   public static void main(String[] args)
   {
      SpringApplication.run(FMZCQRSHibernateApplication.class, args);
   }
}