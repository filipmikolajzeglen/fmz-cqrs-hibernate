package com.filipmikolajzeglen.cqrs.persistence.database;

import jakarta.persistence.*;

@Entity
@Table(name = "nested_super_entity", schema = "fmzcqrspersistence")
public class NestedSuperEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne
    @JoinColumn(name = "super_entity_id")
    SuperEntity superEntity;

    public NestedSuperEntity() {}

    public NestedSuperEntity(SuperEntity superEntity) {
        this.superEntity = superEntity;
    }

    public Long getId() {
        return id;
    }

    public SuperEntity getSuperEntity() {
        return superEntity;
    }

    public void setSuperEntity(SuperEntity superEntity) {
        this.superEntity = superEntity;
    }
}