package com.filipmikolajzeglen.cqrs.persistence.database

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import spock.lang.Specification

class PropertyBuilderSpec extends Specification {

   def "should apply ForwardStrategy and NegatedStrategy"() {
      given:
      def cb = Mock(CriteriaBuilder)
      def predicate = Mock(Predicate)

      when:
      def forward = PropertyBuilder.NegationStrategy.ForwardStrategy.INSTANCE
      def negated = PropertyBuilder.NegationStrategy.NegatedStrategy.INSTANCE

      then:
      forward.apply(cb, predicate) == predicate
      negated.apply(cb, predicate) == cb.not(predicate)
   }

   def "should negate strategies"() {
      expect:
      PropertyBuilder.NegationStrategy.ForwardStrategy.INSTANCE.negate() == PropertyBuilder.NegationStrategy.NegatedStrategy.INSTANCE
      PropertyBuilder.NegationStrategy.NegatedStrategy.INSTANCE.negate() == PropertyBuilder.NegationStrategy.ForwardStrategy.INSTANCE
   }
}