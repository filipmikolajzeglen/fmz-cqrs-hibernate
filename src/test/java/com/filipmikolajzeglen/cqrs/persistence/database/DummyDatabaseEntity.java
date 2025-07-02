package com.filipmikolajzeglen.cqrs.persistence.database;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
@Table(name = "dummy_database_entity", schema = "fmzcqrspersistence")
public class DummyDatabaseEntity
{
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   Long id;
   String name;
   boolean flag;
   Long number;
}