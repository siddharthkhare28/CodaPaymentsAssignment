# Docker Compose Setup

This Docker Compose configuration sets up a complete load-balanced environment with:

- **3 Echo Service Instances**: Running on ports 8081, 8082, and 8083
- **1 Round Robin Load Balancer**: Running on port 8080

## Services

### Echo Services
- `echo-1`: Available at http://localhost:8081
- `echo-2`: Available at http://localhost:8082  
- `echo-3`: Available at http://localhost:8083

### Round Robin Load Balancer
- `roundrobin`: Available at http://localhost:8080

## Quick Start

1. **Build and start all services:**
   ```bash
   docker-compose up --build
   ```

2. **Start services in detached mode:**
   ```bash
   docker-compose up -d --build
   ```

3. **View logs:**
   ```bash
   # All services
   docker-compose logs -f
   
   # Specific service
   docker-compose logs -f roundrobin
   docker-compose logs -f echo-1
   ```

4. **Stop all services:**
   ```bash
   docker-compose down
   ```

## Testing the Setup

### Test Individual Echo Services
```bash
curl http://localhost:8081/echo?message=hello
curl http://localhost:8082/echo?message=hello
curl http://localhost:8083/echo?message=hello
```

### Test Load Balancer
```bash
# The round robin load balancer will distribute requests across all three echo services
curl http://localhost:8080/echo?message=hello
curl http://localhost:8080/echo?message=hello
curl http://localhost:8080/echo?message=hello
```

### Health Checks
```bash
# Check echo service health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health

# Check round robin service health
curl http://localhost:8080/health
```

## Architecture

The setup creates an isolated Docker network where:
- All services can communicate with each other using service names
- The round robin service is configured to route traffic to `echo-1:8080`, `echo-2:8080`, and `echo-3:8080`
- Health checks ensure services are ready before the load balancer starts
- External access is provided through port mappings

## Configuration

### Echo Services
- Use Docker profile with Spring Boot
- Expose actuator endpoints for health monitoring
- Run on internal port 8080, mapped to external ports 8081, 8082, 8083

### Round Robin Service
- Uses static server discovery pointing to Docker service names
- Configured with health checks and performance monitoring
- Implements load balancing across the three echo instances

## Troubleshooting

### Check service status:
```bash
docker-compose ps
```

### Rebuild specific service:
```bash
docker-compose build echo-1
docker-compose up -d echo-1
```

### Clean rebuild:
```bash
docker-compose down
docker-compose build --no-cache
docker-compose up
```