package com.filipmikolajzeglen.cqrs.persistence;

import com.filipmikolajzeglen.cqrs.spring.EnableCqrs;
import com.filipmikolajzeglen.cqrs.spring.SpringDispatcherConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@EnableCqrs
@SpringBootApplication
public class FMZCQRSHibernateApplication
{
   public static void main(String[] args)
   {
      SpringApplication.run(FMZCQRSHibernateApplication.class, args);
   }
}