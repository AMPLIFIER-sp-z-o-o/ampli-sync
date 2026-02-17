# AMPLI-SYNC

## Overview

AMPLI-SYNC is an open-source synchronization framework designed for
bidirectional data replication between a local SQLite database
(edge/client) and a centralized relational database (PostgreSQL, MS SQL,
MySQL, Oracle).

The framework enables offline-first architectures where applications
operate independently of network availability and synchronize
automatically once connectivity is restored.

------------------------------------------------------------------------

## Related projects

### Example client implementation

https://github.com/AMPLIFIER-sp-z-o-o/ampli-sync-rn-example

### Tomcat server

https://github.com/AMPLIFIER-sp-z-o-o/custom-tomcat

------------------------------------------------------------------------

# 1. Architectural Principles

## 1.1 Offline-First by Design

Clients operate on a fully functional local SQLite database.

Characteristics:

-   No runtime dependency on network
-   Full transactional consistency locally
-   Local writes never blocked by remote availability
-   Deterministic sync recovery after failure

------------------------------------------------------------------------

## 1.2 Decoupled Sync Engine

The synchronization engine operates as a separate logical layer:

    Application Layer
            ↓
    Local Database (SQLite)
            ↓
    Sync Adapter
            ↓
    Transport Layer (HTTPS / Queue / RPC)
            ↓
    Server Sync Endpoint
            ↓
    Central Database

The engine does not enforce schema structure. It operates on metadata
and change tracking strategies defined per project.

------------------------------------------------------------------------

# 2. Data Model Requirements

To enable synchronization, each table must be initialized, look for:

    public void InitSync(String schema)

------------------------------------------------------------------------

# 3. Change Tracking Strategy

AMPLI-SYNC uses incremental synchronization based on:

-   Version counters
-   Logical delete markers

Two synchronization directions:

## 3.1 Push Phase (Client → Server)

Client sends:

-   New records
-   Updated records
-   Soft-deleted records

Server validates: - Schema integrity - Referential integrity - Business
rules

------------------------------------------------------------------------

## 3.2 Pull Phase (Server → Client)

Server returns:

-   Records modified since client's last sync watermark
-   Conflict resolutions (if any)
-   Forced overrides (optional)

------------------------------------------------------------------------

# 4. Conflict Resolution

AMPLI-SYNC does not enforce a single strategy.

Supported models:

### 4.1 Last Write Wins (LWW)

Version-based resolution.

### 4.2 Server Authority

Server overrides client in conflict.

### 4.3 Client Authority

Edge device takes priority.

### 4.4 Custom Merge Logic

Domain-specific resolution layer (recommended for enterprise systems).

------------------------------------------------------------------------

# 5. Transaction Handling

Each sync batch operates inside transactional boundaries:

-   Atomic batch processing
-   Partial retry support

Server endpoints must be idempotent to support retry mechanisms.

------------------------------------------------------------------------

# 6. Scalability Considerations

## 6.1 Server Side

-   Horizontal scaling behind load balancer
-   Stateless sync endpoints
-   Batch processing

## 6.2 Client Side

-   Batched payload transmission
-   Delta-based updates
-   Local indexing for change queries

------------------------------------------------------------------------

# 7. Performance Optimizations

-   Compression (gzip)
-   JSON streaming or binary encoding
-   Parallel table sync
-   Watermark-based incremental queries

------------------------------------------------------------------------

# 8. Security Model

Recommended security measures:

-   HTTPS only
-   Token-based authentication (JWT / API keys)
-   Request signature validation
-   Payload size limits
-   Rate limiting
-   Audit logging

------------------------------------------------------------------------

# 9. Failure Recovery

AMPLI-SYNC supports:

-   Exponential backoff retry
-   Resume from last successful watermark
-   Idempotent reprocessing
-   Network interruption tolerance

Failure scenarios handled:

-   Partial batch failure
-   Network timeout
-   Duplicate submission
-   Concurrent modification

------------------------------------------------------------------------

# 10. Multi-Tenant Environments

For SaaS systems:

-   Tenant isolation at DB level (schema-based or row-level security)
-   Tenant-aware sync tokens
-   Scoped synchronization contexts

------------------------------------------------------------------------

# 11. Production Validation

AMPLI-SYNC architecture has been validated in:

-   Mobile field sales systems
-   Field service applications
-   Distributed B2B ordering platforms
-   Multi-tenant SaaS ecosystems

The framework is optimized for high write-frequency environments with
thousands of records per sync cycle.

------------------------------------------------------------------------

# 12. Implementation Philosophy

AMPLI-SYNC is not a product. It is a synchronization framework
requiring:

-   Domain modeling
-   Sync strategy design
-   Conflict definition
-   Infrastructure preparation

It is intended for engineering teams building scalable distributed
systems.

------------------------------------------------------------------------

# License

MIT License

------------------------------------------------------------------------

# Commercial Support

If you plan to implement AMPLI-SYNC in a commercial or enterprise
environment, architectural and integration support is available.
