package com.filipmikolajzeglen.cqrs.hibernate.database;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

class RawPathResolver<ENTITY> implements PathResolverStrategy<ENTITY>
{
   private final String path;

   public RawPathResolver(String path)
   {
      this.path = path;
   }

   @Override
   public Path<?> resolve(Root<ENTITY> root)
   {
      return root.get(path);
   }
}