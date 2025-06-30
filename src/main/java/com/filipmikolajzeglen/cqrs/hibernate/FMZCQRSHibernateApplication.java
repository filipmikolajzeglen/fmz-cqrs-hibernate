package com.filipmikolajzeglen.cqrs.hibernate;

import com.filipmikolajzeglen.cqrs.core.DispatcherConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(DispatcherConfiguration.class)
public class FMZCQRSHibernateApplication
{
   public static void main(String[] args)
   {
      SpringApplication.run(FMZCQRSHibernateApplication.class, args);
   }
}