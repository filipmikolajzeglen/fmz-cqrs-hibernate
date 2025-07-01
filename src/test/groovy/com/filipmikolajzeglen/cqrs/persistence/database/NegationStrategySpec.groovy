package com.filipmikolajzeglen.cqrs.persistence.database

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import spock.lang.Specification

class NegationStrategySpec extends Specification {

   def "should apply ForwardStrategy and NegatedStrategy"() {
      given:
      def cb = Mock(CriteriaBuilder)
      def predicate = Mock(Predicate)

      when:
      def forward = NegationStrategy.ForwardStrategy.INSTANCE
      def negated = NegationStrategy.NegatedStrategy.INSTANCE

      then:
      forward.apply(cb, predicate) == predicate
      negated.apply(cb, predicate) == cb.not(predicate)
   }

   def "should negate strategies"() {
      expect:
      NegationStrategy.ForwardStrategy.INSTANCE.negate() == NegationStrategy.NegatedStrategy.INSTANCE
      NegationStrategy.NegatedStrategy.INSTANCE.negate() == NegationStrategy.ForwardStrategy.INSTANCE
   }
}