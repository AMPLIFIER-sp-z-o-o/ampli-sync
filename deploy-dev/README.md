# Local Development Setup

This directory contains a simple local setup for running `ampli-sync` with Docker.

The goal of this setup is to make the project easy to start for development and basic testing. It runs:

- PostgreSQL 16
- Tomcat with the `ampli-sync` WAR
- a minimal test database
- local development auth bypass

This setup is only for local development.

## Prerequisites

You need:

- Java and Maven
- Docker
- Docker Compose
- curl

## Getting Started
### How to Build the WAR

From the repository root, run:

`./deploy-dev/build-dev.sh`

This script builds the Maven project and copies the generated WAR to:

`deploy-dev/docker/webapps/ROOT.war`

The WAR is deployed as ROOT.war, so the application is available at:

`http://localhost:8080/ampli-sync/`

not at:

`http://localhost:8080/ampli_sync_war/ampli-sync/`

### How to start the Docker Setup

After building the WAR, start the Docker setup:
```bash
cd deploy-dev/docker
docker compose up --build
```

The logs from PostgreSQL and Tomcat will be shown in the terminal.

### Remote Debugging

The local Docker setup can also run Tomcat with remote debugging.

First build the WAR:

`./deploy-dev/build-dev.sh`

Then start Docker Compose with the extra debug file that overrides base compose file:
```bash
cd deploy-dev/docker
docker compose -f docker-compose.yml -f docker-compose.debug.yml up --build
```
This starts the application normally on:

`http://localhost:8080/ampli-sync/`

and exposes the debug port on:

`localhost:5005`

In IntelliJ IDEA, create a new configuration:

`Run -> Edit Configurations -> Add New Configuration -> Remote JVM Debug`

Use:

`Host: localhost`

`Port: 5005`

Start this configuration with Debug, not Run.

You can test the debugger for example by setting a breakpoint in helper method:

`SyncAPI3.getSubscriberUUID`

and then call:

`curl http://localhost:8080/ampli-sync/prepopulate-db/test-device-1`

If IntelliJ stops on breakpoint, remote debugging is working.


### Optional Startup Script

There is also an optional helper script:

`./deploy-dev/start-dev.sh`

It runs both the WAR build script and then starts Docker Compose.


## How It Works

### Docker Setup

The Docker Compose setup starts two services:

- `postgres`
- `amplisync`

### `postgres`

This service runs PostgreSQL 16 for local development.

It creates the database with configuration:

- database: `ampli_sync_test`
- user: `postgres`
- password: `postgres`

PostgreSQL is exposed on the host at:

  `localhost:5432`

The database files are stored in the Docker volume:

`postgres-data`

The first time the database is created, PostgreSQL runs the SQL files from:

`deploy-dev/docker/init-db/`

### `amplisync`

This service runs Tomcat and deploys the ampli-sync WAR.

Tomcat is exposed on the host at:

`localhost:8080`

The service uses the following environment variables:
```text
WORKING_DIR=/working-dir/
DBHOST=postgres
DBPORT=5432
DBNAME=ampli_sync_test
DBUSER=postgres
DBPASS=postgres
AUTH_DISABLED=true
DEV_USER_ID=1
```
`AUTH_DISABLED=true` is used only in the local development setup. It disables JWT validation in the authentication filter.

When auth is disabled, `DEV_USER_ID=1` is used as the user id for requests without a JWT. The test database contains this user and maps it to the `tenant_test` schema.

The Tomcat container mounts:

deploy-dev/docker/webapps -> /usr/local/tomcat/webapps 
deploy-dev/docker/working-dir -> /working-dir

The `webapps` mount is used to deploy ROOT.war.

The `working-dir` mount is used by the application to store generated files.



### Database 

The local PostgreSQL database is initialized from:

`deploy-dev/docker/init-db/001-init.sql`

It creates a minimal database needed to run the sync server locally.

It creates:

- `public.tenants_tenant`
- `public.users_customuser`
-  schema `tenant_test` 
- `tenant_test.document_headers`

The test user is:
`user id: 1`

This user is assigned to:
`tenant_test`

The test business table is:
`tenant_test.document_headers`

 
### Authentication in Local Development

The normal application expects a JWT token.

For local development, this setup disables authentication by setting:
```bash
  AUTH_DISABLED=true
  DEV_USER_ID=1
```
This means that requests without a JWT are treated as requests from user 1.

Do not use `AUTH_DISABLED=true` outside of local development.

## Testing
### Verify the Server

When the stack is running, check the health endpoint:

curl http://localhost:8080/ampli-sync/

Expected response contains:

Database connected

### Download a Prepopulated SQLite Database

You can test database prepopulation with:

`curl -OJ http://localhost:8080/ampli-sync/prepopulate-db/test-device-1`

This should download a ZIP file with a SQLite database inside.

The test-device-1 value is a device identifier. The server uses it to register or find a sync subscriber for that device.

### Smoke Test Suite

A simple smoke test suite is available at:

`deploy-dev/docker/smoke-test.sh`

Run it after the Docker stack is already running:
```bash
  cd deploy-dev/docker
 ./smoke-test.sh
```
The smoke test checks that:

- the API responds
- the API can connect to PostgreSQL
- prepopulate-db returns a non-empty ZIP file

### Reset the Environment

To stop the containers:
```bash
cd deploy-dev/docker
docker compose down
```
To stop the containers and remove the PostgreSQL volume:
```bash
  cd deploy-dev/docker
  docker compose down -v
```
Use down -v when you want to recreate the database from init-db/001-init.sql.

## Useful Paths

- `deploy-dev/build-dev.sh`:              builds the WAR and copies it as ROOT.war
- `deploy-dev/start-dev.sh`:              builds WAR, copies it as ROOT.war, and starts the local setup
- `deploy-dev/docker/docker-compose.yml`: local Docker Compose setup
- `deploy-dev/docker/init-db/001-init.sql`: local database initialization file
- `deploy-dev/docker/webapps/ROOT.war`:   deployed WAR file
- `deploy-dev/docker/working-dir`:        application working directory
- `deploy-dev/docker/smoke-test.sh`:      basic smoke test suite

