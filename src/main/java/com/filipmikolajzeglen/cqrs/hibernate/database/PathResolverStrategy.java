package com.filipmikolajzeglen.cqrs.hibernate.database;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;

interface PathResolverStrategy<ENTITY>
{
   Path<?> resolve(Root<ENTITY> root);
}