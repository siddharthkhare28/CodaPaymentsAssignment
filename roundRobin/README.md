# Round Robin Load Balancer

A Spring Boot-based intelligent load balancer with multiple distribution strategies, health monitoring, and dynamic server discovery.

## Features

### Load Balancing Strategies
- **Round Robin** (default): Distributes requests evenly across healthy servers
- **Least Response Time**: Routes to the server with lowest average response time

### Server Discovery
- **Static Discovery**: Configure servers in YAML properties
- **File-based Discovery**: Dynamic server list from `servers.txt` file

### Health & Performance Monitoring
- Periodic health checks with configurable intervals
- Intelligent error handling (connection failures vs HTTP errors)
- Response time tracking and slowness detection
- Automatic server recovery and cooldown periods

### Admin Interface
- `GET /admin/health` - Server health status
- `GET /admin/strategy` - Current load balancing strategy  
- `GET /admin/stats` - Performance statistics

## Quick Start

### 1. Configuration

**Static servers (application.yaml):**
```yaml
roundrobin:
  discovery:
    strategy: static
  servers:
    - http://localhost:8081
    - http://localhost:8082
  health-check-interval: 10000
  slow-threshold-ms: 1000
  strategy: round-robin
```

**Dynamic file discovery:**
```yaml
roundrobin:
  discovery:
    strategy: file
    file-path: servers.txt
  # Create servers.txt with one server URL per line
```

### 2. Run the Application
```bash
./mvnw spring-boot:run
```

### 3. Test Load Balancing
```bash
curl http://localhost:8080/api/test
```

## Configuration Options

| Property | Description | Default |
|----------|-------------|---------|
| `roundrobin.strategy` | Load balancing strategy (`round-robin`, `least-response-time`) | `round-robin` |
| `roundrobin.discovery.strategy` | Server discovery method (`static`, `file`) | `static` |
| `roundrobin.discovery.file-path` | Path to server list file | `servers.txt` |
| `roundrobin.health-check-interval` | Health check frequency (ms) | 10000 |
| `roundrobin.slow-threshold-ms` | Slowness detection threshold | 1000 |
| `roundrobin.request-timeout-seconds` | Request timeout | 5 |

## Architecture

The application uses a layered architecture with clear separation of concerns:
- **Controllers**: Handle HTTP requests and admin endpoints
- **Services**: Core load balancing logic and health monitoring
- **Strategies**: Pluggable load balancing algorithms
- **Models**: Server health tracking and request wrappers
