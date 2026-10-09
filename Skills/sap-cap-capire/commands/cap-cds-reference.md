---
name: cap-cds-reference
description: Quick CDS syntax reference for entities, services, annotations, and queries
allowed-tools:
  - Read
  - Grep
  - Glob
argument-hint: "[topic]"
arguments:
  - name: topic
    description: "Topic: entities, services, annotations, queries, or types"
    required: false
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return the requested CDS reference, version-sensitive caveats, minimal examples, and related CAP commands or references. Default to read-only guidance and mark runtime or activation checks as pending unless project evidence is available.


# CAP CDS Quick Reference

Comprehensive CDS (Core Data Services) syntax reference for SAP Cloud Application Programming Model.

## Entities

### Basic Entity
```cds
entity Books {
  key ID : UUID;
  title  : String(100);
  price  : Decimal(10,2);
  stock  : Integer;
}
```

### Entity with Aspects
```cds
using { cuid, managed } from '@sap/cds/common';

entity Books : cuid, managed {
  title  : String(100);
  price  : Decimal(10,2);
}
// cuid adds: ID : UUID
// managed adds: createdAt, createdBy, modifiedAt, modifiedBy
```

### Associations
```cds
entity Books {
  key ID     : UUID;
  title      : String(100);
  author     : Association to Authors;  // to-one
  reviews    : Composition of many Reviews on reviews.book = $self;  // to-many
}
```

## Services

### Basic Service
```cds
service CatalogService {
  entity Books as projection on my.Books;
  entity Authors as projection on my.Authors;
}
```

### Service with Actions
```cds
service OrdersService {
  entity Orders as projection on my.Orders actions {
    action cancel() returns String;
  };
}
```

## Annotations

### Basic Annotations
```cds
entity Books {
  title  : String(100) @title: 'Book Title';
  price  : Decimal(10,2) @title: 'Price';
  stock  : Integer @assert.range: [0, 9999];
}
```

### UI Annotations (Fiori)
```cds
annotate CatalogService.Books with @(
  UI.LineItem: [
    { Value: title, Label: 'Title' },
    { Value: author.name, Label: 'Author' }
  ]
);
```

## CQL Queries

### SELECT
```javascript
const books = await SELECT.from(Books);
const book = await SELECT.one.from(Books).where({ ID: bookID });
```

### INSERT
```javascript
await INSERT.into(Books).entries({ title: 'CAP Guide', price: 29.99 });
```

### UPDATE
```javascript
await UPDATE(Books).set({ stock: stock + 10 }).where({ ID: bookID });
```

### DELETE
```javascript
await DELETE.from(Books).where({ ID: bookID });
```

## Complete Reference

For comprehensive documentation, see:
- [CDL Syntax Reference](../skills/sap-cap-capire/references/cdl-syntax.md)
- [Annotations Reference](../skills/sap-cap-capire/references/annotations-reference.md)
- [CQL Queries Reference](../skills/sap-cap-capire/references/cql-queries.md)
