package com.filipmikolajzeglen.cqrs.persistence.database;

import jakarta.persistence.*;

@Entity
@Table(name = "super_entity", schema = "fmzcqrspersistence")
public class SuperEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne
    @JoinColumn(name = "dummy_database_entity_id")
    DummyDatabaseEntity dummyDatabaseEntity;

    public SuperEntity() {}

    public SuperEntity(DummyDatabaseEntity dummyDatabaseEntity) {
        this.dummyDatabaseEntity = dummyDatabaseEntity;
    }

    public Long getId() {
        return id;
    }

    public DummyDatabaseEntity getDummyDatabaseEntity() {
        return dummyDatabaseEntity;
    }

    public void setDummyDatabaseEntity(DummyDatabaseEntity dummyDatabaseEntity) {
        this.dummyDatabaseEntity = dummyDatabaseEntity;
    }
}