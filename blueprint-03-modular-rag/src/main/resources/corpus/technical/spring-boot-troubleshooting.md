# Port already in use
If Spring Boot fails with port 8080 already in use, set server.port to a free port or stop the other process.

# Slow startup
Enable spring.main.lazy-initialization for local development and check the startup actuator endpoint for slow beans.
