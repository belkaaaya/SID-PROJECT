# SIDP Distributed Library System

A lightweight distributed system with multiple autonomous library servers and a central coordinator. Clients connect to the coordinator to search books across all libraries, lease/return books, and view aggregated statistics.

## Components

- Coordinator Server: Broadcasts searches, aggregates results, forwards lease/return, collects stats.
- Library Server (3 instances): Stores local books and statistics, persists lease/return state to CSV.
- Client Application (CLI): Connects to the coordinator via TCP, provides `search`, `list`, `lease`, `return`, `stats`, `servers`, `quit`.

## Prerequisites

- Java JDK 11+ installed and on `PATH` (`java`, `javac`).
- Windows PowerShell or any terminal.

## Build

```powershell
cd "C:\Users\bensa\Documents\trae_projects\SIDP"
javac -d out src/main/java/com/sidp/distributed/*.java
```

## Run: Library Servers

Open three terminals (one per server) and run:

```powershell
java -cp out com.sidp.distributed.LibraryServer LIB1 9101 data/lib1.csv
java -cp out com.sidp.distributed.LibraryServer LIB2 9102 data/lib2.csv
java -cp out com.sidp.distributed.LibraryServer LIB3 9103 data/lib3.csv
```

Notes:
- If the CSV file doesn’t exist, the server creates it with default books and persists availability changes on lease/return.
- You can run these on different machines; just keep the chosen ports accessible.

## Run: Coordinator Server

Start the coordinator with endpoints for each library (`serverId@host:port`):

```powershell
# All services on the same machine
java -cp out com.sidp.distributed.CoordinatorServer 9000 \
  LIB1@127.0.0.1:9101 LIB2@127.0.0.1:9102 LIB3@127.0.0.1:9103

# Example with remote libraries
java -cp out com.sidp.distributed.CoordinatorServer 9000 \
  LIB1@192.168.1.10:9101 LIB2@192.168.1.11:9102 LIB3@192.168.1.12:9103
```

Behavior:
- Coordinator ignores libraries that don’t respond within timeout and aggregates only responsive results.

## Run: Client (Interactive CLI)

Open one or more terminals and connect to the coordinator:

```powershell
java -cp out com.sidp.distributed.ClientApp <coord_ip> 9000
# Example
java -cp out com.sidp.distributed.ClientApp 127.0.0.1 9000
```

Commands:
- `search <keyword>` — search titles/authors/keywords across all libraries
- `list` — list all available books from all libraries
- `lease <book_id>` — lease a book (e.g., `lease LIB3-002`)
- `return <book_id>` — return a leased book
- `stats` — aggregated keyword and book-search counts
- `servers` — list configured library endpoints
- `quit` — exit the client

Open multiple client windows quickly (Windows):

```powershell
Start-Process powershell -ArgumentList '-NoExit','-Command','cd "C:\\Users\\bensa\\Documents\\trae_projects\\SIDP"; java -cp out com.sidp.distributed.ClientApp 127.0.0.1 9000'
Start-Process powershell -ArgumentList '-NoExit','-Command','cd "C:\\Users\\bensa\\Documents\\trae_projects\\SIDP"; java -cp out com.sidp.distributed.ClientApp 127.0.0.1 9000'
Start-Process powershell -ArgumentList '-NoExit','-Command','cd "C:\\Users\\bensa\\Documents\\trae_projects\\SIDP"; java -cp out com.sidp.distributed.ClientApp 127.0.0.1 9000'
```

## Data & Persistence

- Each library server persists its books to `data/libX.csv`.
- Lease/return toggles availability and writes back to the CSV.
- Statistics (keyword and per-book search counts) are tracked in-memory per server; coordinator aggregates across servers on `stats`.

## Protocol (TCP Lines)

Requests sent to library servers:
- `SEARCH <keyword>` — server replies with `BOOK ...` lines followed by `END`
- `LIST` — server replies with available `BOOK ...` lines followed by `END`
- `LEASE <book_id>` — replies `OK` or `ERROR <Reason>`
- `RETURN <book_id>` — replies `OK` or `ERROR <Reason>`
- `STATS` — replies `KEYWORD <kw>|<count>` and `BOOKSEARCH <bookId>|<count>`, then `END`

Coordinator output format:
- Aggregated books: `BOOK <id>|<title>|<author>|<serverId>|<available|leased>` then `END`
- Stats: `KEYWORD <kw> <count>` and `BOOKSEARCH <bookId> <count>` then `END`

Example `BOOK` line:
```
BOOK LIB3-002|Design Patterns|Erich Gamma|LIB3|available
```

## Example Sessions

List available books via coordinator:
```powershell
java -cp out com.sidp.distributed.TestClient 127.0.0.1 9000 LIST
```
Search for a keyword:
```powershell
java -cp out com.sidp.distributed.TestClient 127.0.0.1 9000 "SEARCH design"
```
Lease then return a book:
```powershell
java -cp out com.sidp.distributed.TestClient 127.0.0.1 9000 "LEASE LIB3-002"
java -cp out com.sidp.distributed.TestClient 127.0.0.1 9000 "RETURN LIB3-002"
```
Show aggregated stats:
```powershell
java -cp out com.sidp.distributed.TestClient 127.0.0.1 9000 STATS
```

## Troubleshooting

- If a client cannot connect, verify coordinator reachability:
  - `Test-NetConnection <coord_ip> -Port 9000`
- Allow inbound firewall rules for `9000` (coordinator) and library ports (`9101`, `9102`, `9103`).
- Verify coordinator endpoints use correct IP/port for each library.
- Unresponsive libraries are skipped automatically; results still return from reachable servers.

## Code Entry Points

- Coordinator server main: `src/main/java/com/sidp/distributed/CoordinatorServer.java:246`
- Library server main: `src/main/java/com/sidp/distributed/LibraryServer.java:183`
- Client CLI main: `src/main/java/com/sidp/distributed/ClientApp.java:14`
- Book protocol line helpers:
  - `toProtocolLine`: `src/main/java/com/sidp/distributed/Book.java:78`
  - `fromProtocolLine`: `src/main/java/com/sidp/distributed/Book.java:82`

## Notes

- Book IDs are prefixed with server ID (e.g., `LIB3-002`), which the coordinator uses to route `LEASE`/`RETURN` to the correct library.
- Coordinator and servers use socket timeouts to avoid blocking on unreachable endpoints.
