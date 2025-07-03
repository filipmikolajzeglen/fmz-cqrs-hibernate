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
  <version>1.2.0</version>
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

## Controversial Usage

### Using `optionally()` with Query Restrictions

In general, the use of `Optional` as a method parameter or field is discouraged in Java, as it can lead to unclear APIs and misuse. However, in the context of query building, there are cases where it is justified and even beneficial.

#### Example

Optional fields that are meant to be used with `optionally()` should be declared as follows:

```java
// These fields are intended to be used as optional query parameters
private Optional<String> optionalName = Optional.empty();
private Optional<Collection<Long>> optionalIds = Optional.empty();
```

This way, you can safely assign values to these fields only when the restriction should be applied, and leave them empty otherwise.

Suppose you want to build a query where some restrictions should only be applied if the value is present (e.g., provided by the user). You can use the `optionally()` modifier to express this intent:

```java
DatabaseQuery<MyEntity> query = DatabaseQuery.<MyEntity>builder(MyEntity.class)
    .property(MyEntity::getName).optionally().equalTo(optionalName)
    .property(MyEntity::getId).optionally().in(optionalIds)
    .build();
```

Here, `optionalName` is of type `Optional<String>` and `optionalIds` is of type `Optional<Collection<Long>>`. If the optionals are empty, the corresponding restrictions are not added to the query.

#### Why is this justified?

- **Declarative intent:** The use of `optionally()` makes it explicit that the restriction should only be applied if the value is present.
- **Avoids boilerplate:** Without this, you would need to write imperative code to conditionally add restrictions, making the query builder less fluent and more error-prone.
- **Query semantics:** In query construction, the presence or absence of a restriction is a first-class concern, and `Optional` is a natural fit for this use case.

#### Why is this an exception?

- **Not for general use:** This pattern should not be used for regular business logic, DTOs, or method signatures outside of query construction.
- **Exception to the rule:** Treat this as a pragmatic exception, justified only by the need for expressive and concise query building. Do not generalize this approach to other parts of your codebase.

## Sorting Support in Pagination

Some pagination strategies support sorting of results. These implement the `SortablePagination` interface, which allows you to specify the order of returned elements by one or more properties.

> **Note:** If you do not provide your own sorting to `DatabaseQuery`, results will be sorted by the `"id"` column in ascending order by default.

### Supported Pagination Types

Sorting is available for the following pagination types:
- `Pagination.all()` (`ListPagination`)
- `Pagination.first()` (`FirstPagination`)
- `Pagination.paged(...)` (`PagedPagination`)
- `Pagination.sliced(...)` (`SlicePagination`)

### Defining Sorting

To specify sorting, use the `orderedByAsc(property)` or `orderedByDesc(property)` methods on the pagination instance. You can chain multiple calls to set sorting by several fields (in priority order).

#### Example usage:

```java
// Sort ascending by the "name" field
List<MyEntity> entities = handler.handle(
    query,
    Pagination.all().orderedByAsc("name")
);

// Sort descending by "createdAt", then ascending by "name"
PagedResult<MyEntity> page = handler.handle(
    query,
    Pagination.paged(0, 10, totalCount)
        .orderedByDesc("createdAt")
        .orderedByAsc("name")
);
```

### Retrieving Sorting Information

You can retrieve the list of declared sort orders using the `getSorts()` method:

```java
SortablePagination<MyEntity, ?> pagination = Pagination.all()
    .orderedByAsc("name")
    .orderedByDesc("createdAt");

List<Sort> sorts = pagination.getSorts();
for (Sort sort : sorts) {
    System.out.println(sort.getProperty() + " " + sort.getDirection());
}
```

## Integration

For **Spring** integration, see the `fmz-cqrs-spring` module, which provides configuration and bean registration.

For **Micronaut** integration, see the `fmz-cqrs-micronaut` module, which provides analogous configuration and bean registration for Micronaut DI.

## Mentorship

- Paweł 'nivertius' Płazieński — [https://source.perfectable.org/](https://source.perfectable.org/)