# FMZ CQRS Persistence

This module provides generic persistence support for the FMZ CQRS framework using JPA/Hibernate.  
It contains abstractions and implementations for command and query handlers, as well as builder utilities for constructing type-safe queries and batch operations.

## Features

- Generic `DatabaseCommand` and `DatabaseQuery` abstractions
- Command and query handlers for JPA (`DatabaseCommandHandler`, `DatabaseQueryHandler`)
- Type-safe property and restriction builders
- Batch update support via `DatabaseSuperCommand`
- No dependency on Spring or Micronaut (integration is provided in separate modules)

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>com.filipmikolajzeglen.cqrs</groupId>
  <artifactId>fmz-cqrs-persistence</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Important for GitHub Packages

To fetch dependencies from GitHub Packages, you **must** add the following to your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/filipmikolajzeglen</url>
  </repository>
</repositories>
```

And for **Gradle** (in your `build.gradle`):

```groovy
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/filipmikolajzeglen")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("USERNAME_GITHUB")
            password = project.findProperty("gpr.key") ?: System.getenv("TOKEN_GITHUB")
        }
    }
}
```

Without this, Maven or Gradle will not be able to download dependencies from the GitHub repository.

## Usage

Add this module as a dependency to your project.  
To use with Spring, add a dependency on `fmz-cqrs-spring` and register the handlers as beans.  
To use with Micronaut, add a dependency on `fmz-cqrs-micronaut` and register the handlers as beans.

### Example: Creating and Executing a Command

```java
// Create a new entity and persist it
EntityManager entityManager = ...; // obtain from your context
DatabaseCommandHandler<MyEntity> handler = new DatabaseCommandHandler<>(entityManager);

MyEntity entity = new MyEntity();
entity.setName("John");
entity.setActive(true);

// Persist the entity
DatabaseCommand<MyEntity> createCommand = DatabaseCommand.create(entity);
handler.handle(createCommand);

// Update the entity
entity.setActive(false);
DatabaseCommand<MyEntity> updateCommand = DatabaseCommand.update(entity);
handler.handle(updateCommand);

// Remove the entity
DatabaseCommand<MyEntity> removeCommand = DatabaseCommand.remove(entity);
handler.handle(removeCommand);

// Flush the persistence context
DatabaseCommand<MyEntity> flushCommand = DatabaseCommand.flush();
handler.handle(flushCommand);
```

### Example: Building and Executing a Query

```java
// Build a query for all active entities named "John"
DatabaseQuery<MyEntity> query = DatabaseQuery.<MyEntity>builder(MyEntity.class)
    .property(MyEntity::getName).equalTo("John")
    .property(MyEntity::isActive).equalTo(true)
    .build();

DatabaseQueryHandler<MyEntity> handler = new DatabaseQueryHandler<>(entityManager);

// Get all results
List<MyEntity> results = handler.handle(query, Pagination.all());

// Get only the first result
MyEntity first = handler.handle(query, Pagination.first());

// Check if any entity matches the query
boolean exists = handler.handle(query, Pagination.exist());

// Count matching entities
long count = handler.handle(query, Pagination.count());
```

### Example: Batch Update with DatabaseSuperCommand

```java
// Set all inactive users named "John" to active
DatabaseSuperCommand<MyEntity> superCommand = DatabaseSuperCommand
    .update(MyEntity.class)
    .set(MyEntity::setActive, true)
    .where(
        DatabaseQuery.<MyEntity>builder(MyEntity.class)
            .property(MyEntity::getName).equalTo("John")
            .property(MyEntity::isActive).equalTo(false)
            .build()
    );

superCommand.execute(entityManager);
```

## Integration

For **Spring** integration, see the `fmz-cqrs-spring` module, which provides configuration and bean registration.

For **Micronaut** integration, see the `fmz-cqrs-micronaut` module, which provides analogous configuration and bean registration for Micronaut DI.

## Mentorship

- Paweł 'nivertius' Płazieński — [https://source.perfectable.org/](https://source.perfectable.org/)