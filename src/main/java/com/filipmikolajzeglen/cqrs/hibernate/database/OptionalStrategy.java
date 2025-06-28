package com.filipmikolajzeglen.cqrs.hibernate.database;

import java.util.function.BiFunction;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

interface OptionalStrategy
{
   Predicate applySafely(BiFunction<CriteriaBuilder, Root<?>, Predicate> fn, CriteriaBuilder cb, Root<?> root);
}